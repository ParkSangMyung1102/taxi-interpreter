package com.taxitalk

import java.util.Locale

/**
 * 승객이 고를 수 있는 언어 목록.
 *
 * Pleos 음성 SDK가 지원하는 언어(en-US, es-US, fr-CA)만 넣었습니다.
 * 중국어·일본어는 외부 음성 API를 붙이는 2단계에서 추가합니다.
 */
enum class PassengerLanguage(
    val flag: String,
    /** 그 나라 말로 쓴 언어 이름 (승객이 읽는 글자) */
    val nativeName: String,
    /** 기사님이 읽는 한국어 이름 */
    val koreanName: String,
    val locale: Locale,
    /** 언어 선택 화면에서 번갈아 들려주는 안내 */
    val choosePrompt: String,
    /** 언어를 고른 뒤 승객에게 들려주는 첫 인사 */
    val greeting: String,
) {
    ENGLISH(
        flag = "🇺🇸",
        nativeName = "English",
        koreanName = "영어",
        locale = Locale.forLanguageTag("en-US"),
        choosePrompt = "Please choose your language.",
        greeting = "Hello! Welcome to TaxiTalk. Where would you like to go?",
    ),
    SPANISH(
        flag = "🇪🇸",
        nativeName = "Español",
        koreanName = "스페인어",
        locale = Locale.forLanguageTag("es-US"),
        choosePrompt = "Por favor, elija su idioma.",
        greeting = "¡Hola! Bienvenido a TaxiTalk. ¿A dónde le gustaría ir?",
    ),
    FRENCH(
        flag = "🇫🇷",
        nativeName = "Français",
        koreanName = "프랑스어",
        locale = Locale.forLanguageTag("fr-CA"),
        choosePrompt = "Veuillez choisir votre langue.",
        greeting = "Bonjour ! Bienvenue dans TaxiTalk. Où souhaitez-vous aller ?",
    ),
}
