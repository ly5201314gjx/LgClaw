package com.lgclaw.ui

import com.lgclaw.config.UiPreferencesConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesModelTest {

    @Test
    fun uiPreferencesConfig_defaultsUseGlassPresetAndReadableRanges() {
        val config = UiPreferencesConfig()

        assertEquals("obsidian_glass", config.themePreset)
        assertEquals("native", config.themeBubbleStyle)
        assertEquals("system", config.themeFontFamily)
        assertTrue(config.themeBubbleOpacity in 0.28f..1f)
        assertTrue(config.themeBubbleCornerRadius in 8f..28f)
        assertTrue(config.themeMessageFontSizeSp in 12f..20f)
        assertTrue(config.themeMessageLineHeightMultiplier in 1f..1.7f)
    }

    @Test
    fun uiFontFamilyChoice_mapsLegacyRoundedToSans() {
        assertEquals(UiFontFamilyChoice.Sans, UiFontFamilyChoice.fromKey("rounded"))
        assertEquals(UiFontFamilyChoice.Custom, UiFontFamilyChoice.fromKey("custom"))
        assertEquals(UiFontFamilyChoice.System, UiFontFamilyChoice.fromKey("missing"))
    }

    @Test
    fun uiBubbleStyle_fallsBackToNativeForUnknownKeys() {
        assertEquals(UiBubbleStyle.Frosted, UiBubbleStyle.fromKey("frosted"))
        assertEquals(UiBubbleStyle.Water, UiBubbleStyle.fromKey("water"))
        assertEquals(UiBubbleStyle.None, UiBubbleStyle.fromKey("none"))
        assertEquals(UiBubbleStyle.Native, UiBubbleStyle.fromKey("unknown"))
    }
}
