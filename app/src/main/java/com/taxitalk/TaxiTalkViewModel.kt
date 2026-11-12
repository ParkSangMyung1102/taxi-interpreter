package com.taxitalk

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.taxitalk.navi.Navigator
import com.taxitalk.navi.Place
import com.taxitalk.navi.Places
import com.taxitalk.translate.PhrasebookTranslator
import com.taxitalk.translate.Translator
import com.taxitalk.vehicle.DoorWatcher
import com.taxitalk.voice.Listener
import com.taxitalk.voice.Speaker
import java.util.Locale

/** 지금 앱이 보여 주는 화면 */
sealed interface Screen {
    /** 승객을 기다리는 중 */
    data object Waiting : Screen

    /** 승객이 탔고, 기사님이 외국인/한국인 승객인지 고르는 중 (소리 없음) */
    data object AskForeigner : Screen

    /** 외국인 승객이고, 언어를 고르는 중 */
    data object ChooseLanguage : Screen

    /** 언어를 골랐고, 통역 중 */
    data class Talking(val language: PassengerLanguage) : Screen
}

/** 통역 중 지금 누구 차례인지 (자동 교대 상태 머신) */
enum class Turn {
    /** 인사말 등 안내를 재생하는 중 */
    GREETING,

    /** 승객 말을 듣는 중 (승객 언어) */
    PASSENGER_LISTENING,

    /** 승객 말을 한국어로 기사님께 들려주는 중 */
    SPEAKING_TO_DRIVER,

    /** 기사님 말을 듣는 중 (한국어) */
    DRIVER_LISTENING,

    /** 기사님 말을 승객 언어로 들려주는 중 */
    SPEAKING_TO_PASSENGER,
}

/** 화면에 보여 줄 대화 한 줄 */
data class ChatLine(
    val fromPassenger: Boolean,
    /** 말한 사람이 실제로 한 말 */
    val original: String,
    /** 상대방 언어로 번역한 말 (번역하지 못했으면 null) */
    val translated: String?,
)

/**
 * 앱의 두뇌.
 * 차량 이벤트(도어)와 화면 입력(국기 터치)을 받아 상태를 바꾸고,
 * 승객 ↔ 기사님 대화를 자동으로 교대하며 통역합니다.
 *
 * 핵심 규칙: 차량 스피커로 말하는 동안에는 절대 듣지 않습니다.
 * (번역 음성을 마이크가 다시 잡아 무한 반복되는 것을 막기 위해,
 *  다음 듣기는 항상 Speaker의 재생 완료 콜백에서 시작합니다.)
 */
class TaxiTalkViewModel(app: Application) : AndroidViewModel(app) {

    private var currentScreen by mutableStateOf<Screen>(Screen.Waiting)
    var screen: Screen
        get() = currentScreen
        private set(value) {
            android.util.Log.d("TaxiTalkVM", "screen $currentScreen -> $value")
            currentScreen = value
        }

    var turn by mutableStateOf(Turn.GREETING)
        private set

    /** 지금 인식 중인 말 (중간 결과) */
    var partialText by mutableStateOf("")
        private set

    /** 이번 운행의 대화 기록 */
    val chat = mutableStateListOf<ChatLine>()

    /** 승객이 말한 목적지 (기사님 확인 전) */
    var pendingPlace by mutableStateOf<Place?>(null)
        private set

    /** 차량 내비게이션으로 안내 중인 목적지와 예상 소요 시간(분) */
    var activeRoute by mutableStateOf<Pair<Place, Int?>?>(null)
        private set

    /** 개발 중 확인용 오류 메시지 */
    var lastError by mutableStateOf<String?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speaker = Speaker(app, onError = { lastError = it })
    private val listener = Listener(app, onError = { lastError = it })
    private val translator: Translator = PhrasebookTranslator()
    private val navigator = Navigator(
        app,
        onRouteStarted = ::onRouteStarted,
        onError = { lastError = it },
    )
    private val doorWatcher = DoorWatcher(
        app,
        onBoarded = ::onPassengerBoarded,
        onError = { lastError = it },
    )

    /** 언어 선택 안내를 몇 바퀴 반복했는지 */
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
            // Pleos 전용 에뮬레이터가 아니면 Vehicle Service가 없어 여기서 실패할 수 있습니다.
            lastError = "차량 연결 실패: ${e.message}"
        }
    }

    // ------------------------------------------------------------------ 탑승과 언어 선택

    /**
     * 도어가 열렸다 닫힘 → 승객 탑승.
     * 대부분은 한국인 승객이므로 외국어 안내 없이, 기사님께 화면으로만 물어봅니다.
     * 아무것도 누르지 않으면 잠시 뒤 한국인 승객으로 보고 대기 화면으로 돌아갑니다.
     */
    fun onPassengerBoarded() {
        if (screen != Screen.Waiting) return
        stopEverything()
        screen = Screen.AskForeigner
        mainHandler.postDelayed(::koreanPassenger, ASK_FOREIGNER_TIMEOUT_MS)
    }

    /** 기사님: [외국인 승객] → 언어 선택 */
    fun foreignPassenger() {
        if (screen == Screen.AskForeigner) showLanguageChoice()
    }

    /** 기사님: [한국인 승객] (또는 시간 초과) → 통역 없이 대기 */
    fun koreanPassenger() {
        if (screen != Screen.AskForeigner) return
        stopEverything()
        screen = Screen.Waiting
    }

    /** 승객 또는 기사님이 국기를 누름 */
    fun chooseLanguage(language: PassengerLanguage) {
        stopEverything()
        chat.clear()
        screen = Screen.Talking(language)
        turn = Turn.GREETING
        // 기사님께 한국어로 먼저 알려 드리고, 승객에게 그 나라 말로 인사한 뒤 승객 차례로 넘어갑니다.
        speaker.say(Locale.KOREA, "${language.koreanName} 승객입니다.")
        speaker.say(language.locale, language.greeting, onDone = ::listenToPassenger)
    }

    /** 통역 화면에서 언어를 다시 고르기 */
    fun changeLanguage() = showLanguageChoice()

    /** 운행 종료 → 다음 승객 대기 */
    fun finishRide() {
        stopEverything()
        chat.clear()
        pendingPlace = null
        activeRoute = null
        screen = Screen.Waiting
    }

    private fun showLanguageChoice() {
        stopEverything()
        screen = Screen.ChooseLanguage
        promptRounds = 0
        playPromptRound()
    }

    /** "Please choose your language"를 언어별로 한 번씩 들려주고, 잠시 쉬었다가 반복 */
    private fun playPromptRound() {
        if (screen != Screen.ChooseLanguage || promptRounds >= MAX_PROMPT_ROUNDS) return
        promptRounds++
        val languages = PassengerLanguage.entries
        languages.forEachIndexed { i, lang ->
            val isLast = i == languages.lastIndex
            speaker.say(lang.locale, lang.choosePrompt, onDone = if (isLast) {
                { mainHandler.postDelayed(::playPromptRound, PROMPT_PAUSE_MS) }
            } else null)
        }
    }

    // ------------------------------------------------------------------ 자동 교대 통역

    /** 승객 차례: 승객 언어로 듣기. 조용하면 계속 다시 듣습니다. */
    private fun listenToPassenger() {
        val lang = language ?: return
        turn = Turn.PASSENGER_LISTENING
        partialText = ""
        listener.listen(
            lang.locale,
            onPartial = { partialText = it },
            onFinal = ::onPassengerSaid,
            onSilence = {
                if (turn == Turn.PASSENGER_LISTENING) {
                    mainHandler.postDelayed(::listenToPassenger, RELISTEN_DELAY_MS)
                }
            },
        )
    }

    private fun onPassengerSaid(text: String) {
        val lang = language ?: return
        partialText = ""
        // 아는 장소를 말했다면 목적지 카드를 띄워 기사님 확인을 기다립니다.
        Places.find(text)?.let { pendingPlace = it }
        translator.toKorean(text, lang) { korean ->
            mainHandler.post {
                chat += ChatLine(fromPassenger = true, original = text, translated = korean)
                turn = Turn.SPEAKING_TO_DRIVER
                val forDriver = korean ?: "승객 말을 번역하지 못했어요. 화면을 확인해 주세요."
                speaker.say(Locale.KOREA, forDriver, onDone = ::listenToDriver)
            }
        }
    }

    /** 기사님 차례: 한국어로 듣기. 대답이 없으면 승객 차례로 돌아갑니다. */
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
        // 목적지 카드가 떠 있을 때 기사님이 "네"라고 하면 바로 안내를 시작합니다.
        if (pendingPlace != null && AFFIRMATIVE.containsMatchIn(text)) startNavigation()
        translator.fromKorean(text, lang) { translated ->
            mainHandler.post {
                chat += ChatLine(fromPassenger = false, original = text, translated = translated)
                if (translated == null) {
                    // 승객에게 엉뚱한 말을 들려주지 않고, 기사님께만 알려 드립니다.
                    turn = Turn.SPEAKING_TO_DRIVER
                    speaker.say(Locale.KOREA, "번역하지 못했어요. 다시 말씀해 주세요.", onDone = ::listenToDriver)
                } else {
                    turn = Turn.SPEAKING_TO_PASSENGER
                    speaker.say(lang.locale, translated, onDone = ::listenToPassenger)
                }
            }
        }
    }

    // ------------------------------------------------------------------ 내비게이션

    /** 목적지 카드의 [안내 시작] 또는 기사님의 "네" → 차량 내비게이션에 목적지 설정 */
    fun startNavigation() {
        val place = pendingPlace ?: return
        pendingPlace = null
        navigator.go(place)
    }

    /** 목적지 카드 닫기 (잘못 알아들은 경우) */
    fun dismissPlace() {
        pendingPlace = null
    }

    /** 내비게이션이 경로를 찾음 → 승객에게 그 나라 말로 알려 드립니다. */
    private fun onRouteStarted(place: Place, minutes: Int?) {
        val lang = language ?: return
        activeRoute = place to minutes
        announceToPassenger(lang, routeMessage(lang, place, minutes))
        // 내비가 화면을 가져가므로, 경로가 잡히면 통역 화면을 다시 앞으로 가져옵니다.
        // (내비 음성 안내는 뒤에서 계속되고, 왼쪽 드라이빙 뷰에 턴바이턴 안내가 표시됨.
        //  지도는 [지도 보기]나 하단 바로 언제든 볼 수 있음)
        // 참고: Pleos 에뮬레이터 v2.1에서 이 호출은 로그에 BAL_BLOCK으로 찍히지만 실제로 앱이 앞으로 옵니다.
        //       실차에서 막히면 기사님이 하단 바의 앱 아이콘을 한 번 누르면 됩니다.
        mainHandler.postDelayed(::bringAppToFront, RETURN_TO_APP_DELAY_MS)
    }

    private fun bringAppToFront() {
        val app = getApplication<Application>()
        val intent = Intent(app, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            lastError = "통역 화면으로 돌아오지 못했어요: ${e.message}"
        }
    }

    /** 기사님이 지도를 보고 싶을 때 */
    fun showMap() = navigator.showMap()

    private fun routeMessage(lang: PassengerLanguage, place: Place, minutes: Int?): String = when (lang) {
        PassengerLanguage.ENGLISH -> "Navigation to ${place.englishName} has started." +
            (minutes?.let { " It takes about $it minutes." } ?: "")
        PassengerLanguage.SPANISH -> "Se inició la navegación a ${place.englishName}." +
            (minutes?.let { " Tarda unos $it minutos." } ?: "")
        PassengerLanguage.FRENCH -> "La navigation vers ${place.englishName} a commencé." +
            (minutes?.let { " Cela prend environ $it minutes." } ?: "")
    }

    /**
     * 대화 중간에 승객에게 안내를 끼워 넣습니다.
     * 듣는 중이었다면 마이크를 먼저 끄고(자기 목소리를 듣지 않도록) 말한 뒤 같은 차례로 돌아갑니다.
     */
    private fun announceToPassenger(lang: PassengerLanguage, text: String) {
        when (turn) {
            Turn.PASSENGER_LISTENING, Turn.DRIVER_LISTENING -> {
                val resume: () -> Unit =
                    if (turn == Turn.DRIVER_LISTENING) ::listenToDriver else ::listenToPassenger
                listener.cancel()
                mainHandler.removeCallbacksAndMessages(null)
                turn = Turn.SPEAKING_TO_PASSENGER
                speaker.say(lang.locale, text, onDone = resume)
            }
            // 이미 말하는 중이면 대기열 뒤에 붙입니다. (끝나면 원래 예정된 차례로 넘어감)
            else -> speaker.say(lang.locale, text)
        }
    }

    /** 기사님이 먼저 말하고 싶을 때 (승객 차례를 끊고 기사님 차례로) */
    fun driverWantsToSpeak() {
        if (language == null) return
        mainHandler.removeCallbacksAndMessages(null)
        speaker.stopAll()
        listenToDriver()
    }

    /** 개발용: 마이크 대신 녹음 파일을 지금 차례의 언어로 인식시킵니다. */
    fun feedTestAudio(pcm: ByteArray) {
        val lang = language ?: return
        val locale = if (turn == Turn.DRIVER_LISTENING) Locale.KOREA else lang.locale
        listener.feedAudio(locale, pcm)
    }

    private fun stopEverything() {
        mainHandler.removeCallbacksAndMessages(null)
        listener.cancel()
        speaker.stopAll()
        partialText = ""
    }

    override fun onCleared() {
        stopEverything()
        doorWatcher.stop()
        navigator.release()
        listener.release()
        speaker.release()
    }

    private companion object {
        const val MAX_PROMPT_ROUNDS = 3
        const val PROMPT_PAUSE_MS = 4_000L
        const val RELISTEN_DELAY_MS = 300L

        /** 경로가 잡힌 뒤 통역 화면으로 돌아오기까지 (기사님이 경로를 한눈에 볼 시간) */
        const val RETURN_TO_APP_DELAY_MS = 3_000L

        /** 외국인/한국인 선택 화면에서 아무것도 누르지 않으면 한국인 승객으로 보는 시간 */
        const val ASK_FOREIGNER_TIMEOUT_MS = 15_000L

        /** 기사님의 "네, 갈게요" 같은 승낙 표현 */
        val AFFIRMATIVE = Regex("네|예|알겠|그럼요|물론|오케이|좋아요|갈게요|가겠습니다|출발")
    }
}
