package com.taxigo.translate

import com.taxigo.PassengerLanguage

/** 결과 null = 번역 실패. 콜백은 아무 스레드에서나 올 수 있음. */
interface Translator {
    fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit)
    fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit)
}
