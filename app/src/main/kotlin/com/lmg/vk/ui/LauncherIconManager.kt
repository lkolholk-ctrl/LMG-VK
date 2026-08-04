package com.lmg.vk.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.lmg.vk.R

/** Варианты ярлыка приложения, доступные через activity-alias в manifest. */
enum class LauncherIcon(
    val id: String,
    val title: String,
    val drawableRes: Int,
    val aliasClassName: String,
) {
    SUNSET("sunset", "Закат", R.drawable.launcher_icon_sunset, "LauncherIconSunset"),
    EMERALD("emerald", "Изумруд", R.drawable.launcher_icon_emerald, "LauncherIconEmerald"),
    LAGOON("lagoon", "Лагуна", R.drawable.launcher_icon_lagoon, "LauncherIconLagoon"),
    AMETHYST("amethyst", "Аметист", R.drawable.launcher_icon_amethyst, "LauncherIconAmethyst"),
    PRISM("prism", "Призма", R.drawable.launcher_icon_prism, "LauncherIconPrism"),
    NEON("neon", "Неон", R.drawable.launcher_icon_neon, "LauncherIconNeon"),
    FUCHSIA("fuchsia", "Фуксия", R.drawable.launcher_icon_fuchsia, "LauncherIconFuchsia"),
    AMBER("amber", "Янтарь", R.drawable.launcher_icon_amber, "LauncherIconAmber"),
    RUBY("ruby", "Рубин", R.drawable.launcher_icon_ruby, "LauncherIconRuby"),
    GRAPHITE("graphite", "Графит", R.drawable.launcher_icon_graphite, "LauncherIconGraphite"),
    ROSE("rose", "Роза", R.drawable.launcher_icon_rose, "LauncherIconRose"),
    COBALT("cobalt", "Кобальт", R.drawable.launcher_icon_cobalt, "LauncherIconCobalt"),
    PEARL("pearl", "Жемчуг", R.drawable.launcher_icon_pearl, "LauncherIconPearl"),
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
