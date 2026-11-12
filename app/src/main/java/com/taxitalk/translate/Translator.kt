package com.taxitalk.translate

import com.taxitalk.PassengerLanguage

/**
 * 번역기 인터페이스.
 *
 * 지금은 [PhrasebookTranslator](택시 상황 문장집)를 쓰고,
 * 번역 API(Papago, Google 등)가 정해지면 이 인터페이스를 구현한 클래스로 바꿔 끼웁니다.
 * 결과가 null이면 "번역하지 못함"입니다.
 */
interface Translator {
    /** 승객 언어 → 한국어 */
    fun toKorean(text: String, from: PassengerLanguage, onResult: (String?) -> Unit)

    /** 한국어 → 승객 언어 */
    fun fromKorean(text: String, to: PassengerLanguage, onResult: (String?) -> Unit)
}
