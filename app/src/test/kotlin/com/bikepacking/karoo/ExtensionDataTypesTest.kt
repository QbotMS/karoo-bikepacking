package com.bikepacking.karoo

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ExtensionDataTypesTest {

    @Test
    fun extension_has_exactly_4_datatypes() {
        val registeredTypes = registeredDataTypes().keys
        assertEquals(4, registeredTypes.size)
    }

    @Test
    fun extension_contains_live_dyn_msg_stats() {
        val types = registeredDataTypes().keys
        
        assertTrue(types.contains("BP_LIVE3X2"))
        assertTrue(types.contains("BP_DYN3X2"))
        assertTrue(types.contains("BP_DYN3X2MSG"))
        assertTrue(types.contains("BP_STATS"))
    }

    @Test
    fun extension_display_names_are_final_field_names() {
        val types = registeredDataTypes()

        assertEquals("LIVE", types["BP_LIVE3X2"])
        assertEquals("DYN", types["BP_DYN3X2"])
        assertEquals("DYNMSG", types["BP_DYN3X2MSG"])
        assertEquals("STATS", types["BP_STATS"])
    }

    @Test
    fun extension_does_not_contain_old_types() {
        val oldTypes = listOf("BP_LIVE", "BP_LIVEUP", "BP_LIVEDOWN", "BP_ETA", "BP_STATS_VISUAL_TEST", "BP_MSG_TEST")
        val currentTypes = registeredDataTypes().keys
        
        for (old in oldTypes) {
            assertFalse("Old type $old should not be in current types", currentTypes.contains(old))
        }
    }

    @Test
    fun dyn_message_severity_values() {
        val severity = RideEngine.DynMessageSeverity.INFO
        assertEquals(RideEngine.DynMessageSeverity.INFO, severity)
        assertEquals(RideEngine.DynMessageSeverity.ALERT, RideEngine.DynMessageSeverity.ALERT)
    }

    @Test
    fun dyn_message_stub_returns_null() {
        val source = projectFile("src/main/kotlin/com/bikepacking/karoo/RideEngine.kt").readText()
        assertTrue(source.contains("fun getDynMessage(): DynMessage? = null"))
    }

    private fun registeredDataTypes(): Map<String, String> {
        val file = projectFile("src/main/res/xml/extension_info.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("DataType")
        return (0 until nodes.length).associate { index ->
            val attrs = nodes.item(index).attributes
            attrs.getNamedItem("typeId").nodeValue to attrs.getNamedItem("displayName").nodeValue
        }
    }

    private fun projectFile(pathFromApp: String): File {
        return listOf(
            File(pathFromApp),
            File("app/$pathFromApp"),
        ).first { it.exists() }
    }
}
