package com.jtech.zemer.sync.models

import com.jtech.zemer.sync.ContentServerApi
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Pins the `/prefs` wire shape: the same fields and null semantics the Firestore mapping had, dates as
 * UTC ISO-8601 (written with millis, read with any fraction length).
 */
class DevicePreferencesJsonTest {
    private val json = ContentServerApi.json

    private fun decode(text: String) = json.decodeFromString(DevicePreferencesEntity.serializer(), text)
    private fun encode(doc: DevicePreferencesEntity) =
        json.parseToJsonElement(json.encodeToString(DevicePreferencesEntity.serializer(), doc)).jsonObject

    // Shape of a doc imported from Firestore: blockPodcasts absent, the retired femalePasscodeHash present
    // (ignored like any unknown field), 0- and 6-digit fractions, an unknown field.
    private val imported = """
        {"userId":"uid1","userEmail":"uid1",
         "contentFilters":{"enableContentFilters":true,"allowFemaleSingers":false,"blockVideos":true,"femalePasscodeHash":null},
         "deviceInfo":{"deviceName":"Pixel","manufacturer":"Google","model":"Pixel 7","androidVersion":"14","sdkVersion":34,"appVersion":"1.0","firstSeen":null,"lastSeen":"2026-09-01T00:00:00Z"},
         "devices":[{"deviceId":"dev-1","deviceInfo":{"deviceName":"Pixel","sdkVersion":34,"lastSeen":"2026-10-26T16:12:50.253033Z"},
                     "contentFilters":{"enableContentFilters":true,"allowFemaleSingers":true,"blockVideos":false,"blockPodcasts":false,"femalePasscodeHash":"abc"},
                     "createdAt":"2026-10-26T16:12:50.253Z","lastSyncTime":1793031170253}],
         "createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-10-26T16:12:50.253033Z","someNewField":1}
    """.trimIndent()

    @Test
    fun `imported doc decodes with Firestore-identical semantics`() {
        val doc = decode(imported)
        assertEquals("uid1", doc.userId)
        assertEquals("uid1", doc.userEmail)
        // absent blockPodcasts stays null and derives from blockVideos, exactly like the Firestore path
        assertNull(doc.contentFilters.blockPodcasts)
        assertTrue(doc.contentFilters.toConfig().blockPodcasts)
        assertNull(doc.deviceInfo.firstSeen)
        assertEquals(Date(1_788_220_800_000L), doc.deviceInfo.lastSeen)
        assertEquals(Date(1_793_031_170_253L), doc.updatedAt) // 6-digit fraction, truncated to millis
        val device = doc.devices.single()
        assertEquals("dev-1", device.deviceId)
        assertEquals(false, device.contentFilters.blockPodcasts)
        assertEquals(true, device.contentFilters.allowFemaleSingers)
        assertEquals(1_793_031_170_253L, device.lastSyncTime)
        assertEquals(Date(1_793_031_170_253L), device.createdAt)
        assertEquals("", device.deviceInfo.model) // absent string -> the entity default, like toObject()
    }

    @Test
    fun `encode sends every field, nulls explicit, dates as millis ISO with Z`() {
        val doc = DevicePreferencesEntity(
            userId = "uid1",
            userEmail = "a@b.c",
            contentFilters = DeviceContentFilters(blockVideos = true, blockPodcasts = null),
            deviceInfo = DeviceMetadata(deviceName = "Pixel", sdkVersion = 34, lastSeen = Date(1_761_495_170_000L)),
            devices = listOf(UserDeviceData(deviceId = "dev-1", createdAt = Date(1_761_495_170_253L), lastSyncTime = 42L)),
            createdAt = Date(0L),
            updatedAt = null,
        )
        val out = encode(doc)
        assertEquals("1970-01-01T00:00:00.000Z", out["createdAt"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, out["updatedAt"])
        val filters = out["contentFilters"]!!.jsonObject
        assertEquals(JsonNull, filters["blockPodcasts"])
        assertFalse("the retired passcode field is never sent", "femalePasscodeHash" in filters)
        assertEquals("true", filters["enableContentFilters"]!!.jsonPrimitive.content)
        val info = out["deviceInfo"]!!.jsonObject
        assertEquals(JsonNull, info["firstSeen"])
        assertEquals("2025-10-26T16:12:50.000Z", info["lastSeen"]!!.jsonPrimitive.content)
        assertEquals("34", info["sdkVersion"]!!.jsonPrimitive.content)
        val device = out["devices"]!!.jsonArray.single().jsonObject
        assertEquals("2025-10-26T16:12:50.253Z", device["createdAt"]!!.jsonPrimitive.content)
        assertEquals(42L, device["lastSyncTime"]!!.jsonPrimitive.long)
        assertFalse(device["lastSyncTime"]!!.jsonPrimitive.isString)
        // every written timestamp matches the server's accepted pattern
        val iso = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""")
        listOf(out["createdAt"], info["lastSeen"], device["createdAt"]).forEach {
            assertTrue(iso.matches(it!!.jsonPrimitive.content))
        }
    }

    @Test
    fun `round trip of an imported doc preserves every known field`() {
        val doc = decode(imported)
        assertEquals(doc, decode(encode(doc).toString()))
    }

    @Test
    fun `explicit null for a non-null field falls back to the entity default instead of failing`() {
        val doc = decode("""{"userId":"u","contentFilters":{"enableContentFilters":null,"blockVideos":null},"devices":null}""")
        assertTrue(doc.contentFilters.enableContentFilters)
        assertFalse(doc.contentFilters.blockVideos)
        assertTrue(doc.devices.isEmpty())
    }

    @Test
    fun `empty object decodes to defaults`() {
        val doc = decode("{}")
        assertEquals(DevicePreferencesEntity(), doc)
        assertEquals(JsonObject::class, encode(doc)::class)
    }
}
