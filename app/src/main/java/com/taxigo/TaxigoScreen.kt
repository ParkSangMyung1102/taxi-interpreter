package com.taxigo

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

internal val Background = Color(0xFF101418)
internal val Card = Color(0xFF1E252C)
internal val TextMain = Color(0xFFF2F4F6)
internal val TextSub = Color(0xFF9AA4AE)
internal val Accent = Color(0xFF3D8BFD)

@Composable
fun TaxigoScreen(vm: TaxigoViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp)
    ) {
        when (val s = vm.screen) {
            Screen.Waiting -> WaitingScreen(onSimulateBoarding = vm::onPassengerBoarded)
            Screen.AskForeigner -> AskForeignerScreen(
                onKorean = vm::koreanPassenger,
                onForeign = vm::foreignPassenger,
            )
            Screen.ChooseLanguage -> LanguageScreen(onChoose = vm::chooseLanguage, onCancel = vm::finishRide)
            is Screen.Talking -> TalkingScreen(vm, s.language)
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
        Text("승객을 기다리는 중입니다", color = TextMain, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("승객이 타면 외국인 승객인지 물어봅니다", color = TextSub, fontSize = 20.sp)
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(48.dp))
            OutlinedButton(onClick = onSimulateBoarding) {
                Text("탑승 테스트", fontSize = 18.sp, color = TextSub)
            }
        }
    }
}

// 자주 누르는 쪽(한국인)을 운전석 쪽 왼쪽에
@Composable
private fun AskForeignerScreen(onKorean: () -> Unit, onForeign: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "외국인 승객인가요?",
            color = TextMain,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "누르지 않으면 15초 뒤 한국인 승객으로 처리됩니다",
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
            ChoiceCard("🇰🇷", "한국인 승객", "통역 없이 운행", Modifier.weight(1f), onKorean)
            ChoiceCard("🌏", "외국인 승객", "언어 선택 후 통역 시작", Modifier.weight(1f), onForeign, highlight = true)
        }
    }
}

@Composable
private fun ChoiceCard(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(if (highlight) Accent else Card)
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 110.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextMain, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = if (highlight) TextMain else TextSub, fontSize = 24.sp)
    }
}

@Composable
private fun LanguageScreen(onChoose: (PassengerLanguage) -> Unit, onCancel: () -> Unit) {
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
        // 외국인 승객 잘못 눌렀을 때
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .padding(top = 24.dp)
                .height(80.dp),
        ) {
            Text("🇰🇷 한국인 승객이에요", fontSize = 26.sp, color = TextSub)
        }
    }
}

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

