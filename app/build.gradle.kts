import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 번역 API 키 (local.properties, 커밋 X)
//   papago.clientId / papago.clientSecret
//   google.translateKey
// 없으면 문장집만 사용
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
