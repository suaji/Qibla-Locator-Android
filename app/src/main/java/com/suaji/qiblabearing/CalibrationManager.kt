package com.suaji.qiblabearing

import android.os.SystemClock
import kotlin.math.abs

class CalibrationManager(
    private val onStep: (Int) -> Unit,
    private val onComplete: () -> Unit
) {

    private var lastAngle = 0f
    private var count = 0
    private val target = 8
    private var lastStepTime: Long = 0L
    private var running: Boolean = false

    fun start() {
        count = 0
        lastAngle = 0f
        lastStepTime = 0L
        running = true
        onStep(count)
    }

    fun stop() {
        running = false
    }

    fun update(currentAngle: Float) {
        if (!running || isCompleted()) return

        val now = SystemClock.elapsedRealtime()

        // semak jarak darjah phone
        if (abs(currentAngle - lastAngle) > 45f && (now - lastStepTime) > 500L) {
            count++
            onStep(count)
            lastAngle = currentAngle
            lastStepTime = now

            if (count >= target) {
                running = false
                onComplete()
            }
        }
    }

    fun isCompleted(): Boolean {
        return count >= target
    }

    fun isRunning(): Boolean {
        return running
    }
}