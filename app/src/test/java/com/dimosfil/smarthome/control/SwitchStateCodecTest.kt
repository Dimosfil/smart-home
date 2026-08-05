package com.dimosfil.smarthome.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchStateCodecTest {
    @Test
    fun parsesPlainAndJsonOnStates() {
        assertTrue(SwitchStateCodec.parse("on"))
        assertTrue(SwitchStateCodec.parse("{\"power\": true}"))
    }

    @Test
    fun parsesPlainAndJsonOffStates() {
        assertFalse(SwitchStateCodec.parse("0"))
        assertFalse(SwitchStateCodec.parse("{\"enabled\": false}"))
    }
}
