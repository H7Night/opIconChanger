package com.opiconchanger.utils

enum class AppFilter { ALL, SYSTEM, USER, UNADAPTED }

data class FilterableApp(val pkg: String, val isSystem: Boolean)

object AppFilterPredicates {
    fun matches(
        app: FilterableApp,
        filter: AppFilter,
        adaptedPackages: Set<String>,
        customizedPackages: Set<String>
    ): Boolean = when (filter) {
        AppFilter.ALL -> true
        AppFilter.SYSTEM -> app.isSystem
        AppFilter.USER -> !app.isSystem
        AppFilter.UNADAPTED ->
            app.pkg !in adaptedPackages && app.pkg !in customizedPackages
    }
}
