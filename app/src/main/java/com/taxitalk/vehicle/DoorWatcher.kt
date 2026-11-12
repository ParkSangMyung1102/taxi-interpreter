package com.taxitalk.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener

/**
 * 승객 도어(조수석, 뒷좌석 좌우)
 * "문이 열렸다가 모두 닫힘" = 승객이 탔다 고 판단해 알려 줍니다.
 */

class DoorWatcher(
    context: Context,
    private val onBoarded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val vehicle = Vehicle(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 지켜볼 승객 도어 (운전석 ROW_1_LEFT는 제외) */
    private val passengerDoors = listOf(
        DoorValue.ROW_1_RIGHT,
        DoorValue.ROW_2_LEFT,
        DoorValue.ROW_2_RIGHT,
    )

    /** 현재 열려 있는 도어 */
    private val openDoors = mutableSetOf<DoorValue>()

    /** 문이 한 번이라도 열렸는지 (닫힘만 보고 탑승으로 오해하지 않기 위해) */
    private var doorWasOpened = false

    private val listeners = mutableMapOf<DoorValue, DoorPositionListener>()

    fun start() {
        vehicle.initialize()
        val door = vehicle.getDoor()
        for (value in passengerDoors) {
            val listener = object : DoorPositionListener {
                // 0 = 닫힘, 0보다 크면 열림 (Sample App 기준 범위 0..1)
                override fun onPositionUpdated(pos: Int?) {
                    mainHandler.post { handlePosition(value, pos) }
                }

                override fun onFailed(e: Exception) {
                    Log.e(TAG, "door $value failed", e)
                    mainHandler.post { onError("도어 $value: ${e.message}") }
                }
            }
            listeners[value] = listener
            door.registerDoorPosition(DoorArea(value), listener)
        }
    }

    fun stop() {
        val door = vehicle.getDoor()
        listeners.forEach { (value, listener) ->
            door.unregisterDoorPosition(DoorArea(value), listener)
        }
        listeners.clear()
        vehicle.release()
    }

    private fun handlePosition(value: DoorValue, pos: Int?) {
        if (pos == null) return
        Log.d(TAG, "door $value position=$pos")
        if (pos > 0) {
            openDoors += value
            doorWasOpened = true
        } else {
            openDoors -= value
            if (doorWasOpened && openDoors.isEmpty()) {
                doorWasOpened = false
                onBoarded()
            }
        }
    }

    private companion object {
        const val TAG = "DoorWatcher"
    }
}
