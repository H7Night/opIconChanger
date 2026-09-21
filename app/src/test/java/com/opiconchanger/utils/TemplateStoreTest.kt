package com.opiconchanger.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateStoreTest {

    private val entryA = TemplateEntry("com.a", "com.pack", "ic_a")
    private val entryB = TemplateEntry("com.b", "com.pack", "ic_b")

    @Test
    fun roundTripPreservesTemplates() {
        val items = listOf(
            IconTemplate("id-1", "我的模板", 1000L, listOf(entryA, entryB)),
            IconTemplate("id-2", "第二套", 2000L, emptyList())
        )
        assertEquals(items, TemplateStore.decode(TemplateStore.encode(items)))
    }

    @Test
    fun encodeEmptyIsDecodable() {
        assertEquals(emptyList<IconTemplate>(), TemplateStore.decode(TemplateStore.encode(emptyList())))
    }

    @Test
    fun decodeCorruptJsonReturnsEmpty() {
        assertEquals(emptyList<IconTemplate>(), TemplateStore.decode("not json"))
        assertEquals(emptyList<IconTemplate>(), TemplateStore.decode("{}"))
    }

    @Test
    fun decodeSkipsTemplateWithoutId() {
        val json = """{"version":1,"templates":[{"name":"no-id","createdAt":1,"entries":[]}]}"""
        assertTrue(TemplateStore.decode(json).isEmpty())
    }

    @Test
    fun decodeSkipsEntryWithoutPkg() {
        val json = """{"version":1,"templates":[{"id":"x","name":"n","createdAt":1,"entries":[{"iconPackPkg":"p","drawableResName":"d"}]}]}"""
        val decoded = TemplateStore.decode(json)
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].entries.isEmpty())
    }

    @Test
    fun isValidTemplateNameRejectsBlank() {
        assertFalse(TemplateStore.isValidTemplateName(""))
        assertFalse(TemplateStore.isValidTemplateName("   "))
    }

    @Test
    fun isValidTemplateNameAcceptsNonBlank() {
        assertTrue(TemplateStore.isValidTemplateName("我的模板"))
        assertTrue(TemplateStore.isValidTemplateName(" x "))
    }
}
