package com.opiconchanger.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIconStoreTest {

    @Test
    fun parseExtractsPackageNamesFromLsOutput() {
        val input = "/data/oplus/uxicons/choose/com.foo.cfg\n/data/oplus/uxicons/choose/com.bar.cfg\n"
        assertEquals(setOf("com.foo", "com.bar"), parseCustomizedPackages(input))
    }

    @Test
    fun parseHandlesBlankAndEmptyInput() {
        assertEquals(emptySet<String>(), parseCustomizedPackages(""))
        assertEquals(setOf("com.foo"), parseCustomizedPackages("com.foo.cfg"))
    }

    @Test
    fun parseKeepsDotsInsidePackageName() {
        val result = parseCustomizedPackages("/data/oplus/uxicons/choose/com.android.settings.cfg\n")
        assertTrue("com.android.settings" in result)
    }
}
