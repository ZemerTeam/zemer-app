package com.jtech.zemer.payments

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** A PaymentIntent created by pay.zemer.io, ready to confirm with the card. */
class DonationIntent(val clientSecret: String, val publishableKey: String)

/** The server refused or failed; [message] is its text for the donor, null when it sent none. */
class DonationException(message: String?) : Exception(message)

/**
 * One-time donations, paid natively with Stripe's card field so no browser ever opens.
 * pay.zemer.io (`POST /app/v1/donation`) creates a card-only PaymentIntent; the app never sees a
 * secret key. The server re-checks the amount, so [MIN_CENTS]/[MAX_CENTS] only gate the button.
 */
object DonationClient {
    private const val ENDPOINT = "https://pay.zemer.io/app/v1/donation"
    const val MIN_CENTS = 100
    const val MAX_CENTS = 1_000_000
    val PRESET_DOLLARS = listOf(5, 18, 36, 100)

    private val AMOUNT = Regex("""\d{1,9}(\.\d{1,9})?""")
    private val DECIMAL_COMMA = Regex("""\d{1,9},\d{1,2}""")
    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 20_000 } }
    }

    suspend fun create(cents: Int, email: String): DonationIntent {
        val body = buildJsonObject {
            put("amount", amountParam(cents))
            email.trim().takeIf { it.isNotEmpty() }?.let { put("email", it) }
        }
        val response = client.post(ENDPOINT) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return parseResponse(response.status.value, response.bodyAsText())
    }

    /** Whole cents for a typed dollar amount ("18", "$7.50", "7,50"), or null when it isn't a valid donation. */
    fun cents(text: String): Int? {
        // Plain digits only: BigDecimal would also take "1e999999999", which overflows or stalls the UI thread.
        // A comma is a decimal point only before 1-2 digits ("7,50"); "1,000" is rejected, never $1.00.
        val plain = text.trim().removePrefix("$").let { if (DECIMAL_COMMA.matches(it)) it.replace(',', '.') else it }
        if (!AMOUNT.matches(plain)) return null
        val dollars = plain.toBigDecimal()
        val cents = dollars.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return cents.takeIf { it >= BigDecimal(MIN_CENTS) && it <= BigDecimal(MAX_CENTS) }?.toInt()
    }

    /** The server's `amount` field: dollars with two decimals, never locale-formatted. */
    internal fun amountParam(cents: Int): String = String.format(Locale.US, "%d.%02d", cents / 100, cents % 100)

    internal fun parseResponse(status: Int, body: String): DonationIntent {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
        // contentOrNull: a JSON null stays null instead of becoming the text "null".
        fun field(name: String) = (obj?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (status !in 200..299) throw DonationException(field("error"))
        val clientSecret = field("client_secret")
        val publishableKey = field("publishable_key")
        if (clientSecret == null || publishableKey == null) throw DonationException(null)
        return DonationIntent(clientSecret, publishableKey)
    }
}
