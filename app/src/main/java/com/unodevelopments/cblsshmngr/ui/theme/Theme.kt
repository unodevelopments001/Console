package com.unodevelopments.cblsshmngr.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unodevelopments.cblsshmngr.CabalApp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as CabalApp
    val id by app.themeStore.id.collectAsStateWithLifecycle()
    val layout by app.themeStore.layout.collectAsStateWithLifecycle()
    val tabs by app.themeStore.tabs.collectAsStateWithLifecycle()
    val custom by app.themeStore.custom.collectAsStateWithLifecycle()
    val look = Looks.of(id, layout, tabs, custom)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(look.scheme.background.toArgb()))
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !look.dark
            controller.isAppearanceLightNavigationBars = !look.dark
        }
    }
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(
            colorScheme = look.scheme,
            typography = if (look.monospace) monoType() else Typography,
            shapes = Shapes(
                small = RoundedCornerShape(look.corner / 2),
                medium = RoundedCornerShape(look.corner * 0.75f),
                large = RoundedCornerShape(look.corner),
                extraLarge = RoundedCornerShape(look.corner),
            ),
            content = content,
        )
    }
}

private fun monoType(): Typography {
    val base = Typography
    val mono = FontFamily.Monospace
    return base.copy(
        displayLarge = base.displayLarge.mono(mono),
        headlineMedium = base.headlineMedium.mono(mono),
        titleLarge = base.titleLarge.mono(mono),
        titleMedium = base.titleMedium.mono(mono),
        bodyLarge = base.bodyLarge.mono(mono),
        bodyMedium = base.bodyMedium.mono(mono),
        labelLarge = base.labelLarge.mono(mono),
    )
}

private fun TextStyle.mono(family: FontFamily) = copy(fontFamily = family)

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
