package com.jtech.zemer.payments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DonationClientTest {

    @Test
    fun `cents parses dollar amounts within the server's limits`() {
        assertEquals(1800, DonationClient.cents("18"))
        assertEquals(750, DonationClient.cents(" \$7.50 "))
        assertEquals(100, DonationClient.cents("1"))
        assertEquals(1_000_000, DonationClient.cents("10000"))
        assertEquals(1235, DonationClient.cents("12.345"))
        assertEquals(150, DonationClient.cents("1,5"))
        assertEquals(750, DonationClient.cents("7,50"))
    }

    @Test
    fun `cents rejects junk and out-of-range amounts`() {
        listOf("", "abc", "0", "0.99", "-5", "10000.01", "1e9", "1e2147483647", "1.2.3", "1,5,0", ".5", "1234567890", "1,000", "2,500", "1,500.00").forEach {
            assertNull("\"$it\"", DonationClient.cents(it))
        }
    }

    @Test
    fun `amountParam is plain dollars whatever the locale`() {
        assertEquals("18.00", DonationClient.amountParam(1800))
        assertEquals("7.05", DonationClient.amountParam(705))
        assertEquals("10000.00", DonationClient.amountParam(1_000_000))
    }

    @Test
    fun `parseResponse returns the intent on success`() {
        val intent = DonationClient.parseResponse(200, """{"client_secret":"pi_1_secret_2","publishable_key":"pk_live_x"}""")
        assertEquals("pi_1_secret_2", intent.clientSecret)
        assertEquals("pk_live_x", intent.publishableKey)
    }

    @Test
    fun `parseResponse surfaces the server's message on failure`() {
        assertEquals("Donations must be between \$1 and \$10000.", failure(400, """{"error":"Donations must be between ${'$'}1 and ${'$'}10000."}"""))
        assertNull(failure(500, "<html>oops</html>"))
        assertNull(failure(200, """{"client_secret":"pi_1_secret_2"}"""))
        assertNull(failure(200, "not json"))
        assertNull(failure(500, """{"error":null}"""))
        assertNull(failure(200, """{"client_secret":null,"publishable_key":"pk_live_x"}"""))
    }

    @Test
    fun `parseConfig reads the publishable key, and fails on anything else`() {
        assertEquals("pk_live_x", DonationClient.parseConfig(200, """{"publishable_key":"pk_live_x"}"""))
        listOf(200 to "{}", 200 to """{"publishable_key":null}""", 200 to "not json", 503 to "<html>down</html>").forEach { (s, b) ->
            try {
                DonationClient.parseConfig(s, b)
                fail("expected DonationException for $s $b")
            } catch (_: DonationException) {
            }
        }
    }

    private fun failure(status: Int, body: String): String? = try {
        DonationClient.parseResponse(status, body)
        fail("expected DonationException")
        null
    } catch (e: DonationException) {
        e.message
    }
}
