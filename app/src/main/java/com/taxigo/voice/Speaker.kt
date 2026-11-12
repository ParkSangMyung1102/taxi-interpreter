package com.taxigo.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import java.util.Locale

/** 문장 단위 TTS 큐. 문장마다 로케일을 바꿔 순서대로 재생한다. */
class Speaker(
    context: Context,
    private val credentials: PleosCredentials?,
    private val onError: (String) -> Unit,
) {

    private class Utterance(val locale: Locale, val text: String)

    // 자격 증명 없으면 ON_DEVICE (앱 등록/네트워크 불필요)
    private val tts = TextToSpeech(context, if (credentials != null) Mode.HYBRID else Mode.ON_DEVICE)
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Utterance>()
    private var ready = false
    private var speaking = false
    private var onQueueDone: (() -> Unit)? = null

    private val events = object : EventListener {
        override fun onReady() {}
        override fun onStart() {}
        override fun onUpdatedRms(rms: Double) {}

        override fun onDone() {
            handler.post { next() }
        }

        override fun onStop() {
            handler.post { speaking = false }
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "tts $errorType")
            handler.post {
                onError("음성 출력 오류: $errorType")
                next()
            }
        }
    }

    fun start() {
        // addEventListener를 initialize 성공 전에 부르면 SDK 내부 lateinit 때문에 크래시
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                handler.post {
                    tts.addEventListener(events)
                    if (credentials != null) register(credentials) else markReady()
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "tts init $errorType")
                handler.post { onError("음성 출력 초기화 실패: $errorType") }
            }
        })
    }

    // 서버 요청은 onConnected 이후에. 실패/무응답이어도 막지 않고 진행 -> HYBRID가 온디바이스로 처리
    private fun register(c: PleosCredentials) {
        val timeout = Runnable {
            Log.w(TAG, "registerApp timeout")
            markReady()
        }
        handler.postDelayed(timeout, PleosCredentials.REGISTER_TIMEOUT_MS)
        tts.registerApp(c.clientId, c.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "tts server connected")
                handler.post {
                    handler.removeCallbacks(timeout)
                    markReady()
                }
            }

            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "tts registerApp $errorType")
                handler.post {
                    handler.removeCallbacks(timeout)
                    onError("음성 서버 연결 실패($errorType), 차량 내 엔진 사용")
                    markReady()
                }
            }
        })
    }

    private fun markReady() {
        if (ready) return
        ready = true
        if (!speaking) next()
    }

    /** onDone은 큐가 다 비었을 때 한 번 호출된다. (null이면 기존 콜백 유지) */
    fun say(locale: Locale, text: String, onDone: (() -> Unit)? = null) {
        queue.addLast(Utterance(locale, text))
        if (onDone != null) onQueueDone = onDone
        if (ready && !speaking) next()
    }

    fun stopAll() {
        queue.clear()
        onQueueDone = null
        if (speaking) tts.stop()
        speaking = false
    }

    fun release() {
        stopAll()
        if (ready) tts.removeEventListener(events)
        tts.release()
    }

    private fun next() {
        val u = queue.removeFirstOrNull()
        if (u == null) {
            speaking = false
            onQueueDone?.also { onQueueDone = null }?.invoke()
            return
        }
        speaking = true
        tts.setLocale(u.locale)
        tts.speak(u.text, true)
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
