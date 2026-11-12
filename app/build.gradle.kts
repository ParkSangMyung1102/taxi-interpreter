import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// local.properties (커밋 X)
//   pleos.clientId / pleos.clientSecret   Playground My Project. 없으면 STT/TTS 온디바이스만
//   papago.clientId / papago.clientSecret
//   google.translateKey                   번역 키 없으면 문장집만
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String): String = localProps.getProperty(name, "").replace("\"", "")

android {
    namespace = "com.taxigo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.taxigo"
        minSdk = 28 // Pleos 요구사항 (서명 키 로테이션)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProp("pleos.clientId")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProp("pleos.clientSecret")}\"")
        buildConfigField("String", "PAPAGO_CLIENT_ID", "\"${localProp("papago.clientId")}\"")
        buildConfigField("String", "PAPAGO_CLIENT_SECRET", "\"${localProp("papago.clientSecret")}\"")
        buildConfigField("String", "GOOGLE_TRANSLATE_KEY", "\"${localProp("google.translateKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 공모전 제출용: 별도 키스토어 없이 바로 설치되도록 debug 키로 서명. 앱마켓 배포 땐 교체.
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")

    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
