package com.taxitalk.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.listener.DoorPositionListener

/** 승객 도어가 열렸다가 전부 닫히면 탑승으로 본다. */
class DoorWatcher(
    context: Context,
    private val onBoarded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val vehicle = Vehicle(context)
    private val handler = Handler(Looper.getMainLooper())

    private val watched = listOf(DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
    private val listeners = mutableMapOf<DoorValue, DoorPositionListener>()
    private val openDoors = mutableSetOf<DoorValue>()

    // 구독 직후 들어오는 "닫힘" 초기값을 탑승으로 착각하지 않도록
    private var openedSinceLastBoarding = false

    fun start() {
        vehicle.initialize()
        val door = vehicle.getDoor()
        watched.forEach { value ->
            val listener = object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) {
                    handler.post { onPosition(value, pos) }
                }

                override fun onFailed(e: Exception) {
                    Log.e(TAG, "door $value", e)
                    handler.post { onError("도어 $value: ${e.message}") }
                }
            }
            listeners[value] = listener
            door.registerDoorPosition(DoorArea(value), listener)
        }
    }

    fun stop() {
        val door = vehicle.getDoor()
        listeners.forEach { (value, l) -> door.unregisterDoorPosition(DoorArea(value), l) }
        listeners.clear()
        vehicle.release()
    }

    // pos: 0 닫힘, 1 열림
    private fun onPosition(value: DoorValue, pos: Int?) {
        pos ?: return
        Log.d(TAG, "$value -> $pos")
        if (pos > 0) {
            openDoors += value
            openedSinceLastBoarding = true
            return
        }
        openDoors -= value
        if (openedSinceLastBoarding && openDoors.isEmpty()) {
            openedSinceLastBoarding = false
            onBoarded()
        }
    }

    private companion object {
        const val TAG = "DoorWatcher"
    }
}
