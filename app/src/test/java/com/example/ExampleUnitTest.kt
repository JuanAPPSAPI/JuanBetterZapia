package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

class ExampleUnitTest {

    @Test
    fun eventIdGeneration_isDeterministic() {
        val timeWindow = 1700000000000L / 5000L
        val raw = "com.whatsapp|Ana|Hola|$timeWindow"
        val bytes1 = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hash1 = bytes1.joinToString("") { "%02x".format(it) }

        val bytes2 = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hash2 = bytes2.joinToString("") { "%02x".format(it) }

        assertEquals(hash1, hash2)

        val rawDifferent = "com.whatsapp|Ana|Otro Mensaje|$timeWindow"
        val bytesDiff = MessageDigest.getInstance("SHA-256").digest(rawDifferent.toByteArray(Charsets.UTF_8))
        val hashDiff = bytesDiff.joinToString("") { "%02x".format(it) }
        assertNotEquals(hash1, hashDiff)
    }
}
