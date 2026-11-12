package com.taxitalk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 차량 화면은 어두운 배경 + 큰 글씨가 기본입니다.
private val Background = Color(0xFF101418)
private val Card = Color(0xFF1E252C)
private val TextMain = Color(0xFFF2F4F6)
private val TextSub = Color(0xFF9AA4AE)
private val Accent = Color(0xFF3D8BFD)

@Composable
fun TaxiTalkScreen(vm: TaxiTalkViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp)
    ) {
        when (val s = vm.screen) {
            Screen.Waiting -> WaitingScreen(onSimulateBoarding = vm::onPassengerBoarded)
            Screen.ChooseLanguage -> LanguageScreen(onChoose = vm::chooseLanguage)
            is Screen.Talking -> TalkingScreen(
                language = s.language,
                onChangeLanguage = vm::changeLanguage,
                onFinish = vm::finishRide,
            )
        }
        vm.lastError?.let {
            Text(
                it,
                color = Color(0xFFFF8A80),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun WaitingScreen(onSimulateBoarding: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("택시톡", color = TextMain, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("승객을 기다리는 중입니다", color = TextSub, fontSize = 28.sp)
        Text(
            "승객 도어가 열렸다 닫히면 언어 선택 화면이 열립니다",
            color = TextSub,
            fontSize = 20.sp,
        )
        Spacer(Modifier.height(48.dp))
        // 개발용: 버추얼 콘솔 없이도 흐름을 확인하는 버튼
        OutlinedButton(onClick = onSimulateBoarding) {
            Text("탑승 테스트", fontSize = 18.sp, color = TextSub)
        }
    }
}

@Composable
private fun LanguageScreen(onChoose: (PassengerLanguage) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Choose your language · Elija su idioma · Choisissez votre langue",
            color = TextMain,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "승객이 가리키는 국기를 눌러 주세요",
            color = TextSub,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PassengerLanguage.entries.forEach { lang ->
                FlagCard(lang, Modifier.weight(1f), onClick = { onChoose(lang) })
            }
        }
    }
}

/** 운전석에서 한 번에 누를 수 있도록 크게 만든 국기 버튼 */
@Composable
private fun FlagCard(lang: PassengerLanguage, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(lang.flag, fontSize = 120.sp)
        Spacer(Modifier.height(16.dp))
        Text(lang.nativeName, color = TextMain, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(lang.koreanName, color = TextSub, fontSize = 24.sp)
    }
}

@Composable
private fun TalkingScreen(
    language: PassengerLanguage,
    onChangeLanguage: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(language.flag, fontSize = 48.sp)
            Spacer(Modifier.padding(8.dp))
            Text(
                "${language.koreanName} 승객",
                color = TextMain,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onChangeLanguage) {
                Text("언어 변경", fontSize = 20.sp, color = TextSub)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                language.greeting,
                color = TextMain,
                fontSize = 40.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text("통역 기능은 다음 단계에서 연결됩니다", color = TextSub, fontSize = 22.sp)
        }
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("운행 종료", fontSize = 22.sp)
        }
    }
}
