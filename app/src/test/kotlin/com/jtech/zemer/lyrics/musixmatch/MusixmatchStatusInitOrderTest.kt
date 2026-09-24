package com.jtech.zemer.lyrics.musixmatch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

/**
 * Regression: initialising a nested status object BEFORE the companion (the lookup path records a status before
 * settings is opened) made the eager singleton list capture a null, and the first `parse` after install threw
 * an NPE. Class init happens once per loader, so the order is forced in a fresh class loader.
 */
class MusixmatchStatusInitOrderTest {
    @Test
    fun `parse works when a nested object initialised the class first`() {
        val urls = System.getProperty("java.class.path").split(File.pathSeparator).map { File(it).toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, null).use { loader ->
            val hit = loader.loadClass("com.jtech.zemer.lyrics.musixmatch.MusixmatchStatus\$Hit").getField("INSTANCE").get(null)
            val status = loader.loadClass("com.jtech.zemer.lyrics.musixmatch.MusixmatchStatus")
            val companion = status.getField("Companion").get(null)
            val parsed = companion.javaClass.getMethod("parse", String::class.java).invoke(companion, "hit")
            assertEquals(hit, parsed)
            assertEquals("network", companion.javaClass.getMethod("parse", String::class.java).invoke(companion, "network")?.let { status.getMethod("getCode").invoke(it) })
        }
    }
}
