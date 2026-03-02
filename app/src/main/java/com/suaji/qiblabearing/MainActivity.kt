package com.suaji.qiblabearing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.*
import android.view.View
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.suaji.qiblabearing.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var compassManager: CompassManager
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var calibrationManager: CalibrationManager

    private var qiblaBearing: Double = 0.0
    private var hasQiblaBearing = false
    private var currentAzimuth = 0f
    private var manualOffset = 0f
    private var isCalibrated = false
    private var isQiblaAligned = false
    private var isPulseActive = false
    private var countryName: String = "-"
    private var locationLabel: String = "-"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        fusedLocation =
            LocationServices.getFusedLocationProviderClient(this)

        requestPermission()

        calibrationManager = CalibrationManager(
            onStep = {
                vibrateShort()
                binding.txtDegree.text = "Calibration: $it / 8"
            },
            onComplete = {
                vibrateShort()
                isCalibrated = true
                Toast.makeText(this, "Calibration Complete", Toast.LENGTH_SHORT).show()
            }
        )

        // Mulakan kalibrasi pertama secara automatik apabila aplikasi dibuka
        isCalibrated = false
        calibrationManager.start()

        compassManager = CompassManager(
            this,
            onAzimuthChanged = { azimuth ->
                currentAzimuth = azimuth

                if (!isCalibrated) {
                    calibrationManager.update(azimuth)
                } else {
                    updateCompass()
                }
            },
            onLowAccuracy = { low ->
                if (low) {
                    binding.txtDegree.text = "Magnetic interference detected"
                }
            }
        )

        if (!compassManager.isSensorAvailable) {
            Toast.makeText(
                this,
                "This device does not support the compass sensor (rotation vector).",
                Toast.LENGTH_LONG
            ).show()
            binding.txtDegree.text = "Compass functionality is not supported on this device"
        }

        setupOffsetControls()

        binding.btnPlus.setOnClickListener {
            manualOffset += 1f
            clampOffset()
            syncOffsetUiAndPersist()
        }

        binding.btnMinus.setOnClickListener {
            manualOffset -= 1f
            clampOffset()
            syncOffsetUiAndPersist()
        }

        loadOffset()
        syncOffsetUi()

        showSensorDisclaimerDialog()

        binding.imgCalibrate.setOnClickListener {
            isCalibrated = false
            calibrationManager.start()
            Toast.makeText(
                this,
                "Move your phone in a figure-8 motion until calibration reaches 8/8.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        compassManager.start()
        getLocation()
    }

    override fun onPause() {
        super.onPause()
        compassManager.stop()
    }

    private fun showSensorDisclaimerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sensor Notice")
            .setMessage(
                        "1. Accuracy depends on your device's hardware sensors.\n\n" +
                        "2. Keep away from metal objects or magnetic phone cases to avoid interference.\n\n" +
                        "3. If inaccurate, please perform the 'figure-8' calibration.\n\n" +
                        "4. Please enable GPS and Internet for better accuracy."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            100
        )
    }

    private fun getLocation() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        binding.txtDegree.text = "Locate Qibla position..."

        fusedLocation
            .getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude

                    qiblaBearing = QiblaCalculator.calculateBearing(lat, lng)
                    hasQiblaBearing = true

                    updateLocationLabels(lat, lng)

                    binding.txtDegree.text = buildString {
                        append("Location: ")
                        append(locationLabel)
                        append('\n')
                        append('\n')
                        append("Country: ")
                        append(countryName)
                        append('\n')
                        append("Qibla: ${qiblaBearing.toInt()}°  Offset: ${manualOffset.toInt()}°")
                    }
                } else {
                    hasQiblaBearing = false
                    binding.txtDegree.text = "Unable to locate your position"
                }
            }
            .addOnFailureListener {
                hasQiblaBearing = false
                binding.txtDegree.text = "Error retrieving location"
            }
    }

    private fun updateCompass() {

        if (!hasQiblaBearing) return

        val direction =
            (qiblaBearing - currentAzimuth + manualOffset + 360) % 360

        // Background bearing compass berpusing
        val compassRotation = -currentAzimuth
        binding.compassBackground.animate()
            .rotation(compassRotation)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Jarum panah Kaabah berpusing
        binding.arrow.animate()
            .rotation(direction.toFloat())
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val diffToQibla = kotlin.math.min(direction, 360 - direction)
        val nowAligned = diffToQibla <= 3f

        if (nowAligned && !isQiblaAligned) {
            vibrateShort()
        }
        isQiblaAligned = nowAligned

        updateAlignedVisualState(nowAligned)

        binding.txtDegree.text = buildString {
            append("Location: ")
            append(locationLabel)
            append('\n')
            append('\n')
            append("Country: ")
            append(countryName)
            append('\n')
            if (nowAligned) {
                append("Qibla direction is accurate: ${qiblaBearing.toInt()}°  Offset: ${manualOffset.toInt()}°")
            } else {
                append("Qibla: ${qiblaBearing.toInt()}°  Offset: ${manualOffset.toInt()}°")
            }
        }
    }

    private fun updateAlignedVisualState(aligned: Boolean) {
        if (aligned) {
            if (!isPulseActive) {
                val pulse = ScaleAnimation(
                    1f, 1.12f,
                    1f, 1.12f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 600
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                }
                binding.arrow.startAnimation(pulse)
                isPulseActive = true
            }
        } else {
            if (isPulseActive) {
                binding.arrow.clearAnimation()
                binding.arrow.scaleX = 1f
                binding.arrow.scaleY = 1f
                isPulseActive = false
            }
        }
    }

    private fun updateLocationLabels(lat: Double, lng: Double) {
        try {
            locationLabel = "Lat: ${"%.5f".format(lat)}, Lng: ${"%.5f".format(lng)}"
            val geocoder = Geocoder(this, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lng, 1)
            if (!results.isNullOrEmpty()) {
                val address = results[0]
                countryName = address.countryName ?: "-"
                locationLabel = buildLocationLabel(address).ifBlank { locationLabel }
            }
        } catch (_: Exception) {
            countryName = "-"
            locationLabel = "-"
        }
    }

    private fun buildLocationLabel(address: android.location.Address): String {
        val parts = listOfNotNull(
            address.thoroughfare,
            address.subLocality,
            address.locality,
            address.adminArea
        )
        return when {
            parts.isNotEmpty() -> parts.joinToString(", ")
            !address.featureName.isNullOrBlank() -> address.featureName
            else -> ""
        }
    }

    private fun saveOffset() {
        val prefs = getSharedPreferences("qibla", Context.MODE_PRIVATE)
        prefs.edit().putFloat("offset", manualOffset).apply()
    }

    private fun loadOffset() {
        val prefs = getSharedPreferences("qibla", Context.MODE_PRIVATE)
        manualOffset = prefs.getFloat("offset", 0f)
    }

    private fun clampOffset() {
        if (manualOffset > 30f) manualOffset = 30f
        if (manualOffset < -30f) manualOffset = -30f
    }

    private fun setupOffsetControls() {
        binding.seekOffset.max = 60 // -30..+30

        binding.seekOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                manualOffset = progress - 30f
                syncOffsetUiAndPersist()
                if (isCalibrated) {
                    updateCompass()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun syncOffsetUi() {
        val progress = (manualOffset + 30f).toInt().coerceIn(0, 60)
        if (binding.seekOffset.progress != progress) {
            binding.seekOffset.progress = progress
        }
        binding.txtOffsetLabel.text = "Offset: ${manualOffset.toInt()}°"
    }

    private fun syncOffsetUiAndPersist() {
        syncOffsetUi()
        saveOffset()
    }

    private fun vibrateShort() {
        val vibrator =
            getSystemService(VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    50,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            vibrator.vibrate(50)
        }
    }
}