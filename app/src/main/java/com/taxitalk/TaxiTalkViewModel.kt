package com.taxitalk

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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

    /** 승객이 탔고, 언어를 고르는 중 */
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

    var screen by mutableStateOf<Screen>(Screen.Waiting)
        private set

    var turn by mutableStateOf(Turn.GREETING)
        private set

    /** 지금 인식 중인 말 (중간 결과) */
    var partialText by mutableStateOf("")
        private set

    /** 이번 운행의 대화 기록 */
    val chat = mutableStateListOf<ChatLine>()

    /** 개발 중 확인용 오류 메시지 */
    var lastError by mutableStateOf<String?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speaker = Speaker(app, onError = { lastError = it })
    private val listener = Listener(app, onError = { lastError = it })
    private val translator: Translator = PhrasebookTranslator()
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
        try {
            doorWatcher.start()
        } catch (e: Exception) {
            // Pleos 전용 에뮬레이터가 아니면 Vehicle Service가 없어 여기서 실패할 수 있습니다.
            lastError = "차량 연결 실패: ${e.message}"
        }
    }

    // ------------------------------------------------------------------ 탑승과 언어 선택

    /** 도어가 열렸다 닫힘 → 승객 탑승 */
    fun onPassengerBoarded() {
        if (screen == Screen.Waiting) showLanguageChoice()
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
        listener.release()
        speaker.release()
    }

    private companion object {
        const val MAX_PROMPT_ROUNDS = 3
        const val PROMPT_PAUSE_MS = 4_000L
        const val RELISTEN_DELAY_MS = 300L
    }
}
