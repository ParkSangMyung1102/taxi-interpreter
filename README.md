# 기사님 출발해주세요 (Taxigo)

택시에 외국인 승객이 타면 차량 마이크·스피커로 기사님과 승객의 대화를 통역하고, 승객이 말한 목적지를 순정 내비게이션에 바로 설정해 주는 Pleos Connect 차량용 앱.

## APK

- [`apk/박상명_기사님출발해주세요_release.apk`](apk/)
- 패키지 `com.taxigo`, versionName 0.1.0 (release 빌드, 공모전 제출용으로 debug 키 서명)

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator **v2.1.0** (AVD `Pleos_Connect_v2.1.0`, Android 14 / API 34, x86_64) |
| 화면 규격 | 2560 × 1440, 160 dpi. 앱 영역 1682 × 1210 (드라이빙 뷰 오른쪽) |
| 개발 도구 | Android Studio, JDK 17+, Gradle 9.6, AGP 9.5.1, Kotlin + Jetpack Compose |
| minSdk / targetSdk | 28 / 34 |

사용한 Pleos Connect SDK

| SDK | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 승객 도어 열림/닫힘으로 탑승 감지 (`CAR_DOORS`) |
| SpeechToText | 3.3.1 | 승객(영/스/프)·기사(한국어) 음성 인식 |
| TextToSpeech | 3.3.1 | 번역 결과를 차량 스피커로 재생 |
| NaviHelper | 3.0.5 | 목적지 설정, 경로 시작 시 예상 소요 시간 |

## 실행 방법

1. Android Studio SDK Manager에서 Pleos Connect system image(API 34)를 설치하고 AVD를 Cold boot로 실행
2. APK 설치
   ```bash
   adb install apk/박상명_기사님출발해주세요_release.apk
   ```
3. 앱 실행 후 버추얼 콘솔 연결
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 http://127.0.0.1:8088 → Convenience → 뒷문(RL/RR) 열기 → 닫기
4. "외국인 승객인가요?" → [외국인 승객] → 국기 선택 → 영어로 목적지를 말하면 통역/목적지 카드 표시 → 한국어로 "네"라고 답하면 내비 안내 시작

음성 입력은 에뮬레이터 Extended Controls → Microphone에서 "Virtual microphone uses host audio input"을 켜야 함.

### 소스에서 빌드

```bash
./gradlew :app:assembleRelease
```

`local.properties`(커밋 안 됨)에 아래 값을 넣으면 해당 기능이 켜지고, 없으면 기본값으로 동작한다.

```properties
pleos.clientId=...        # 있으면 STT/TTS HYBRID(서버 우선). 없으면 ON_DEVICE
pleos.clientSecret=...
papago.clientId=...       # 번역 API. 없으면 내장 택시 문장집 사용
papago.clientSecret=...
google.translateKey=...
```

제출 APK는 키 없이 빌드했다 (ON_DEVICE 음성 + 내장 문장집 번역).

## 동작 흐름

```
대기 ─(승객 도어 열림→닫힘)→ 외국인 승객인가요? ─[외국인]→ 국기 선택 → 인사
                                 └─[한국인]/15초 무응답→ 대기

통역: 승객 듣기 → 기사에게 한국어 재생 → 기사 듣기 → 승객 언어로 재생 → 반복
      (말하는 동안에는 마이크를 열지 않음)
목적지: 아는 장소 인식 → 목적지 카드 → 기사 "네"/[안내 시작] → NaviHelper 경로 → 승객에게 예상 시간 안내
        → 3초 뒤 통역 화면 복귀 (TBT는 드라이빙 뷰에 표시)
```

## 알려진 제약

- 음성 지원 언어: 영어, 스페인어, 프랑스어 (Pleos STT/TTS 지원 로케일). 중국어·일본어는 미지원
- 번역 API 키가 없으면 정해진 택시 표현(목적지, 요금, 카드 결제, 소요 시간 등)만 번역
- 목적지는 내장 목록(서울 주요 관광지 13곳)만 인식
- 경로 설정 후 앱 복귀는 에뮬레이터에서 확인. 실차 정책에 따라 막히면 하단 바 아이콘으로 복귀
