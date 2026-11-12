package com.taxitalk

import java.util.Locale

// Pleos STT/TTS 지원 로케일(en-US, es-US, fr-CA)만. zh/ja는 외부 음성 API 붙인 뒤 추가.
enum class PassengerLanguage(
    val flag: String,
    val nativeName: String,
    val koreanName: String,
    val locale: Locale,
    val choosePrompt: String,
    val greeting: String,
) {
    ENGLISH(
        flag = "🇺🇸",
        nativeName = "English",
        koreanName = "영어",
        locale = Locale.forLanguageTag("en-US"),
        choosePrompt = "Please choose your language.",
        greeting = "Hello! Welcome to Korea. Where would you like to go?",
    ),
    SPANISH(
        flag = "🇪🇸",
        nativeName = "Español",
        koreanName = "스페인어",
        locale = Locale.forLanguageTag("es-US"),
        choosePrompt = "Por favor, elija su idioma.",
        greeting = "¡Hola! Bienvenido a Corea. ¿A dónde le gustaría ir?",
    ),
    FRENCH(
        flag = "🇫🇷",
        nativeName = "Français",
        koreanName = "프랑스어",
        locale = Locale.forLanguageTag("fr-CA"),
        choosePrompt = "Veuillez choisir votre langue.",
        greeting = "Bonjour ! Bienvenue en Corée. Où souhaitez-vous aller ?",
    );

    fun routeStarted(place: String, minutes: Int?): String = when (this) {
        ENGLISH -> "Navigation to $place has started." + (minutes?.let { " It takes about $it minutes." } ?: "")
        SPANISH -> "Se inició la navegación a $place." + (minutes?.let { " Tarda unos $it minutos." } ?: "")
        FRENCH -> "La navigation vers $place a commencé." + (minutes?.let { " Cela prend environ $it minutes." } ?: "")
    }
}
