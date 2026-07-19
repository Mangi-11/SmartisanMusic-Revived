package com.smartisan.music.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconManagerTest {
    @Test
    fun `original alias resolves to original icon`() {
        assertEquals(AppIcon.Original, resolveAppIcon(setOf(AppIcon.Original)))
    }

    @Test
    fun `modern alias resolves to modern icon`() {
        assertEquals(AppIcon.Modern, resolveAppIcon(setOf(AppIcon.Modern)))
    }

    @Test
    fun `invalid alias combinations fall back to original icon`() {
        assertEquals(AppIcon.Original, resolveAppIcon(emptySet()))
        assertEquals(AppIcon.Original, resolveAppIcon(AppIcon.entries.toSet()))
    }
}
