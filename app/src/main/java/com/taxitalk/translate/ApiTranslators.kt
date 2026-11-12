package com.taxitalk.translate

import android.util.Log
import com.taxitalk.PassengerLanguage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * 인터넷 번역 API를 쓰는 번역기들의 공통 부분.
 * - 네트워크는 백그라운드 스레드에서 호출하고, 결과는 onResult로 돌려줍니다(호출한 쪽에서 메인 스레드로 옮김).
 * - 차 안 대화가 멈추지 않도록 짧은 제한 시간을 둡니다. 실패하면 null을 돌려줍니다.
 */
abstract class HttpTranslator : Translator {

    /** source/target: "ko", "en", "es", "fr" 같은 언어 코드 */
    protected abstract fun request(text: String, source: String, target: String): String?

    override fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit) =
        run(text, from.locale.language, "ko", onResult)

    override fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit) =
        run(text, "ko", to.locale.language, onResult)

    private fun run(text: String, source: String, target: String, onResult: (String?) -> Unit) {
        executor.execute {
            val result = try {
                request(text, source, target)?.trim()?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.w(TAG, "${javaClass.simpleName} failed: ${e.message}")
                null
            }
            onResult(result)
        }
    }

    protected fun post(url: String, headers: Map<String, String>, contentType: String, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", contentType)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code ${response.take(200)}")
            return response
        } finally {
            conn.disconnect()
        }
    }

    protected companion object {
        const val TAG = "Translator"
        const val TIMEOUT_MS = 4_000
        private val executor = Executors.newSingleThreadExecutor()

        fun form(vararg pairs: Pair<String, String>) =
            pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }
}

/** 네이버 클라우드 Papago Translation (한국어 번역 품질이 좋음) */
class PapagoTranslator(private val clientId: String, private val clientSecret: String) : HttpTranslator() {
    override fun request(text: String, source: String, target: String): String? {
        val json = post(
            url = "https://papago.apigw.ntruss.com/nmt/v1/translation",
            headers = mapOf(
                "X-NCP-APIGW-API-KEY-ID" to clientId,
                "X-NCP-APIGW-API-KEY" to clientSecret,
            ),
            contentType = "application/x-www-form-urlencoded; charset=UTF-8",
            body = form("source" to source, "target" to target, "text" to text),
        )
        return JSONObject(json).getJSONObject("message").getJSONObject("result").getString("translatedText")
    }
}

/** Google Cloud Translation (v2) */
class GoogleTranslator(private val apiKey: String) : HttpTranslator() {
    override fun request(text: String, source: String, target: String): String? {
        val body = JSONObject()
            .put("q", text)
            .put("source", source)
            .put("target", target)
            .put("format", "text")
            .toString()
        val json = post(
            url = "https://translation.googleapis.com/language/translate/v2",
            headers = mapOf("X-Goog-Api-Key" to apiKey),
            contentType = "application/json; charset=UTF-8",
            body = body,
        )
        return JSONObject(json).getJSONObject("data").getJSONArray("translations")
            .getJSONObject(0).getString("translatedText")
    }
}

/**
 * 먼저 primary(번역 API)로 번역하고, 실패하면 fallback(택시 문장집)으로 번역합니다.
 * 인터넷이 끊겨도 기본적인 택시 대화는 계속되도록 하기 위함입니다.
 */
class FallbackTranslator(
    private val primary: Translator,
    private val fallback: Translator,
) : Translator {
    override fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit) =
        primary.toKorean(text, from) { it?.let(onResult) ?: fallback.toKorean(text, from, onResult) }

    override fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit) =
        primary.fromKorean(text, to) { it?.let(onResult) ?: fallback.fromKorean(text, to, onResult) }
}
