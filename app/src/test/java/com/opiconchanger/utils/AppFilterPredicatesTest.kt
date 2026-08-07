package com.opiconchanger.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterPredicatesTest {
    private val userApp = FilterableApp(pkg = "com.example.app", isSystem = false)
    private val systemApp = FilterableApp(pkg = "com.android.settings", isSystem = true)

    @Test
    fun allAlwaysMatches() {
        assertTrue(AppFilterPredicates.matches(userApp, AppFilter.ALL, emptySet(), emptySet()))
        assertTrue(AppFilterPredicates.matches(systemApp, AppFilter.ALL, emptySet(), emptySet()))
    }

    @Test
    fun systemMatchesOnlySystemApps() {
        assertTrue(AppFilterPredicates.matches(systemApp, AppFilter.SYSTEM, emptySet(), emptySet()))
        assertFalse(AppFilterPredicates.matches(userApp, AppFilter.SYSTEM, emptySet(), emptySet()))
    }

    @Test
    fun userMatchesOnlyNonSystemApps() {
        assertTrue(AppFilterPredicates.matches(userApp, AppFilter.USER, emptySet(), emptySet()))
        assertFalse(AppFilterPredicates.matches(systemApp, AppFilter.USER, emptySet(), emptySet()))
    }

    @Test
    fun unadaptedExcludesAdaptedPackage() {
        val adapted = setOf("com.example.app")
        assertFalse(AppFilterPredicates.matches(userApp, AppFilter.UNADAPTED, adapted, emptySet()))
    }

    @Test
    fun unadaptedExcludesCustomizedPackage() {
        val customized = setOf("com.example.app")
        assertFalse(AppFilterPredicates.matches(userApp, AppFilter.UNADAPTED, emptySet(), customized))
    }

    @Test
    fun unadaptedMatchesWhenNeitherAdaptedNorCustomized() {
        assertTrue(AppFilterPredicates.matches(userApp, AppFilter.UNADAPTED, emptySet(), emptySet()))
    }
}
