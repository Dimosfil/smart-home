package com.dimosfil.smarthome.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ShellyRpcCodecTest {
    @Test
    fun `parses authoritative switch output`() {
        assertTrue(ShellyRpcCodec.output("""{"id":0,"source":"init","output":true}"""))
        assertFalse(ShellyRpcCodec.output("""{"output": false,"apower":0.0}"""))
    }

    @Test
    fun `surfaces rpc error`() {
        try {
            ShellyRpcCodec.ensureNoRpcError(
                """{"error":{"code":-103,"message":"Invalid argument"}}""",
            )
            fail("RPC error must be reported")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("-103"))
            assertTrue(error.message.orEmpty().contains("Invalid argument"))
        }
    }
}
