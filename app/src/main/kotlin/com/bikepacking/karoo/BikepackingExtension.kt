package com.bikepacking.karoo

import com.bikepacking.karoo.datatypes.BpDyn3x2DataType
import com.bikepacking.karoo.datatypes.BpDyn3x2MsgDataType
import com.bikepacking.karoo.datatypes.BpStatsDataType
import com.bikepacking.karoo.field.BpLive3x2DataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension

class BikepackingExtension : KarooExtension("bikepacking-eta", "0.1.0") {

    private val settings by lazy { AppSettings(this) }
    private val karooSystem by lazy { KarooSystemService(this) }
    private val rideEngine by lazy { RideEngine(karooSystem, settings, this) }

    override val types by lazy {
        val typeList = listOf(
            BpLive3x2DataType(rideEngine = rideEngine, extension = extension),
            BpDyn3x2DataType(rideEngine = rideEngine, extension = extension),
            BpDyn3x2MsgDataType(rideEngine = rideEngine, extension = extension),
            BpStatsDataType(rideEngine = rideEngine, extension = extension),
        )
        android.util.Log.d("QBOT_TYPES", "registered types=${typeList.map { it.typeId }}")
        typeList
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