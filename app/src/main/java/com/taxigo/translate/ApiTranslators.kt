package com.taxigo.translate

import android.util.Log
import com.taxigo.PassengerLanguage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

// 대화가 멈추면 안 되니 타임아웃은 짧게. 예외는 전부 null로.
abstract class HttpTranslator : Translator {

    // source/target: ISO 639-1 (ko, en, es, fr)
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

// NCP Papago Translation
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

// Cloud Translation v2
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

// API 실패(오프라인, 키 오류, 타임아웃) 시 문장집으로
class FallbackTranslator(
    private val primary: Translator,
    private val fallback: Translator,
) : Translator {
    override fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit) =
        primary.toKorean(text, from) { it?.let(onResult) ?: fallback.toKorean(text, from, onResult) }

    override fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit) =
        primary.fromKorean(text, to) { it?.let(onResult) ?: fallback.fromKorean(text, to, onResult) }
}
