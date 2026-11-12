package com.taxigo.voice

import com.taxigo.BuildConfig

/** Playground My Project 자격 증명. 있으면 STT/TTS를 HYBRID(서버 우선, 끊기면 온디바이스)로 씀. */
data class PleosCredentials(val clientId: String, val clientSecret: String) {
    companion object {
        fun fromBuildConfig(): PleosCredentials? =
            PleosCredentials(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET)
                .takeIf { it.clientId.isNotBlank() && it.clientSecret.isNotBlank() }

        // registerApp 응답이 안 오면 이 시간 뒤에 그냥 진행 (온디바이스로라도 동작하게)
        const val REGISTER_TIMEOUT_MS = 5_000L
    }
}
