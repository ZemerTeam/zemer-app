package com.jtech.zemer.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvSignalsTest {
    @Test
    fun `the bitmask, with the release-only bits ignored in a debug build`() {
        assertEquals(0, EnvSignals.compute(releaseBuild = true, debuggable = false, certMismatch = false, hooking = false, emulator = false))
        assertEquals(15, EnvSignals.compute(releaseBuild = true, debuggable = true, certMismatch = true, hooking = true, emulator = true))
        assertEquals("a debug build is debuggable and differently signed by design", 12,
            EnvSignals.compute(releaseBuild = false, debuggable = true, certMismatch = true, hooking = true, emulator = true))
        assertEquals(2, EnvSignals.compute(releaseBuild = true, debuggable = false, certMismatch = true, hooking = false, emulator = false))
        assertEquals(20, EnvSignals.compute(releaseBuild = true, debuggable = false, certMismatch = false, hooking = true, emulator = false, targeted = true))
        assertEquals("targeting counts in a debug build too", 16,
            EnvSignals.compute(releaseBuild = false, debuggable = true, certMismatch = false, hooking = false, emulator = false, targeted = true))
    }

    @Test
    fun `a Frida agent or gadget in our process targets Zemer, a device-wide framework does not`() {
        listOf(
            "7f00-7f10 r-xp 00000000 00:00 0  /memfd:frida-agent-64.so (deleted)",
            "7f00-7f10 r-xp 00000000 00:00 0  /data/app/~~a==/com.example-b==/lib/arm64/libgadget.so",
        ).forEach { assertTrue(it, EnvSignals.isFridaMapping(it)) }
        listOf(
            "7f00-7f10 r-xp 00000000 00:00 0  /data/adb/modules/zygisk_lsposed/lib/liblspd.so",
            "7f00-7f10 r-xp 00000000 00:00 0  /system/lib64/libriru.so",
            "7f00-7f10 r-xp 00000000 00:00 0  /system/lib64/libc.so",
        ).forEach { assertFalse(it, EnvSignals.isFridaMapping(it)) }
    }

    @Test
    fun `hooking frameworks are recognised by their mappings, ordinary libraries are not`() {
        listOf(
            "7f00-7f10 r-xp 00000000 00:00 0  /data/local/tmp/re.frida.server/frida-agent-64.so",
            "7f00-7f10 r--p 00000000 00:00 0  /system/framework/XposedBridge.jar",
            "7f00-7f10 r-xp 00000000 00:00 0  /data/adb/modules/zygisk_lsposed/lib/liblspd.so",
            "7f00-7f10 r-xp 00000000 00:00 0  /system/lib64/libsubstrate.so",
        ).forEach { assertTrue(it, EnvSignals.isHookMapping(it)) }
        listOf(
            "7f00-7f10 r-xp 00000000 00:00 0  /data/app/~~a==/com.jtech.zemer-b==/base.apk",
            "7f00-7f10 r-xp 00000000 00:00 0  /system/lib64/libc.so",
            "7f00-7f10 rw-p 00000000 00:00 0  [anon:dalvik-main space]",
        ).forEach { assertFalse(it, EnvSignals.isHookMapping(it)) }
    }

    @Test
    fun `emulators are recognised, a real phone is not`() {
        assertTrue(EnvSignals.isEmulator("google/sdk_gphone64_arm64/emu64a:16/...", "ranchu", "sdk_gphone64_arm64", "sdk_gphone64_arm64"))
        assertTrue(EnvSignals.isEmulator("generic/sdk/generic:8.0.0/...", "goldfish", "sdk", "Android SDK built for x86"))
        assertTrue(EnvSignals.isEmulator("Genymotion/vbox86p/vbox86p:9/...", "vbox86", "vbox86p", "Custom Phone"))
        assertFalse(EnvSignals.isEmulator("samsung/a54xsqw/a54x:16/...", "s5e8835", "a54xsqw", "SM-A546U"))
        assertFalse(EnvSignals.isEmulator("google/husky/husky:16/...", "husky", "husky", "Pixel 8 Pro"))
    }
}
