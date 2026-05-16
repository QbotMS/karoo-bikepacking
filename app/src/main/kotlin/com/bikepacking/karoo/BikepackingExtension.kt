package com.bikepacking.karoo

import com.bikepacking.karoo.datatypes.BpEtaDataType
import com.bikepacking.karoo.datatypes.BpLiveDataType
import com.bikepacking.karoo.datatypes.BpStatsDataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

class BikepackingExtension : KarooExtension("bikepacking-eta", "0.1.0") {

    private val settings by lazy { AppSettings(this) }
    private val karooSystem by lazy { KarooSystemService(this) }
    private val rideEngine by lazy { RideEngine(karooSystem, settings, this) }

    override val types by lazy {
        listOf(
            BpLiveDataType(rideEngine = rideEngine, extension = extension),
            BpEtaDataType(rideEngine = rideEngine, extension = extension),
            BpStatsDataType(rideEngine = rideEngine, extension = extension)
        )
    }

    override fun onCreate() {
        super.onCreate()
        try { karooSystem.connect { rideEngine.start() } } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { rideEngine.stop(); karooSystem.disconnect() } catch (_: Exception) {}
        super.onDestroy()
    }
}