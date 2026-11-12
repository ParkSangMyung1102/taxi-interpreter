package com.taxitalk.translate

import com.taxitalk.PassengerLanguage
import com.taxitalk.PassengerLanguage.ENGLISH
import com.taxitalk.PassengerLanguage.FRENCH
import com.taxitalk.PassengerLanguage.SPANISH
import com.taxitalk.navi.Places

/**
 * 번역 API를 붙이기 전까지 쓰는 "택시 상황 문장집" 번역기.
 *
 * 문장 전체를 번역하지 않고, 택시에서 자주 나오는 말을 키워드로 알아봅니다.
 * - 승객: 목적지("take me to ○○"), 공항, 소요 시간, 요금, 카드, 정차, 덥다/춥다, 감사
 * - 기사님: 네/알겠습니다, 카드 됩니다, ○분 걸려요, 도착했습니다, 감사합니다
 * 알아보지 못한 말은 null을 돌려줍니다.
 */
class PhrasebookTranslator : Translator {

    override fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit) {
        onResult(passengerToKorean(text, from))
    }

    override fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit) {
        onResult(koreanToPassenger(text, to))
    }

    // ---------------------------------------------------------------- 승객 → 한국어

    private fun passengerToKorean(text: String, lang: PassengerLanguage): String? {
        val t = text.lowercase()
        // "take me to ○○" 형태이거나, 아는 장소 이름이 들어 있으면 목적지 요청으로 봅니다.
        (destination(text, lang) ?: Places.find(text)?.koreanName)
            ?.let { return "${it.withEuro()} 가 주세요." }

        val rules: List<Pair<List<String>, String>> = when (lang) {
            ENGLISH -> listOf(
                listOf("airport") to "공항으로 가 주세요.",
                listOf("how long", "how many minutes") to "얼마나 걸리나요?",
                listOf("how much", "fare", "cost") to "요금이 얼마인가요?",
                listOf("card") to "카드로 결제할 수 있나요?",
                listOf("stop here", "right here", "pull over") to "여기서 세워 주세요.",
                listOf("hot", "warm") to "조금 더워요.",
                listOf("cold", "chilly") to "조금 추워요.",
                listOf("thank") to "감사합니다.",
                listOf("hello", "hi ") to "안녕하세요.",
            )
            SPANISH -> listOf(
                listOf("aeropuerto") to "공항으로 가 주세요.",
                listOf("cuánto tiempo", "cuanto tiempo", "cuántos minutos") to "얼마나 걸리나요?",
                listOf("cuánto cuesta", "cuanto cuesta", "tarifa", "cuánto es") to "요금이 얼마인가요?",
                listOf("tarjeta") to "카드로 결제할 수 있나요?",
                listOf("pare aquí", "pare aqui", "aquí está bien") to "여기서 세워 주세요.",
                listOf("calor") to "조금 더워요.",
                listOf("frío", "frio") to "조금 추워요.",
                listOf("gracias") to "감사합니다.",
                listOf("hola") to "안녕하세요.",
            )
            FRENCH -> listOf(
                listOf("aéroport", "aeroport") to "공항으로 가 주세요.",
                listOf("combien de temps", "combien de minutes") to "얼마나 걸리나요?",
                listOf("combien ça coûte", "combien ca coute", "tarif", "c'est combien") to "요금이 얼마인가요?",
                listOf("carte") to "카드로 결제할 수 있나요?",
                listOf("arrêtez-vous ici", "arretez-vous ici", "ici c'est bien") to "여기서 세워 주세요.",
                listOf("chaud") to "조금 더워요.",
                listOf("froid") to "조금 추워요.",
                listOf("merci") to "감사합니다.",
                listOf("bonjour") to "안녕하세요.",
            )
        }
        return rules.firstOrNull { (keys, _) -> keys.any { it in "$t " } }?.second
    }

    /** "take me to ○○", "go to ○○" 같은 목적지 말하기에서 ○○를 꺼냅니다. */
    private fun destination(text: String, lang: PassengerLanguage): String? {
        val patterns = when (lang) {
            ENGLISH -> listOf("take me to ", "go to ", "going to ", "drive me to ", "head to ")
            SPANISH -> listOf("lléveme a ", "llevame a ", "voy a ", "vamos a ", "ir a ")
            FRENCH -> listOf("emmenez-moi à ", "emmenez-moi a ", "je vais à ", "aller à ", "conduisez-moi à ")
        }
        val lower = text.lowercase()
        val p = patterns.firstOrNull { it in lower } ?: return null
        val start = lower.indexOf(p) + p.length
        val place = text.substring(start)
            .substringBefore(",")
            .substringBefore("?")
            .substringBefore(".")
            .replace(Regex("(?i)\\b(please|por favor|s'il vous plaît|s'il vous plait)\\b"), "")
            .replace(Regex("(?i)^(the|el|la|le|les) "), "")
            .trim()
        if (place.isBlank()) return null
        return Places.find(place)?.koreanName ?: place
    }

    /** "경복궁" → "경복궁으로", "남대문" → "남대문으로", "공항" → "공항으로" (받침에 따라 로/으로) */
    private fun String.withEuro(): String {
        val last = lastOrNull() ?: return this
        if (last !in '가'..'힣') return "$this(으)로"
        val jong = (last - '가') % 28
        return if (jong == 0 || jong == 8) "${this}로" else "${this}으로"
    }

    // ---------------------------------------------------------------- 한국어 → 승객

    private fun koreanToPassenger(text: String, lang: PassengerLanguage): String? {
        val parts = mutableListOf<String>()
        if (Regex("네|예|알겠|그럼요|물론").containsMatchIn(text)) parts += say(lang, "ok")
        if ("카드" in text) parts += say(lang, "card")
        Regex("(\\d+)\\s*분").find(text)?.let { parts += minutes(lang, it.groupValues[1]) }
        if ("도착" in text) parts += say(lang, "arrived")
        if ("감사" in text || "고맙" in text) parts += say(lang, "thanks")
        if ("안녕" in text) parts += say(lang, "hello")
        return parts.distinct().takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun say(lang: PassengerLanguage, key: String): String = when (key) {
        "ok" -> when (lang) { ENGLISH -> "Okay."; SPANISH -> "De acuerdo."; FRENCH -> "D'accord." }
        "card" -> when (lang) {
            ENGLISH -> "Yes, you can pay by card."
            SPANISH -> "Sí, puede pagar con tarjeta."
            FRENCH -> "Oui, vous pouvez payer par carte."
        }
        "arrived" -> when (lang) {
            ENGLISH -> "We have arrived."
            SPANISH -> "Hemos llegado."
            FRENCH -> "Nous sommes arrivés."
        }
        "thanks" -> when (lang) { ENGLISH -> "Thank you."; SPANISH -> "Gracias."; FRENCH -> "Merci." }
        "hello" -> when (lang) { ENGLISH -> "Hello."; SPANISH -> "Hola."; FRENCH -> "Bonjour." }
        else -> ""
    }

    private fun minutes(lang: PassengerLanguage, n: String) = when (lang) {
        ENGLISH -> "It takes about $n minutes."
        SPANISH -> "Tarda unos $n minutos."
        FRENCH -> "Cela prend environ $n minutes."
    }
}
