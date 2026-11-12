package com.taxitalk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taxitalk.navi.Place

private val FinishRed = Color(0xFFE5484D)
private val PassengerBubble = Color(0xFF1E2A38)
private val DriverBubble = Color(0xFF26302A)
private val Listening = Color(0xFF4ADE80)
private val DestinationBg = Color(0xFF2A2440)

/**
 * 통역 화면.
 * - 가운데: 대화 기록 (승객 말은 왼쪽, 기사님 말은 오른쪽, 큰 글씨는 상대방이 읽을 번역)
 * - 아래: 지금 누구 차례인지 + 인식 중인 말
 * - 맨 아래 왼쪽(운전석 쪽): 크게 만든 운행 종료 버튼
 */
@Composable
fun TalkingScreen(vm: TaxiTalkViewModel, language: PassengerLanguage) {
    Column(Modifier.fillMaxSize()) {
        Header(language, onChangeLanguage = vm::changeLanguage)

        val listState = rememberLazyListState()
        LaunchedEffect(vm.chat.size) {
            if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(vm.chat) { line -> ChatBubble(line, language) }
        }

        vm.pendingPlace?.let { place ->
            PendingDestinationCard(place, onStart = vm::startNavigation, onDismiss = vm::dismissPlace)
            Spacer(Modifier.height(12.dp))
        }
        vm.activeRoute?.let { (place, minutes) ->
            ActiveRouteCard(place, minutes, onShowMap = vm::showMap)
            Spacer(Modifier.height(12.dp))
        }

        TurnStatus(vm.turn, vm.partialText, language)
        Spacer(Modifier.height(16.dp))
        BottomBar(vm)
    }
}

@Composable
private fun Header(language: PassengerLanguage, onChangeLanguage: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(language.flag, fontSize = 48.sp)
        Spacer(Modifier.size(12.dp))
        Text(
            "${language.koreanName} 승객",
            color = TextMain,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onChangeLanguage, modifier = Modifier.height(64.dp)) {
            Text("언어 변경", fontSize = 22.sp, color = TextSub)
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine, language: PassengerLanguage) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .align(if (line.fromPassenger) Alignment.CenterStart else Alignment.CenterEnd)
                .widthIn(max = 900.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (line.fromPassenger) PassengerBubble else DriverBubble)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            val who = if (line.fromPassenger) "${language.flag} 승객" else "🚕 기사님"
            Text("$who · ${line.original}", color = TextSub, fontSize = 20.sp)
            Text(
                line.translated ?: "(번역하지 못했어요)",
                color = if (line.translated != null) TextMain else TextSub,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 승객이 말한 목적지. 기사님이 "네"라고 하거나 [안내 시작]을 누르면 내비게이션에 설정됩니다. */
@Composable
private fun PendingDestinationCard(place: Place, onStart: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DestinationBg)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("📍 목적지 · \"네\"라고 하시면 안내를 시작해요", color = TextSub, fontSize = 20.sp)
            Text(
                "${place.koreanName}  (${place.englishName})",
                color = TextMain,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.height(80.dp)) {
            Text("아니요", fontSize = 24.sp, color = TextSub)
        }
        Spacer(Modifier.size(16.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(80.dp),
        ) {
            Text("안내 시작", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveRouteCard(place: Place, minutes: Int?, onShowMap: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "🧭 ${place.koreanName}(으)로 안내 중" + (minutes?.let { " · 약 ${it}분" } ?: ""),
            color = Listening,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onShowMap, modifier = Modifier.height(64.dp)) {
            Text("🗺 지도 보기", fontSize = 22.sp, color = TextMain)
        }
    }
}

@Composable
private fun TurnStatus(turn: Turn, partial: String, language: PassengerLanguage) {
    val (label, listening) = when (turn) {
        Turn.GREETING -> "인사말을 들려주는 중" to false
        Turn.PASSENGER_LISTENING -> "🎤 승객 말을 듣는 중 (${language.koreanName})" to true
        Turn.SPEAKING_TO_DRIVER -> "🔊 기사님께 한국어로 전달하는 중" to false
        Turn.DRIVER_LISTENING -> "🎤 기사님 말씀을 듣는 중 (한국어)" to true
        Turn.SPEAKING_TO_PASSENGER -> "🔊 승객에게 ${language.koreanName}로 전달하는 중" to false
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(label, color = if (listening) Listening else TextSub, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (partial.isNotBlank()) Text(partial, color = TextMain, fontSize = 22.sp)
    }
}

@Composable
private fun BottomBar(vm: TaxiTalkViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 운전석(왼쪽)에서 바로 누를 수 있도록 왼쪽 아래에 크게 둡니다.
        Button(
            onClick = vm::finishRide,
            colors = ButtonDefaults.buttonColors(containerColor = FinishRed),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .height(104.dp)
                .widthIn(min = 320.dp),
        ) {
            Text("운행 종료", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = vm::driverWantsToSpeak,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(104.dp),
        ) {
            Text("기사님 말하기", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        if (BuildConfig.DEBUG) DebugAudioButtons(vm)
    }
}

/** 개발용: 마이크 없이 녹음 파일로 통역 흐름을 확인하는 버튼 (디버그 빌드에만 표시) */
@Composable
private fun DebugAudioButtons(vm: TaxiTalkViewModel) {
    val context = LocalContext.current
    fun feed(file: String) = vm.feedTestAudio(context.assets.open(file).use { it.readBytes() })
    // 두 버튼의 터치 영역이 겹치지 않도록 가로로 충분히 띄워 둡니다.
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        OutlinedButton(onClick = { feed("test_passenger_en.pcm") }) {
            Text("테스트: 승객 음성", fontSize = 16.sp, color = TextSub)
        }
        OutlinedButton(onClick = { feed("test_driver_ko.pcm") }) {
            Text("테스트: 기사님 음성", fontSize = 16.sp, color = TextSub)
        }
    }
}
