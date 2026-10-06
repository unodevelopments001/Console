package com.unodevelopments.cblsshmngr.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("app-look", Context.MODE_PRIVATE)

    private val _id = MutableStateFlow(readId())
    val id: StateFlow<ThemeId> = _id.asStateFlow()

    private val _layout = MutableStateFlow(readLayout())
    val layout: StateFlow<LaunchLayout> = _layout.asStateFlow()

    private val _tabs = MutableStateFlow(readTabs())
    val tabs: StateFlow<TabStyle> = _tabs.asStateFlow()

    private val _custom = MutableStateFlow(readCustom())
    val custom: StateFlow<CustomChoices> = _custom.asStateFlow()

    private val _appLock = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
        _onboardingDone.value = true
    }

    fun setAppLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK, enabled).apply()
        _appLock.value = enabled
    }

    fun select(id: ThemeId) {
        val (layout, tabs) = Looks.defaults(id)
        prefs.edit()
            .putString(KEY_ID, id.name)
            .putString(KEY_LAYOUT, layout.name)
            .putString(KEY_TABS, tabs.name)
            .apply()
        _id.value = id
        _layout.value = layout
        _tabs.value = tabs
        if (id == ThemeId.CUSTOM) {
            _custom.value = _custom.value.copy(layout = layout, tabStyle = tabs)
        }
    }

    fun setLayout(layout: LaunchLayout) {
        prefs.edit().putString(KEY_LAYOUT, layout.name).apply()
        _layout.value = layout
    }

    fun setTabs(tabs: TabStyle) {
        prefs.edit().putString(KEY_TABS, tabs.name).apply()
        _tabs.value = tabs
    }

    fun setCustom(background: Int? = null, primary: Int? = null, accent: Int? = null) {
        val next = _custom.value.copy(
            background = background ?: _custom.value.background,
            primary = primary ?: _custom.value.primary,
            accent = accent ?: _custom.value.accent,
        )
        prefs.edit()
            .putString(KEY_ID, ThemeId.CUSTOM.name)
            .putInt(KEY_BG, next.background)
            .putInt(KEY_PRIMARY, next.primary)
            .putInt(KEY_ACCENT, next.accent)
            .apply()
        _custom.value = next
        _id.value = ThemeId.CUSTOM
    }

    private fun readId(): ThemeId = enumOr(prefs.getString(KEY_ID, null), ThemeId.LIGHT)

    private fun readLayout(): LaunchLayout {
        val saved = prefs.getString(KEY_LAYOUT, null)
        return if (saved == null) Looks.defaults(readId()).first else enumOr(saved, LaunchLayout.PANELS)
    }

    private fun readTabs(): TabStyle {
        val saved = prefs.getString(KEY_TABS, null)
        return if (saved == null) Looks.defaults(readId()).second else enumOr(saved, TabStyle.UNDERLINE)
    }

    private fun readCustom(): CustomChoices = CustomChoices(
        background = prefs.getInt(KEY_BG, CustomChoices().background),
        primary = prefs.getInt(KEY_PRIMARY, CustomChoices().primary),
        accent = prefs.getInt(KEY_ACCENT, CustomChoices().accent),
        layout = readLayout(),
        tabStyle = readTabs(),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T {
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_LAYOUT = "layout"
        const val KEY_TABS = "tabs"
        const val KEY_BG = "bg"
        const val KEY_PRIMARY = "primary"
        const val KEY_ACCENT = "accent"
        const val KEY_LOCK = "app_lock"
        const val KEY_ONBOARDING = "onboarding_done"
    }
}
