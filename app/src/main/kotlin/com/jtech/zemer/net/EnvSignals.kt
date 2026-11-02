package com.jtech.zemer.net

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.jtech.zemer.BuildConfig
import com.jtech.zemer.sig.NativeSig
import java.io.File

/**
 * Environment signals sent to the Zemer servers as `X-Zemer-Env: <bitmask>` (only when one is set), which the
 * servers only count. INFORMATION ONLY: nothing in the app may ever block, degrade or change behaviour on them.
 */
object EnvSignals {
    const val HEADER = "X-Zemer-Env"
    const val RELEASE_DEBUGGABLE = 1
    const val CERT_MISMATCH = 2
    const val HOOKING = 4
    const val EMULATOR = 8

    /**
     * Zemer itself is targeted, not just a framework present: an Xposed bridge loaded in our process (a module has
     * Zemer in scope), a Frida agent or gadget mapped into it, or the native signer's code patched in memory.
     * [HOOKING] stays the general "a framework is injected" note.
     */
    const val TARGETED = 16

    /** The signals of this process; 0 (no header) until [init] has run. */
    @Volatile
    var mask = 0
        internal set

    /** Computes the signals once, off the main thread, after the native signer has bound its certificate. */
    fun init(context: Context) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val maps = runCatching { File("/proc/self/maps").readLines() }.getOrDefault(emptyList())
        val xposed = runCatching { Class.forName("de.robv.android.xposed.XposedBridge", false, ClassLoader.getSystemClassLoader()) }.isSuccess
        mask = compute(
            releaseBuild = !BuildConfig.DEBUG,
            debuggable = debuggable,
            certMismatch = NativeSig.certMismatch(),
            hooking = xposed || maps.any(::isHookMapping),
            emulator = isEmulator(Build.FINGERPRINT, Build.HARDWARE, Build.PRODUCT, Build.MODEL),
            targeted = xposed || maps.any(::isFridaMapping) || NativeSig.codePatched(),
        )
    }

    /** Bits 1 and 2 concern release builds only; a debug build is debuggable and differently signed by design. */
    internal fun compute(
        releaseBuild: Boolean,
        debuggable: Boolean,
        certMismatch: Boolean,
        hooking: Boolean,
        emulator: Boolean,
        targeted: Boolean = false,
    ): Int =
        (if (releaseBuild && debuggable) RELEASE_DEBUGGABLE else 0) or
            (if (releaseBuild && certMismatch) CERT_MISMATCH else 0) or
            (if (hooking) HOOKING else 0) or
            (if (emulator) EMULATOR else 0) or
            (if (targeted) TARGETED else 0)

    /** A memory mapping of a known code-hooking framework (Frida, Xposed and its successors, Substrate). */
    internal fun isHookMapping(line: String): Boolean {
        val path = line.substringAfter('/', "").lowercase()
        return HOOK_MARKERS.any { it in path }
    }

    /** A Frida agent or gadget in our own process: someone attached Frida to Zemer (frida-server is never mapped here). */
    internal fun isFridaMapping(line: String): Boolean {
        val path = line.substringAfter('/', "").lowercase()
        return "frida" in path || "libgadget" in path
    }

    private val HOOK_MARKERS = listOf("frida", "xposed", "lspd", "lsposed", "edxp", "riru", "substrate", "libgadget")

    internal fun isEmulator(fingerprint: String, hardware: String, product: String, model: String): Boolean =
        hardware in setOf("goldfish", "ranchu", "vbox86") ||
            fingerprint.startsWith("generic") || "emulator" in fingerprint || "sdk_gphone" in fingerprint ||
            product.startsWith("sdk") || "emulator" in product || product.startsWith("vbox") ||
            model.startsWith("Android SDK built for")
}
