package com.taxigo

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.taxigo.navi.Navigator
import com.taxigo.navi.Place
import com.taxigo.navi.Places
import com.taxigo.translate.FallbackTranslator
import com.taxigo.translate.GoogleTranslator
import com.taxigo.translate.PapagoTranslator
import com.taxigo.translate.PhrasebookTranslator
import com.taxigo.translate.Translator
import com.taxigo.vehicle.DoorWatcher
import com.taxigo.voice.Listener
import com.taxigo.voice.PleosCredentials
import com.taxigo.voice.Speaker
import java.util.Locale

sealed interface Screen {
    data object Waiting : Screen
    data object AskForeigner : Screen
    data object ChooseLanguage : Screen
    data class Talking(val language: PassengerLanguage) : Screen
}

enum class Turn { GREETING, PASSENGER_LISTENING, SPEAKING_TO_DRIVER, DRIVER_LISTENING, SPEAKING_TO_PASSENGER }

data class ChatLine(val fromPassenger: Boolean, val original: String, val translated: String?)

/*
 * 대기 -> (도어) -> 외국인 여부 -> 언어 선택 -> 통역
 * 통역은 승객 듣기 -> 기사에게 재생 -> 기사 듣기 -> 승객에게 재생 반복.
 *
 * 스피커로 말하는 동안에는 절대 마이크를 열지 않는다. 번역 음성을 다시 인식해서 무한루프 돎.
 * 그래서 다음 listen은 항상 Speaker onDone에서 시작.
 */
class TaxigoViewModel(app: Application) : AndroidViewModel(app) {

    private var _screen by mutableStateOf<Screen>(Screen.Waiting)
    var screen: Screen
        get() = _screen
        private set(value) {
            Log.d(TAG, "screen $_screen -> $value")
            _screen = value
        }

    var turn by mutableStateOf(Turn.GREETING)
        private set
    var partialText by mutableStateOf("")
        private set
    val chat = mutableStateListOf<ChatLine>()

    var pendingPlace by mutableStateOf<Place?>(null)
        private set
    var activeRoute by mutableStateOf<Pair<Place, Int?>?>(null) // 목적지, 예상 분
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val pleos = PleosCredentials.fromBuildConfig()
    private val speaker = Speaker(app, pleos) { lastError = it }
    private val listener = Listener(app, pleos) { lastError = it }
    private val translator = createTranslator()
    private val navigator = Navigator(app, ::onRouteStarted) { lastError = it }
    private val doorWatcher = DoorWatcher(app, ::onPassengerBoarded) { lastError = it }

    private var promptRounds = 0

    private val language: PassengerLanguage?
        get() = (screen as? Screen.Talking)?.language

    init {
        speaker.start()
        listener.start()
        navigator.start()
        try {
            doorWatcher.start()
        } catch (e: Exception) {
            // Pleos 이미지가 아닌 AVD에서는 Vehicle Service가 없음
            lastError = "차량 연결 실패: ${e.message}"
        }
    }

    // --- 탑승 / 언어 선택

    // 승객 대부분이 한국인이라 여기서는 소리 없이 기사 화면에만 묻는다
    fun onPassengerBoarded() {
        if (screen != Screen.Waiting) return
        resetIo()
        screen = Screen.AskForeigner
        handler.postDelayed(::koreanPassenger, ASK_FOREIGNER_TIMEOUT_MS)
    }

    fun foreignPassenger() {
        if (screen == Screen.AskForeigner) showLanguageChoice()
    }

    fun koreanPassenger() {
        if (screen != Screen.AskForeigner) return
        resetIo()
        screen = Screen.Waiting
    }

    fun chooseLanguage(lang: PassengerLanguage) {
        resetIo()
        chat.clear()
        screen = Screen.Talking(lang)
        turn = Turn.GREETING
        speaker.say(Locale.KOREA, "${lang.koreanName} 승객입니다.")
        speaker.say(lang.locale, lang.greeting, onDone = ::listenToPassenger)
    }

    fun changeLanguage() = showLanguageChoice()

    fun finishRide() {
        resetIo()
        chat.clear()
        pendingPlace = null
        activeRoute = null
        screen = Screen.Waiting
    }

    private fun showLanguageChoice() {
        resetIo()
        screen = Screen.ChooseLanguage
        promptRounds = 0
        playPrompts()
    }

    private fun playPrompts() {
        if (screen != Screen.ChooseLanguage || promptRounds >= MAX_PROMPT_ROUNDS) return
        promptRounds++
        val langs = PassengerLanguage.entries
        langs.forEach { lang ->
            val done: (() -> Unit)? =
                if (lang == langs.last()) { { handler.postDelayed(::playPrompts, PROMPT_PAUSE_MS) } } else null
            speaker.say(lang.locale, lang.choosePrompt, done)
        }
    }

    // --- 통역

    private fun listenToPassenger() {
        val lang = language ?: return
        turn = Turn.PASSENGER_LISTENING
        partialText = ""
        listener.listen(
            lang.locale,
            onPartial = { partialText = it },
            onFinal = ::onPassengerSaid,
            onSilence = {
                if (turn == Turn.PASSENGER_LISTENING) handler.postDelayed(::listenToPassenger, RELISTEN_DELAY_MS)
            },
        )
    }

    private fun onPassengerSaid(text: String) {
        val lang = language ?: return
        partialText = ""
        val place = Places.find(text)
        if (place != null) pendingPlace = place

        translator.toKorean(text, lang) { result ->
            handler.post {
                // API는 STT가 뭉갠 지명을 이상하게 옮기는 경우가 있어서 인식한 목적지를 붙여 줌
                val korean = if (result != null && place != null && place.koreanName !in result) {
                    "$result 목적지는 ${place.koreanName}입니다."
                } else {
                    result
                }
                chat += ChatLine(fromPassenger = true, original = text, translated = korean)
                turn = Turn.SPEAKING_TO_DRIVER
                speaker.say(Locale.KOREA, korean ?: "번역 실패. 화면을 확인해 주세요.", onDone = ::listenToDriver)
            }
        }
    }

    // 기사가 대답 안 하면(무음) 다시 승객 차례
    private fun listenToDriver() {
        if (language == null) return
        turn = Turn.DRIVER_LISTENING
        partialText = ""
        listener.listen(
            Locale.KOREA,
            onPartial = { partialText = it },
            onFinal = ::onDriverSaid,
            onSilence = { if (turn == Turn.DRIVER_LISTENING) listenToPassenger() },
        )
    }

    private fun onDriverSaid(text: String) {
        val lang = language ?: return
        partialText = ""
        if (pendingPlace != null && AFFIRMATIVE.containsMatchIn(text)) startNavigation()

        translator.fromKorean(text, lang) { result ->
            handler.post {
                chat += ChatLine(fromPassenger = false, original = text, translated = result)
                if (result == null) {
                    // 틀린 번역을 승객에게 들려주느니 기사에게 다시 말해 달라고 함
                    turn = Turn.SPEAKING_TO_DRIVER
                    speaker.say(Locale.KOREA, "번역 실패. 다시 말씀해 주세요.", onDone = ::listenToDriver)
                } else {
                    turn = Turn.SPEAKING_TO_PASSENGER
                    speaker.say(lang.locale, result, onDone = ::listenToPassenger)
                }
            }
        }
    }

    fun driverWantsToSpeak() {
        if (language == null) return
        handler.removeCallbacksAndMessages(null)
        speaker.stopAll()
        listenToDriver()
    }

    // --- 내비

    fun startNavigation() {
        val place = pendingPlace ?: return
        pendingPlace = null
        navigator.go(place)
    }

    fun dismissPlace() {
        pendingPlace = null
    }

    fun showMap() = navigator.showMap()

    private fun onRouteStarted(place: Place, minutes: Int?) {
        val lang = language ?: return
        activeRoute = place to minutes
        announce(lang, lang.routeStarted(place.englishName, minutes))
        // 경로 요청하면 내비가 앱 영역을 통째로 덮음. 몇 초 보여 주고 통역 화면으로 복귀.
        // TBT는 드라이빙 뷰에 계속 표시되고 내비 음성도 백그라운드에서 나옴.
        handler.postDelayed(::bringToFront, RETURN_TO_APP_DELAY_MS)
    }

    // 에뮬레이터(v2.1)에선 로그에 BAL_BLOCK이 찍히는데도 실제로는 앞으로 나옴.
    // 실차에서 막히면 GNB 아이콘으로 복귀하면 됨.
    private fun bringToFront() {
        val app = getApplication<Application>()
        val intent = Intent(app, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        runCatching { app.startActivity(intent) }
            .onFailure { lastError = "통역 화면 복귀 실패: ${it.message}" }
    }

    // 듣는 중이면 마이크 끄고 말한 다음 원래 차례로. 말하는 중이면 큐 뒤에 붙임.
    private fun announce(lang: PassengerLanguage, text: String) {
        when (turn) {
            Turn.PASSENGER_LISTENING, Turn.DRIVER_LISTENING -> {
                val resume: () -> Unit = if (turn == Turn.DRIVER_LISTENING) ::listenToDriver else ::listenToPassenger
                listener.cancel()
                handler.removeCallbacksAndMessages(null)
                turn = Turn.SPEAKING_TO_PASSENGER
                speaker.say(lang.locale, text, onDone = resume)
            }
            else -> speaker.say(lang.locale, text)
        }
    }

    // --- 기타

    fun feedTestAudio(pcm: ByteArray) {
        val lang = language ?: return
        listener.feedAudio(if (turn == Turn.DRIVER_LISTENING) Locale.KOREA else lang.locale, pcm)
    }

    private fun resetIo() {
        handler.removeCallbacksAndMessages(null)
        listener.cancel()
        speaker.stopAll()
        partialText = ""
    }

    // Papago > Google > 문장집. 키는 local.properties -> BuildConfig
    private fun createTranslator(): Translator {
        val phrasebook = PhrasebookTranslator()
        val api = when {
            BuildConfig.PAPAGO_CLIENT_ID.isNotBlank() && BuildConfig.PAPAGO_CLIENT_SECRET.isNotBlank() ->
                PapagoTranslator(BuildConfig.PAPAGO_CLIENT_ID, BuildConfig.PAPAGO_CLIENT_SECRET)
            BuildConfig.GOOGLE_TRANSLATE_KEY.isNotBlank() -> GoogleTranslator(BuildConfig.GOOGLE_TRANSLATE_KEY)
            else -> null
        }
        Log.i(TAG, "translator: ${api?.javaClass?.simpleName ?: "phrasebook"}, speech: ${if (pleos != null) "HYBRID" else "ON_DEVICE"}")
        return if (api != null) FallbackTranslator(api, phrasebook) else phrasebook
    }

    override fun onCleared() {
        resetIo()
        doorWatcher.stop()
        navigator.release()
        listener.release()
        speaker.release()
    }

    private companion object {
        const val TAG = "TaxigoVM"
        const val MAX_PROMPT_ROUNDS = 3
        const val PROMPT_PAUSE_MS = 4_000L
        const val RELISTEN_DELAY_MS = 300L
        const val RETURN_TO_APP_DELAY_MS = 3_000L
        const val ASK_FOREIGNER_TIMEOUT_MS = 15_000L
        val AFFIRMATIVE = Regex("네|예|알겠|그럼요|물론|오케이|좋아요|갈게요|가겠습니다|출발")
    }
}
