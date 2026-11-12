package com.taxitalk.navi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/**
 * 차량 기본 내비게이션에 목적지를 넘겨주는 도우미 (Pleos NaviHelper SDK).
 *
 * 별도 지도 앱을 만들지 않고, 차량에 이미 있는 내비게이션으로 안내를 시작합니다.
 * 경로 탐색이 시작되면 예상 소요 시간(분)을 알려 줍니다.
 */
class Navigator(
    context: Context,
    private val onRouteStarted: (place: Place, minutes: Int?) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val navi = NaviHelper(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connected = false

    /** 마지막으로 요청한 목적지 (경로 시작 이벤트와 짝을 맞추기 위해) */
    private var requested: Place? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.d(TAG, "route started $routeStartInfo")
            mainHandler.post {
                val place = requested ?: return@post
                // duration 단위는 초로 보고 분으로 바꿉니다. (값이 없으면 null)
                val minutes = routeStartInfo.duration.takeIf { it > 0 }?.let { (it + 59) / 60 }
                onRouteStarted(place, minutes)
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.e(TAG, "navi error $errorCode")
            mainHandler.post { onError("내비게이션 오류: $errorCode") }
        }
    }

    fun start() {
        navi.initialize(
            onConnected = {
                mainHandler.post {
                    navi.addListener(listener)
                    connected = true
                }
            },
            onFailed = { e ->
                Log.e(TAG, "navi connect failed", e)
                mainHandler.post { onError("내비게이션 연결 실패: ${e.message}") }
            },
        )
    }

    /** 이 장소로 안내 시작 */
    fun go(place: Place) {
        if (!connected) {
            onError("내비게이션이 아직 연결되지 않았어요")
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

    fun cancel() {
        requested = null
        if (connected) navi.cancelRoute()
    }

    fun release() {
        if (connected) navi.removeListener(listener)
        navi.release()
    }

    private companion object {
        const val TAG = "Navigator"
    }
}
