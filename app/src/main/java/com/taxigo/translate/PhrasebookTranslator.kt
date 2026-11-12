package com.taxigo.translate

import com.taxigo.PassengerLanguage
import com.taxigo.PassengerLanguage.ENGLISH
import com.taxigo.PassengerLanguage.FRENCH
import com.taxigo.PassengerLanguage.SPANISH
import com.taxigo.navi.Places

/**
 * 오프라인용 택시 문장집. 문장 번역이 아니라 키워드로 자주 쓰는 표현만 잡는다.
 * API 키가 없거나 네트워크가 안 될 때 fallback으로 씀.
 */
class PhrasebookTranslator : Translator {

    override fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit) =
        onResult(passengerToKorean(text, from))

    override fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit) =
        onResult(koreanToPassenger(text, to))

    private fun passengerToKorean(text: String, lang: PassengerLanguage): String? {
        val dest = extractDestination(text, lang) ?: Places.find(text)?.koreanName
        if (dest != null) return "${dest.withRo()} 가 주세요."

        val t = text.lowercase() + " "
        return passengerPhrases.getValue(lang).firstOrNull { (keys, _) -> keys.any { it in t } }?.second
    }

    private fun koreanToPassenger(text: String, lang: PassengerLanguage): String? {
        val out = mutableListOf<String>()
        if (Regex("네|예|알겠|그럼요|물론").containsMatchIn(text)) out += Reply.OK.text(lang)
        if ("카드" in text) out += Reply.CARD.text(lang)
        Regex("(\\d+)\\s*분").find(text)?.let { out += minutes(lang, it.groupValues[1]) }
        if ("도착" in text) out += Reply.ARRIVED.text(lang)
        if ("감사" in text || "고맙" in text) out += Reply.THANKS.text(lang)
        if ("안녕" in text) out += Reply.HELLO.text(lang)
        return out.distinct().joinToString(" ").ifEmpty { null }
    }

    // "take me to X, please" -> X
    private fun extractDestination(text: String, lang: PassengerLanguage): String? {
        val lower = text.lowercase()
        val prefix = destinationPrefixes.getValue(lang).firstOrNull { it in lower } ?: return null
        val place = text.substring(lower.indexOf(prefix) + prefix.length)
            .substringBefore(",").substringBefore("?").substringBefore(".")
            .replace(Regex("(?i)\\b(please|por favor|s'il vous pla[iî]t)\\b"), "")
            .replace(Regex("(?i)^(the|el|la|le|les) "), "")
            .trim()
        if (place.isEmpty()) return null
        return Places.find(place)?.koreanName ?: place
    }

    // 받침 없거나 ㄹ받침이면 "로", 아니면 "으로"
    private fun String.withRo(): String {
        val last = lastOrNull() ?: return this
        if (last !in '가'..'힣') return "$this(으)로"
        val jong = (last - '가') % 28
        return if (jong == 0 || jong == 8) "${this}로" else "${this}으로"
    }

    private fun minutes(lang: PassengerLanguage, n: String) = when (lang) {
        ENGLISH -> "It takes about $n minutes."
        SPANISH -> "Tarda unos $n minutos."
        FRENCH -> "Cela prend environ $n minutes."
    }

    private enum class Reply(val en: String, val es: String, val fr: String) {
        OK("Okay.", "De acuerdo.", "D'accord."),
        CARD("Yes, you can pay by card.", "Sí, puede pagar con tarjeta.", "Oui, vous pouvez payer par carte."),
        ARRIVED("We have arrived.", "Hemos llegado.", "Nous sommes arrivés."),
        THANKS("Thank you.", "Gracias.", "Merci."),
        HELLO("Hello.", "Hola.", "Bonjour.");

        fun text(lang: PassengerLanguage) = when (lang) {
            ENGLISH -> en
            SPANISH -> es
            FRENCH -> fr
        }
    }

    private companion object {
        val destinationPrefixes = mapOf(
            ENGLISH to listOf("take me to ", "go to ", "going to ", "drive me to ", "head to "),
            SPANISH to listOf("lléveme a ", "llevame a ", "voy a ", "vamos a ", "ir a "),
            FRENCH to listOf("emmenez-moi à ", "emmenez-moi a ", "je vais à ", "aller à ", "conduisez-moi à "),
        )

        // 위에서부터 먼저 맞는 것 사용
        val passengerPhrases: Map<PassengerLanguage, List<Pair<List<String>, String>>> = mapOf(
            ENGLISH to listOf(
                listOf("airport") to "공항으로 가 주세요.",
                listOf("how long", "how many minutes") to "얼마나 걸리나요?",
                listOf("how much", "fare", "cost") to "요금이 얼마인가요?",
                listOf("card") to "카드로 결제할 수 있나요?",
                listOf("stop here", "right here", "pull over") to "여기서 세워 주세요.",
                listOf("hot", "warm") to "조금 더워요.",
                listOf("cold", "chilly") to "조금 추워요.",
                listOf("thank") to "감사합니다.",
                listOf("hello", "hi ") to "안녕하세요.",
            ),
            SPANISH to listOf(
                listOf("aeropuerto") to "공항으로 가 주세요.",
                listOf("cuánto tiempo", "cuanto tiempo", "cuántos minutos") to "얼마나 걸리나요?",
                listOf("cuánto cuesta", "cuanto cuesta", "tarifa", "cuánto es") to "요금이 얼마인가요?",
                listOf("tarjeta") to "카드로 결제할 수 있나요?",
                listOf("pare aquí", "pare aqui", "aquí está bien") to "여기서 세워 주세요.",
                listOf("calor") to "조금 더워요.",
                listOf("frío", "frio") to "조금 추워요.",
                listOf("gracias") to "감사합니다.",
                listOf("hola") to "안녕하세요.",
            ),
            FRENCH to listOf(
                listOf("aéroport", "aeroport") to "공항으로 가 주세요.",
                listOf("combien de temps", "combien de minutes") to "얼마나 걸리나요?",
                listOf("combien ça coûte", "combien ca coute", "tarif", "c'est combien") to "요금이 얼마인가요?",
                listOf("carte") to "카드로 결제할 수 있나요?",
                listOf("arrêtez-vous ici", "arretez-vous ici", "ici c'est bien") to "여기서 세워 주세요.",
                listOf("chaud") to "조금 더워요.",
                listOf("froid") to "조금 추워요.",
                listOf("merci") to "감사합니다.",
                listOf("bonjour") to "안녕하세요.",
            ),
        )
    }
}
