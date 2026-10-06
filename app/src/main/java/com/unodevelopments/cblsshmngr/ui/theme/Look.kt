package com.unodevelopments.cblsshmngr.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeId {
    LIGHT,
    DARK,
    HACKER,
    SCIFI,
    CONSOLE,
    CUSTOM,
}

enum class LaunchLayout {
    PANELS,
    TABS,
    LIST,
}

enum class TabStyle {
    UNDERLINE,
    PILLS,
}

data class Look(
    val id: ThemeId,
    val dark: Boolean,
    val scheme: ColorScheme,
    val layout: LaunchLayout,
    val tabStyle: TabStyle,
    val corner: Dp,
    val monospace: Boolean,
    val solidPanels: Boolean,
)

data class CustomChoices(
    val background: Int = 0xFFF7F8FA.toInt(),
    val primary: Int = 0xFF1565C0.toInt(),
    val accent: Int = 0xFF455A64.toInt(),
    val layout: LaunchLayout = LaunchLayout.PANELS,
    val tabStyle: TabStyle = TabStyle.UNDERLINE,
)

val LocalLook = staticCompositionLocalOf {
    Looks.of(ThemeId.LIGHT, LaunchLayout.PANELS, TabStyle.UNDERLINE, CustomChoices())
}

object Looks {
    fun of(
        id: ThemeId,
        layout: LaunchLayout,
        tabStyle: TabStyle,
        custom: CustomChoices,
    ): Look {
        val preset = preset(id, custom)
        return preset.copy(layout = layout, tabStyle = tabStyle)
    }

    fun defaults(id: ThemeId): Pair<LaunchLayout, TabStyle> = when (id) {
        ThemeId.LIGHT -> LaunchLayout.PANELS to TabStyle.UNDERLINE
        ThemeId.DARK -> LaunchLayout.LIST to TabStyle.UNDERLINE
        ThemeId.HACKER -> LaunchLayout.LIST to TabStyle.UNDERLINE
        ThemeId.SCIFI -> LaunchLayout.TABS to TabStyle.PILLS
        ThemeId.CONSOLE -> LaunchLayout.PANELS to TabStyle.PILLS
        ThemeId.CUSTOM -> LaunchLayout.PANELS to TabStyle.PILLS
    }

    private fun preset(id: ThemeId, custom: CustomChoices): Look = when (id) {
        ThemeId.LIGHT -> look(
            id = id,
            dark = false,
            background = Color(0xFFF7F8FA),
            surface = Color(0xFFFFFFFF),
            primary = Color(0xFF1565C0),
            accent = Color(0xFF455A64),
            ink = Color(0xFF1A1C1E),
            muted = Color(0xFF5C636A),
            corner = 20.dp,
            monospace = false,
            solidPanels = false,
            layout = LaunchLayout.PANELS,
            tabStyle = TabStyle.UNDERLINE,
        )
        ThemeId.DARK -> look(
            id = id,
            dark = true,
            background = Color(0xFF12141A),
            surface = Color(0xFF1C1F28),
            primary = Color(0xFFE6E8EE),
            accent = Color(0xFFB7C6FF),
            ink = Color(0xFFE6E8EE),
            muted = Color(0xFF9AA0AD),
            corner = 16.dp,
            monospace = false,
            solidPanels = true,
            layout = LaunchLayout.LIST,
            tabStyle = TabStyle.UNDERLINE,
        )
        ThemeId.HACKER -> look(
            id = id,
            dark = true,
            background = Color(0xFF050805),
            surface = Color(0xFF0C120C),
            primary = Color(0xFF39FF14),
            accent = Color(0xFF7CFF6B),
            ink = Color(0xFF39FF14),
            muted = Color(0xFF1F8A12),
            corner = 2.dp,
            monospace = true,
            solidPanels = true,
            layout = LaunchLayout.LIST,
            tabStyle = TabStyle.UNDERLINE,
        )
        ThemeId.SCIFI -> look(
            id = id,
            dark = true,
            background = Color(0xFF070B18),
            surface = Color(0xFF10182C),
            primary = Color(0xFF5CE1FF),
            accent = Color(0xFFFF4D8D),
            ink = Color(0xFFE7F6FF),
            muted = Color(0xFF8AA0C8),
            corner = 8.dp,
            monospace = true,
            solidPanels = true,
            layout = LaunchLayout.TABS,
            tabStyle = TabStyle.PILLS,
        )
        ThemeId.CONSOLE -> look(
            id = id,
            dark = false,
            background = Paper,
            surface = Cream,
            primary = Moss,
            accent = Navy,
            ink = Ink,
            muted = Mist,
            corner = 28.dp,
            monospace = false,
            solidPanels = true,
            layout = LaunchLayout.PANELS,
            tabStyle = TabStyle.PILLS,
        )
        ThemeId.CUSTOM -> {
            val background = Color(custom.background)
            val primary = Color(custom.primary)
            val accent = Color(custom.accent)
            val dark = background.luminance() < 0.4f
            val ink = if (dark) Color.White else Color(0xFF1A1C1E)
            val muted = if (dark) Color.White.copy(alpha = 0.62f) else Color(0xFF5C636A)
            look(
                id = id,
                dark = dark,
                background = background,
                surface = if (dark) {
                    Color(
                        red = (background.red + 0.05f).coerceAtMost(1f),
                        green = (background.green + 0.05f).coerceAtMost(1f),
                        blue = (background.blue + 0.06f).coerceAtMost(1f),
                    )
                } else {
                    Color.White
                },
                primary = primary,
                accent = accent,
                ink = ink,
                muted = muted,
                corner = 18.dp,
                monospace = false,
                solidPanels = dark,
                layout = custom.layout,
                tabStyle = custom.tabStyle,
            )
        }
    }

    private fun look(
        id: ThemeId,
        dark: Boolean,
        background: Color,
        surface: Color,
        primary: Color,
        accent: Color,
        ink: Color,
        muted: Color,
        corner: Dp,
        monospace: Boolean,
        solidPanels: Boolean,
        layout: LaunchLayout,
        tabStyle: TabStyle,
    ): Look {
        val onPrimary = if (primary.luminance() > 0.6f) Color(0xFF101114) else Color.White
        val onAccent = if (accent.luminance() > 0.6f) Color(0xFF101114) else Color.White
        val scheme = if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = primary,
                onSecondary = onPrimary,
                tertiary = accent,
                onTertiary = onAccent,
                background = background,
                onBackground = ink,
                surface = surface,
                onSurface = ink,
                surfaceVariant = surface,
                onSurfaceVariant = muted,
                outline = muted.copy(alpha = 0.45f),
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = primary,
                onSecondary = onPrimary,
                tertiary = accent,
                onTertiary = onAccent,
                background = background,
                onBackground = ink,
                surface = surface,
                onSurface = ink,
                surfaceVariant = Color(0xFFE7EEF6),
                onSurfaceVariant = muted,
                outline = Color(0xFFD5D8DE),
            )
        }
        return Look(id, dark, scheme, layout, tabStyle, corner, monospace, solidPanels)
    }
}
