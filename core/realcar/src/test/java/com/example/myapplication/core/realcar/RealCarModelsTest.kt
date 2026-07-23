package com.example.myapplication.core.realcar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RealCarModelsTest {
    @Test
    fun `batch options reject unsafe values`() {
        assertFails { RealCarBatchOptions(timeoutMillis = 0) }
        assertFails { RealCarBatchOptions(maxRequestsPerChunk = 0) }
        assertFails { RealCarBatchOptions(maxConcurrentChunks = 9) }
    }

    @Test
    fun `batch result preserves partial failure counts`() {
        val error = RealCarPropertyException(2, 0, "expected")
        val result =
            RealCarBatchResult(
                items =
                    listOf(
                        RealCarBatchItem(
                            requestIndex = 0,
                            propertyId = 1,
                            areaId = 0,
                            result =
                                RealCarPropertyResult.Success(
                                    "ok",
                                    RealCarPropertyValueSource.REMOTE,
                                ),
                        ),
                        RealCarBatchItem(
                            requestIndex = 1,
                            propertyId = 2,
                            areaId = 0,
                            result = RealCarPropertyResult.Failure(error),
                        ),
                    ),
                elapsedRealtimeMillis = 4,
            )

        assertEquals(1, result.successCount)
        assertEquals(1, result.failureCount)
        assertFalse(result.isCompleteSuccess)
        assertEquals(1, result.failures().single().requestIndex)
    }

    @Test
    fun `result map keeps source and does not transform failure`() {
        val success =
            RealCarPropertyResult.Success(
                value = 21,
                source = RealCarPropertyValueSource.CALLBACK,
            )
        val mapped = success.map(Int::toString)

        assertEquals("21", mapped.getOrThrow())
        assertEquals(
            RealCarPropertyValueSource.CALLBACK,
            (mapped as RealCarPropertyResult.Success).source,
        )

        val error = RealCarPropertyException(1, 0, "failure")
        var transformed = false
        val failure =
            RealCarPropertyResult.Failure(error).map {
                transformed = true
                it
            }
        assertTrue(failure is RealCarPropertyResult.Failure)
        assertFalse(transformed)
    }

    @Test
    fun `callback handle is idempotent`() {
        var closeCount = 0
        val handle = RealCarPropertyCallbackHandle { closeCount++ }

        handle.close()
        handle.close()

        assertTrue(handle.isClosed)
        assertEquals(1, closeCount)
    }

    @Test
    fun `value adapters cover every supported shape and protect array snapshots`() {
        fun value(raw: Any) = RealCarPropertyValue.remote(1, 0, raw, 1)

        assertTrue(value(true).asBoolean())
        assertEquals(7, value(7).asInt())
        assertEquals(8L, value(8L).asLong())
        assertEquals(21.5f, value(21.5f).asFloat())
        assertEquals("vehicle", value("vehicle").asString())
        assertArrayEquals(intArrayOf(1, 2), value(arrayOf(1, 2)).asIntArray())
        assertArrayEquals(floatArrayOf(1.5f, 2.5f), value(arrayOf(1.5f, 2.5f)).asFloatArray(), 0f)
        assertArrayEquals(longArrayOf(3L, 4L), value(arrayOf(3L, 4L)).asLongArray())

        val sourceBytes = byteArrayOf(5, 6)
        val bytesValue = value(sourceBytes)
        sourceBytes[0] = 99
        val returnedBytes = bytesValue.asByteArray()
        assertArrayEquals(byteArrayOf(5, 6), returnedBytes)
        returnedBytes[1] = 99
        assertArrayEquals(byteArrayOf(5, 6), bytesValue.asByteArray())

        val mixed = value(arrayOf<Any?>(1, "two", byteArrayOf(3))).asMixed()
        assertEquals(1, mixed[0])
        assertEquals("two", mixed[1])
        assertArrayEquals(byteArrayOf(3), mixed[2] as ByteArray)
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }
}
