import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 번역 API 키는 git에 올라가지 않는 local.properties에서 읽습니다.
//   papago.clientId=...       (네이버 클라우드 Papago Translation)
//   papago.clientSecret=...
//   google.translateKey=...   (Google Cloud Translation)
// 둘 다 없으면 앱은 내장 택시 문장집으로 번역합니다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String): String = localProps.getProperty(name, "").replace("\"", "")

android {
    namespace = "com.taxitalk"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.taxitalk"
        // Pleos 요구사항: 차량 보안(사이닝 키 로테이션) 때문에 minSdk 28 이상
        minSdk = 28
        // Pleos Connect 에뮬레이터는 Android 14(API 34)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "PAPAGO_CLIENT_ID", "\"${localProp("papago.clientId")}\"")
        buildConfigField("String", "PAPAGO_CLIENT_SECRET", "\"${localProp("papago.clientSecret")}\"")
        buildConfigField("String", "GOOGLE_TRANSLATE_KEY", "\"${localProp("google.translateKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Pleos Connect SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    // 화면(Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
