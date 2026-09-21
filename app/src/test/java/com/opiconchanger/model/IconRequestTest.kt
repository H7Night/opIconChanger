package com.opiconchanger.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRequestTest {

    @Test
    fun isValidPackageNameAcceptsNormalPackages() {
        assertTrue(IconRequest.isValidPackageName("com.opiconchanger"))
        assertTrue(IconRequest.isValidPackageName("com.android.launcher"))
        assertTrue(IconRequest.isValidPackageName("a.b.c"))
        assertTrue(IconRequest.isValidPackageName("com.example.app_1"))
    }

    @Test
    fun isValidPackageNameRejectsPathTraversal() {
        assertFalse(IconRequest.isValidPackageName("../evil"))
        assertFalse(IconRequest.isValidPackageName(".."))
        assertFalse(IconRequest.isValidPackageName("com/evil"))
        assertFalse(IconRequest.isValidPackageName("com\\evil"))
    }

    @Test
    fun isValidPackageNameRejectsJunk() {
        assertFalse(IconRequest.isValidPackageName(""))
        assertFalse(IconRequest.isValidPackageName(" "))
        assertFalse(IconRequest.isValidPackageName("has space"))
        assertFalse(IconRequest.isValidPackageName("1.start.with.digit"))
        assertFalse(IconRequest.isValidPackageName("..com.evil"))
    }

    @Test
    fun isValidResourceNameAcceptsNormal() {
        assertTrue(IconRequest.isValidResourceName("ic_telegram"))
        assertTrue(IconRequest.isValidResourceName("activobank"))
        assertTrue(IconRequest.isValidResourceName("a1_b2"))
    }

    @Test
    fun isValidResourceNameRejectsDangerous() {
        assertFalse(IconRequest.isValidResourceName(""))
        assertFalse(IconRequest.isValidResourceName("../evil"))
        assertFalse(IconRequest.isValidResourceName("ic:colon"))
        assertFalse(IconRequest.isValidResourceName("ic space"))
        assertFalse(IconRequest.isValidResourceName("ic;rm"))
    }

    @Test
    fun applyRoundTrip() {
        val req = IconRequest.apply("com.foo", "com.pack", "ic_foo")
        assertEquals(req, IconRequest.fromJson(req.toJson()))
    }

    @Test
    fun restoreRoundTripKeepsOnlyPackages() {
        val req = IconRequest.restore(listOf("com.a", "com.b"))
        val restored = IconRequest.fromJson(req.toJson())!!
        assertEquals(RequestAction.RESTORE, restored.action)
        assertEquals(listOf("com.a", "com.b"), restored.items.map { it.targetPkg })
    }

    @Test
    fun legacyFlatJsonParsesAsSingleApply() {
        val legacy = """{"targetPkg":"com.foo","iconPackPkg":"com.pack","drawableResName":"ic_foo"}"""
        val req = IconRequest.fromJson(legacy)!!
        assertEquals(RequestAction.APPLY, req.action)
        assertEquals(1, req.items.size)
        assertEquals("com.foo", req.items[0].targetPkg)
    }

    @Test
    fun rejectsEmptyOrTooManyItems() {
        assertFalse(IconRequest.fromJson("""{"action":"apply","items":[]}""") != null)
        val many = (0..IconRequest.MAX_ITEMS).joinToString(",") {
            """{"targetPkg":"com.p$it","iconPackPkg":"com.pack","drawableResName":"ic"}"""
        }
        assertFalse(IconRequest.fromJson("""{"action":"apply","items":[$many]}""") != null)
    }

    @Test
    fun rejectsUnknownAction() {
        assertFalse(IconRequest.fromJson("""{"action":"delete","items":[{"targetPkg":"com.a"}]}""") != null)
    }

    @Test
    fun rejectsInvalidItemFields() {
        assertFalse(IconRequest.fromJson("""{"action":"apply","items":[{"targetPkg":"../evil","iconPackPkg":"com.pack","drawableResName":"ic"}]}""") != null)
        assertFalse(IconRequest.fromJson("""{"action":"apply","items":[{"targetPkg":"com.a","iconPackPkg":"com.pack","drawableResName":"../evil"}]}""") != null)
        assertFalse(IconRequest.fromJson("""{"action":"restore","items":[{"targetPkg":"../evil"}]}""") != null)
        assertFalse(IconRequest.fromJson("not json") != null)
    }
}
