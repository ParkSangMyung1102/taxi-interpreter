package com.taxitalk.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/**
 * 차량 스피커로 문장을 "순서대로" 읽어 주는 도우미.
 *
 * - Pleos TextToSpeech SDK를 온디바이스 모드로 씁니다(앱 등록·네트워크 불필요).
 * - 한 문장이 끝나야(onDone) 다음 문장을 읽습니다. 문장마다 언어가 달라도 됩니다.
 * - 나중에 통역 단계에서 "말하는 동안 마이크를 끄기" 위해 isSpeaking 상태를 제공합니다.
 */
class Speaker(context: Context, private val onError: (String) -> Unit) {

    private data class Utterance(val locale: Locale, val text: String)

    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Utterance>()
    private var ready = false

    /** 지금 차량 스피커로 말하는 중인지 */
    var isSpeaking = false
        private set

    /** 대기열이 모두 끝났을 때 한 번 호출 */
    private var onQueueDone: (() -> Unit)? = null

    private val eventListener = object : EventListener {
        override fun onReady() {}
        override fun onStart() {}
        override fun onUpdatedRms(rms: Double) {}

        override fun onDone() {
            mainHandler.post { speakNext() }
        }

        override fun onStop() {
            mainHandler.post { isSpeaking = false }
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "tts error $errorType")
            mainHandler.post {
                onError("음성 출력 오류: $errorType")
                speakNext()
            }
        }
    }

    fun start() {
        // 순서 주의: initialize()가 성공한 뒤에 addEventListener()를 불러야 합니다.
        // (먼저 부르면 SDK 내부가 준비되지 않아 앱이 종료됩니다)
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                mainHandler.post {
                    tts.addEventListener(eventListener)
                    ready = true
                    if (!isSpeaking) speakNext()
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "tts init failed $errorType")
                mainHandler.post { onError("음성 출력 초기화 실패: $errorType") }
            }
        })
    }

    /** 문장을 대기열 끝에 추가. onDone은 대기열이 모두 끝났을 때 호출됩니다. */
    fun say(locale: Locale, text: String, onDone: (() -> Unit)? = null) {
        queue.addLast(Utterance(locale, text))
        if (onDone != null) onQueueDone = onDone
        if (ready && !isSpeaking) speakNext()
    }

    /** 지금 말하던 것과 대기열을 모두 취소 */
    fun stopAll() {
        queue.clear()
        onQueueDone = null
        if (isSpeaking) tts.stop()
        isSpeaking = false
    }

    fun release() {
        stopAll()
        if (ready) tts.removeEventListener(eventListener)
        tts.release()
    }

    private fun speakNext() {
        val next = queue.removeFirstOrNull()
        if (next == null) {
            isSpeaking = false
            val done = onQueueDone
            onQueueDone = null
            done?.invoke()
            return
        }
        isSpeaking = true
        tts.setLocale(next.locale)
        tts.speak(next.text, true)
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
