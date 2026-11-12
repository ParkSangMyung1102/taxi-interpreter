package com.taxigo.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale

/**
 * 한 번 listen() 하면 onFinal(text) 또는 onSilence() 중 하나로 끝난다.
 * 로케일은 매번 지정 (승객 차례: 승객 언어 / 기사 차례: ko-KR).
 */
class Listener(
    context: Context,
    private val credentials: PleosCredentials?,
    private val onError: (String) -> Unit,
) {

    private class Session(
        val onPartial: (String) -> Unit,
        val onFinal: (String) -> Unit,
        val onSilence: () -> Unit,
    ) {
        val sentences = mutableListOf<String>()
    }

    private val stt = SpeechToText(context, if (credentials != null) Mode.HYBRID else Mode.ON_DEVICE)
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var pendingStart: (() -> Unit)? = null
    private var session: Session? = null
    private var finishTask: Runnable? = null

    // feedAudio()에서 직접 stop()한 뒤 오는 ended 이벤트는 버린다
    private var endsToIgnore = 0

    private val results = object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() {}
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}

        override fun onUpdated(stt: String, completed: Boolean) {
            Log.d(TAG, "updated completed=$completed $stt")
            handler.post {
                val s = session ?: return@post
                if (!completed) {
                    cancelFinish()
                    s.onPartial((s.sentences + stt).joinToString(" "))
                } else if (stt.isNotBlank()) {
                    // 말을 잠깐 끊었다 이어 하는 경우가 많아서 바로 넘기지 않고 조금 기다림
                    s.sentences += stt
                    scheduleFinish(s, NEXT_SENTENCE_WAIT_MS)
                }
            }
        }

        override fun onEndedRecognition() {
            Log.d(TAG, "ended")
            handler.post {
                if (endsToIgnore > 0) {
                    endsToIgnore--
                    return@post
                }
                // ended가 최종 결과(completed=true)보다 먼저 오는 경우가 있음. 바로 silence 처리하면 결과를 놓친다.
                val s = session ?: return@post
                if (finishTask == null) scheduleFinish(s, FINAL_RESULT_GRACE_MS)
            }
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "stt $errorType")
            handler.post {
                onError("음성 인식 오류: $errorType")
                session?.let(::finish)
            }
        }
    }

    fun start() {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                handler.post {
                    stt.addListener(results)
                    if (credentials != null) register(credentials) else markReady()
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "stt init $errorType")
                handler.post { onError("음성 인식 초기화 실패: $errorType") }
            }
        })
    }

    // Speaker.register와 같은 방식
    private fun register(c: PleosCredentials) {
        val timeout = Runnable {
            Log.w(TAG, "registerApp timeout")
            markReady()
        }
        handler.postDelayed(timeout, PleosCredentials.REGISTER_TIMEOUT_MS)
        stt.registerApp(c.clientId, c.clientSecret, object : OnServerConnectionListener {
            override fun onConnected() {
                Log.i(TAG, "stt server connected")
                handler.post {
                    handler.removeCallbacks(timeout)
                    markReady()
                }
            }

            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "stt registerApp $errorType")
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
        pendingStart?.invoke()
        pendingStart = null
    }

    fun listen(
        locale: Locale,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onSilence: () -> Unit,
    ) {
        cancel()
        session = Session(onPartial, onFinal, onSilence)
        val begin = {
            stt.setLocale(locale)
            stt.request()
        }
        if (ready) begin() else pendingStart = begin
    }

    /** 디버그용. 마이크 대신 PCM(16kHz/16bit/mono)을 현재 세션에 넣는다. ON_DEVICE 모드에서만 됨. */
    fun feedAudio(locale: Locale, pcm: ByteArray) {
        if (!ready || session == null) return
        endsToIgnore++
        stt.stop()
        stt.setLocale(locale)
        pcm.asList().chunked(CHUNK_100MS).forEach { stt.sendAudio(it.toByteArray()) }
        stt.completeAudioSend()
    }

    fun cancel() {
        pendingStart = null
        cancelFinish()
        if (session != null) {
            session = null
            if (ready) stt.stop()
        }
    }

    fun release() {
        cancel()
        if (ready) stt.removeListener(results)
        stt.release()
    }

    private fun scheduleFinish(s: Session, delayMs: Long) {
        cancelFinish()
        finishTask = Runnable { if (session === s) finish(s) }.also { handler.postDelayed(it, delayMs) }
    }

    private fun cancelFinish() {
        finishTask?.let(handler::removeCallbacks)
        finishTask = null
    }

    private fun finish(s: Session) {
        cancelFinish()
        session = null
        val text = s.sentences.joinToString(" ")
        if (text.isBlank()) {
            s.onSilence()
        } else {
            if (ready) stt.stop()
            s.onFinal(text)
        }
    }

    private companion object {
        const val TAG = "Listener"
        const val FINAL_RESULT_GRACE_MS = 700L
        const val NEXT_SENTENCE_WAIT_MS = 1_200L
        const val CHUNK_100MS = 3_200 // 16000Hz * 2byte * 0.1s
    }
}
