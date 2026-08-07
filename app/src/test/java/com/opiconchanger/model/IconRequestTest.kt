package com.opiconchanger.model

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
    fun fromJsonRejectsInvalidFields() {
        assertTrue(IconRequest.fromJson("""{"targetPkg":"com.foo","iconPackPkg":"com.pack","drawableResName":"ic_foo"}""") != null)
        assertFalse(IconRequest.fromJson("""{"targetPkg":"../evil","iconPackPkg":"com.pack","drawableResName":"ic_foo"}""") != null)
        assertFalse(IconRequest.fromJson("""{"targetPkg":"com.foo","iconPackPkg":"com.pack","drawableResName":"../evil"}""") != null)
        assertFalse(IconRequest.fromJson("not json") != null)
        assertFalse(IconRequest.fromJson("""{"targetPkg":"com.foo"}""") != null)
    }

    @Test
    fun roundTripJson() {
        val req = IconRequest("com.foo", "com.pack", "ic_foo")
        val restored = IconRequest.fromJson(req.toJson())
        assertTrue(restored == req)
    }
}
