package com.taxitalk

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.taxitalk.vehicle.DoorWatcher
import com.taxitalk.voice.Speaker
import java.util.Locale

/** 지금 앱이 보여 주는 화면(= 상태 머신의 현재 상태) */
sealed interface Screen {
    /** 승객을 기다리는 중 */
    data object Waiting : Screen

    /** 승객이 탔고, 언어를 고르는 중 */
    data object ChooseLanguage : Screen

    /** 언어를 골랐고 인사를 마친 상태 (다음 단계: 통역) */
    data class Talking(val language: PassengerLanguage) : Screen
}

/**
 * 앱의 두뇌.
 * 차량 이벤트(도어)와 화면 입력(국기 터치)을 받아 상태를 바꾸고, 음성 안내를 냅니다.
 */
class TaxiTalkViewModel(app: Application) : AndroidViewModel(app) {

    var screen by mutableStateOf<Screen>(Screen.Waiting)
        private set

    /** 개발 중 확인용 오류 메시지 (차량 SDK 연결 실패 등) */
    var lastError by mutableStateOf<String?>(null)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speaker = Speaker(app, onError = { lastError = it })
    private val doorWatcher = DoorWatcher(
        app,
        onBoarded = ::onPassengerBoarded,
        onError = { lastError = it },
    )

    /** 언어 선택 안내를 몇 바퀴 반복했는지 */
    private var promptRounds = 0

    init {
        speaker.start()
        try {
            doorWatcher.start()
        } catch (e: Exception) {
            // Pleos 전용 에뮬레이터가 아니면 Vehicle Service가 없어 여기서 실패할 수 있습니다.
            lastError = "차량 연결 실패: ${e.message}"
        }
    }

    /** 도어가 열렸다 닫힘 → 승객 탑승 */
    fun onPassengerBoarded() {
        if (screen == Screen.Waiting) showLanguageChoice()
    }

    /** 승객 또는 기사님이 국기를 누름 */
    fun chooseLanguage(language: PassengerLanguage) {
        stopPromptLoop()
        screen = Screen.Talking(language)
        // 기사님께 한국어로 먼저 알려 드리고, 승객에게 그 나라 말로 인사합니다.
        speaker.say(Locale.KOREA, "${language.koreanName} 승객입니다.")
        speaker.say(language.locale, language.greeting)
    }

    /** 통역 화면에서 언어를 다시 고르기 */
    fun changeLanguage() = showLanguageChoice()

    /** 운행 종료 → 다음 승객 대기 */
    fun finishRide() {
        stopPromptLoop()
        screen = Screen.Waiting
    }

    private fun showLanguageChoice() {
        stopPromptLoop()
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

    private fun stopPromptLoop() {
        mainHandler.removeCallbacksAndMessages(null)
        speaker.stopAll()
    }

    override fun onCleared() {
        stopPromptLoop()
        doorWatcher.stop()
        speaker.release()
    }

    private companion object {
        const val MAX_PROMPT_ROUNDS = 3
        const val PROMPT_PAUSE_MS = 4_000L
    }
}
