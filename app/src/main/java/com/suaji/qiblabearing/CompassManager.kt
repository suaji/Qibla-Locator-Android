package com.suaji.qiblabearing

import android.content.Context
import android.hardware.*
import kotlin.math.abs

class CompassManager(
    context: Context,
    private val onAzimuthChanged: (Float) -> Unit,
    private val onLowAccuracy: (Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isSensorAvailable: Boolean
        get() = rotationSensor != null

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var lastAzimuth = 0f
    private val alpha = 0.1f // smoothing factor

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        SensorManager.getRotationMatrixFromVector(
            rotationMatrix,
            event.values
        )

        SensorManager.getOrientation(rotationMatrix, orientation)

        var azimuth = Math.toDegrees(
            orientation[0].toDouble()
        ).toFloat()

        azimuth = (azimuth + 360) % 360

        val smooth = lastAzimuth + alpha * (azimuth - lastAzimuth)
        lastAzimuth = smooth

        onAzimuthChanged(smooth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            onLowAccuracy(accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
        }
    }
}