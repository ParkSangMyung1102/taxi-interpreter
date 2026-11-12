package com.taxigo.navi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/** 순정 내비에 목적지 전달 (NaviHelper). 경로가 잡히면 예상 소요시간(분)을 넘겨준다. */
class Navigator(
    context: Context,
    private val onRouteStarted: (place: Place, minutes: Int?) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val navi = NaviHelper(context)
    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private var requested: Place? = null

    private val events = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "route started $routeStartInfo")
            handler.post {
                val place = requested ?: return@post
                // duration은 초 단위 (에뮬레이터: 4020 -> 내비 화면 1시간 7분)
                val minutes = routeStartInfo.duration.takeIf { it > 0 }?.let { (it + 59) / 60 }
                onRouteStarted(place, minutes)
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.e(TAG, "navi $errorCode")
            handler.post { onError("내비게이션 오류: $errorCode") }
        }
    }

    fun start() {
        navi.initialize(
            onConnected = {
                handler.post {
                    navi.addListener(events)
                    connected = true
                }
            },
            onFailed = { e ->
                Log.e(TAG, "navi connect", e)
                handler.post { onError("내비게이션 연결 실패: ${e.message}") }
            },
        )
    }

    fun go(place: Place) {
        if (!connected) {
            onError("내비게이션 연결 전")
            return
        }
        requested = place
        navi.requestRoute(
            RouteInfo(
                latitude = place.latitude,
                longitude = place.longitude,
                displayLongitude = null,
                displayLatitude = null,
                poiName = place.koreanName,
                poiId = "",
                poiSubId = "0",
                address = place.address,
            )
        )
    }

    fun showMap() {
        if (connected) navi.requestNavigationScreen(NavigationScreenRequest.Foreground)
    }

    fun release() {
        if (connected) navi.removeListener(events)
        navi.release()
    }

    private companion object {
        const val TAG = "Navigator"
    }
}
