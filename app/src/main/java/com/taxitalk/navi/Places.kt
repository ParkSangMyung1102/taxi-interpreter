package com.taxitalk.navi

/**
 * 외국인 승객이 자주 가는 장소 목록 (장소 검색 API를 붙이기 전까지 사용).
 *
 * aliases: 승객이 말할 수 있는 이름들(소문자). 음성 인식이 한국 지명을 잘못 알아듣는 경우가 많아
 *          자주 나오는 변형도 함께 넣었습니다. 앞쪽 장소일수록 먼저 찾습니다.
 * 좌표는 각 장소의 대표 위치(입구·역 등) 근사값입니다.
 */
data class Place(
    val koreanName: String,
    val englishName: String,
    val aliases: List<String>,
    val latitude: Double,
    val longitude: Double,
    val address: String,
)

object Places {
    val all = listOf(
        Place("경복궁", "Gyeongbokgung Palace",
            listOf("gyeongbokgung", "gyeongbok", "kyungbok", "kyeongbok", "gyungbok", "bokgung"),
            37.5796, 126.9770, "서울 종로구 사직로 161"),
        Place("명동", "Myeongdong",
            listOf("myeongdong", "myungdong", "myeong-dong", "myung dong", "myeong dong"),
            37.5636, 126.9826, "서울 중구 명동길"),
        Place("남산서울타워", "N Seoul Tower",
            listOf("n seoul tower", "namsan", "seoul tower"),
            37.5512, 126.9882, "서울 용산구 남산공원길 105"),
        Place("홍대입구역", "Hongik Univ. Station",
            listOf("hongdae", "hongik", "hong dae"),
            37.5572, 126.9245, "서울 마포구 양화로 160"),
        Place("인사동", "Insadong",
            listOf("insadong", "insa-dong", "insa dong"),
            37.5740, 126.9850, "서울 종로구 인사동길"),
        Place("동대문디자인플라자", "Dongdaemun Design Plaza",
            listOf("dongdaemun", "ddp", "design plaza"),
            37.5665, 127.0092, "서울 중구 을지로 281"),
        Place("강남역", "Gangnam Station",
            listOf("gangnam", "kangnam"),
            37.4979, 127.0276, "서울 강남구 강남대로 396"),
        Place("롯데월드", "Lotte World",
            listOf("lotte world", "lotte tower", "jamsil"),
            37.5111, 127.0982, "서울 송파구 올림픽로 240"),
        Place("서울역", "Seoul Station",
            listOf("seoul station"),
            37.5547, 126.9707, "서울 용산구 한강대로 405"),
        Place("인천국제공항", "Incheon Airport",
            listOf("incheon", "inchon", "international airport"),
            37.4492, 126.4509, "인천 중구 공항로 272"),
        Place("김포공항", "Gimpo Airport",
            listOf("gimpo", "kimpo"),
            37.5587, 126.7945, "서울 강서구 하늘길 112"),
        Place("이태원역", "Itaewon Station",
            listOf("itaewon"),
            37.5345, 126.9946, "서울 용산구 이태원로 177"),
        Place("북촌한옥마을", "Bukchon Hanok Village",
            listOf("bukchon", "hanok village"),
            37.5826, 126.9830, "서울 종로구 계동길 37"),
        // 마지막 보루: 이름은 못 알아들었지만 "palace"라고 했다면 가장 유명한 궁궐로 추정
        Place("경복궁", "Gyeongbokgung Palace",
            listOf("palace", "palacio", "palais"),
            37.5796, 126.9770, "서울 종로구 사직로 161"),
    )

    /** 승객 말에서 장소를 찾습니다. 없으면 null */
    fun find(text: String): Place? {
        val t = text.lowercase()
        return all.firstOrNull { place -> place.aliases.any { it in t } }
    }
}
