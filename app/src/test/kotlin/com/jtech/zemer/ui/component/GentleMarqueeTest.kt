package com.jtech.zemer.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the pure param spec behind the shared one-shot title marquee ([gentleMarqueeParams]). The
 * marquee node restarts its animation on ANY param change, so these params ARE the behavior: a
 * focus gain must always be a param change (the D-pad re-arm), and a focus loss must land on the
 * no-animation state (iterations = 0) - falling back to the resting params would itself be a param
 * change and replay the glide behind the D-pad cursor on every row the user leaves.
 */
class GentleMarqueeTest {

    @Test
    fun `never focused is the calm appear-once default`() {
        assertEquals(
            GentleMarqueeParams(iterations = 1, initialDelayMillis = 3000),
            gentleMarqueeParams(focused = false, everFocused = false),
        )
    }

    @Test
    fun `focus gain re-arms promptly and is a param change from both unfocused states`() {
        val focusedParams = gentleMarqueeParams(focused = true, everFocused = false)
        assertEquals(GentleMarqueeParams(iterations = 1, initialDelayMillis = 600), focusedParams)
        // focused dominates the latch, so a re-gain after a loss lands on the same params...
        assertEquals(focusedParams, gentleMarqueeParams(focused = true, everFocused = true))
        // ...and they differ from BOTH unfocused states, so every focus gain restarts the node.
        assertNotEquals(focusedParams, gentleMarqueeParams(focused = false, everFocused = false))
        assertNotEquals(focusedParams, gentleMarqueeParams(focused = false, everFocused = true))
    }

    @Test
    fun `focus loss disables the glide instead of replaying it`() {
        val afterLoss = gentleMarqueeParams(focused = false, everFocused = true)
        assertEquals(0, afterLoss.iterations)
        // It must NOT equal the resting params - that fallback is exactly the replay-on-loss bug.
        assertNotEquals(gentleMarqueeParams(focused = false, everFocused = false), afterLoss)
    }
}
