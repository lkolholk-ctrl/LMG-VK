package com.lmg.vk.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.lmg.vk.R

/** Варианты ярлыка приложения, доступные через activity-alias в manifest. */
enum class LauncherIcon(
    val id: String,
    val titleRes: Int,
    val drawableRes: Int,
    val aliasClassName: String,
) {
    SUNSET("sunset", R.string.launcher_icon_sunset, R.drawable.launcher_icon_sunset, "LauncherIconSunset"),
    EMERALD("emerald", R.string.launcher_icon_emerald, R.drawable.launcher_icon_emerald, "LauncherIconEmerald"),
    LAGOON("lagoon", R.string.launcher_icon_lagoon, R.drawable.launcher_icon_lagoon, "LauncherIconLagoon"),
    AMETHYST("amethyst", R.string.launcher_icon_amethyst, R.drawable.launcher_icon_amethyst, "LauncherIconAmethyst"),
    PRISM("prism", R.string.launcher_icon_prism, R.drawable.launcher_icon_prism, "LauncherIconPrism"),
    NEON("neon", R.string.launcher_icon_neon, R.drawable.launcher_icon_neon, "LauncherIconNeon"),
    FUCHSIA("fuchsia", R.string.launcher_icon_fuchsia, R.drawable.launcher_icon_fuchsia, "LauncherIconFuchsia"),
    AMBER("amber", R.string.launcher_icon_amber, R.drawable.launcher_icon_amber, "LauncherIconAmber"),
    RUBY("ruby", R.string.launcher_icon_ruby, R.drawable.launcher_icon_ruby, "LauncherIconRuby"),
    GRAPHITE("graphite", R.string.launcher_icon_graphite, R.drawable.launcher_icon_graphite, "LauncherIconGraphite"),
    ROSE("rose", R.string.launcher_icon_rose, R.drawable.launcher_icon_rose, "LauncherIconRose"),
    COBALT("cobalt", R.string.launcher_icon_cobalt, R.drawable.launcher_icon_cobalt, "LauncherIconCobalt"),
    PEARL("pearl", R.string.launcher_icon_pearl, R.drawable.launcher_icon_pearl, "LauncherIconPearl"),
}

/**
 * Системная иконка Android выбирается через включение одного activity-alias.
 * Выбор сохраняется в SharedPreferences и не требует перезапуска приложения.
 */
object LauncherIconManager {
    private const val PREFS = "launcher_icon"
    private const val KEY_SELECTED_ID = "selected_id"

    fun current(context: Context): LauncherIcon {
        val savedId = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_ID, LauncherIcon.SUNSET.id)
        return LauncherIcon.values().firstOrNull { it.id == savedId } ?: LauncherIcon.SUNSET
    }

    /** @return false, если лаунчер-алиас не удалось переключить на устройстве. */
    fun select(context: Context, icon: LauncherIcon): Boolean {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        try {
            // Сначала включаем новую: в лаунчере не возникает промежутка без ярлыка.
            packageManager.setComponentEnabledSetting(
                componentName(appContext, icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            LauncherIcon.values()
                .asSequence()
                .filter { it != icon }
                .forEach { other ->
                    packageManager.setComponentEnabledSetting(
                        componentName(appContext, other),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_ID, icon.id)
                .apply()
            return true
        } catch (_: IllegalArgumentException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
    }

    private fun componentName(context: Context, icon: LauncherIcon) = ComponentName(
        context.packageName,
        "${context.packageName}.${icon.aliasClassName}",
    )
}
