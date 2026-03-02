package com.suaji.qiblabearing

import kotlin.math.*

object QiblaCalculator {

    //Kedudukan Kaabah di Mekah
    private const val KAABAH_LAT = 21.4225
    private const val KAABAH_LNG = 39.8262

    fun calculateBearing(userLat: Double, userLng: Double): Double {

        val lat1 = Math.toRadians(userLat)
        val lon1 = Math.toRadians(userLng)
        val lat2 = Math.toRadians(KAABAH_LAT)
        val lon2 = Math.toRadians(KAABAH_LNG)

        val dLon = lon2 - lon1

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) -
                sin(lat1) * cos(lat2) * cos(dLon)

        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360

        return bearing
    }
}