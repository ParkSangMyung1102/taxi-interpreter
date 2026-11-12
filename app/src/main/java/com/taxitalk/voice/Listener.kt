package com.taxitalk.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale

/**
 * 차량 마이크로 한 번의 발화를 듣고 텍스트로 돌려주는 도우미.
 *
 * - Pleos SpeechToText SDK를 온디바이스 모드로 씁니다(앱 등록·네트워크 불필요).
 * - 인식 언어는 들을 때마다 지정합니다. (승객 차례 = 승객 언어, 기사님 차례 = 한국어)
 * - 한 번 listen() 하면 결과는 둘 중 하나로 끝납니다.
 *     onFinal(text)  : 말을 알아들음
 *     onSilence()    : 아무 말이 없었거나 알아듣지 못함
 */
class Listener(context: Context, private val onError: (String) -> Unit) {

    private val stt = SpeechToText(context, Mode.ON_DEVICE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ready = false
    private var pendingStart: (() -> Unit)? = null

    private var session: Session? = null

    /** 우리가 일부러 stop()해서 생기는 "인식 종료" 이벤트는 무시합니다. */
    private var endsToIgnore = 0

    private class Session(
        val onPartial: (String) -> Unit,
        val onFinal: (String) -> Unit,
        val onSilence: () -> Unit,
    ) {
        /** 지금까지 확정된 문장들 (말을 잠깐 끊었다 이어 하면 여러 개가 됩니다) */
        val sentences = mutableListOf<String>()
        val text get() = sentences.joinToString(" ")
    }

    /** 세션을 끝내기로 예약한 작업 (말이 이어지면 취소하고 다시 예약) */
    private var finishTask: Runnable? = null

    private val resultListener = object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() {}
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}

        override fun onUpdated(stt: String, completed: Boolean) {
            Log.d(TAG, "updated completed=$completed text=$stt")
            mainHandler.post {
                val s = session ?: return@post
                if (!completed) {
                    // 아직 말하는 중 → 끝내기 예약을 미룹니다.
                    cancelFinish()
                    s.onPartial((s.sentences + stt).joinToString(" "))
                } else if (stt.isNotBlank()) {
                    // 한 문장이 확정됨 → 이어지는 말이 있는지 잠깐 기다렸다가 합쳐서 넘깁니다.
                    s.sentences += stt
                    scheduleFinish(s, NEXT_SENTENCE_WAIT_MS)
                }
            }
        }

        override fun onEndedRecognition() {
            Log.d(TAG, "ended")
            mainHandler.post {
                if (endsToIgnore > 0) {
                    endsToIgnore--
                    return@post
                }
                // 주의: Pleos 인식기는 "종료" 이벤트를 최종 결과보다 먼저 보낼 때가 있습니다.
                // 그래서 바로 "말이 없었다"고 판단하지 않고, 최종 결과가 올 시간을 잠깐 줍니다.
                val s = session ?: return@post
                if (finishTask == null) scheduleFinish(s, FINAL_RESULT_GRACE_MS)
            }
        }

        override fun onError(errorType: ErrorType) {
            Log.e(TAG, "stt error $errorType")
            mainHandler.post {
                onError("음성 인식 오류: $errorType")
                session?.let { finishSession(it) }
            }
        }
    }

    fun start() {
        // TextToSpeech와 같은 방식으로, 초기화가 끝난 뒤 리스너를 등록합니다.
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                mainHandler.post {
                    stt.addListener(resultListener)
                    ready = true
                    pendingStart?.invoke()
                    pendingStart = null
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "stt init failed $errorType")
                mainHandler.post { onError("음성 인식 초기화 실패: $errorType") }
            }
        })
    }

    /** 차량 마이크로 한 번 듣기 */
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

    /**
     * 개발용: 마이크 대신 녹음 파일(16kHz, 16bit, 모노 PCM)을 인식시킵니다.
     * 지금 듣고 있던 세션의 콜백을 그대로 사용합니다.
     */
    fun feedAudio(locale: Locale, pcm: ByteArray) {
        if (!ready || session == null) return
        stopEngine()
        stt.setLocale(locale)
        pcm.asList().chunked(BYTES_PER_100_MS).forEach { stt.sendAudio(it.toByteArray()) }
        stt.completeAudioSend()
    }

    /** 듣기를 그만두고, 이번 세션의 콜백도 부르지 않습니다. */
    fun cancel() {
        pendingStart = null
        cancelFinish()
        if (session != null) {
            session = null
            // 세션을 비웠으므로 뒤따라오는 종료 이벤트는 저절로 무시됩니다.
            if (ready) stt.stop()
        }
    }

    fun release() {
        cancel()
        if (ready) stt.removeListener(resultListener)
        stt.release()
    }

    private fun stopEngine() {
        if (!ready) return
        endsToIgnore++
        stt.stop()
    }

    private fun scheduleFinish(s: Session, delayMs: Long) {
        cancelFinish()
        val task = Runnable { if (session === s) finishSession(s) }
        finishTask = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun cancelFinish() {
        finishTask?.let { mainHandler.removeCallbacks(it) }
        finishTask = null
    }

    private fun finishSession(s: Session) {
        cancelFinish()
        session = null
        val text = s.text
        if (text.isBlank()) {
            s.onSilence()
        } else {
            // 결과를 받았으니 남은 인식은 멈춥니다. (그 종료 이벤트는 세션이 없어 무시됨)
            if (ready) stt.stop()
            s.onFinal(text)
        }
    }

    private companion object {
        const val TAG = "Listener"
        /** 종료 이벤트 뒤 최종 결과를 기다리는 시간 */
        const val FINAL_RESULT_GRACE_MS = 700L
        /** 한 문장이 확정된 뒤, 이어지는 말을 기다리는 시간 */
        const val NEXT_SENTENCE_WAIT_MS = 1_200L
        /** 16kHz × 16bit × 모노 = 초당 32,000바이트 → 100ms당 3,200바이트 */
        const val BYTES_PER_100_MS = 3_200
    }
}
