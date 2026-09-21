package com.opiconchanger.utils

import com.opiconchanger.model.IconAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRequestWriterTest {

    @Test
    fun chunkEmptyReturnsEmpty() {
        assertTrue(IconRequestWriter.chunk(emptyList()).isEmpty())
    }

    @Test
    fun chunkSmallerThanLimitReturnsSingle() {
        val items = (1..3).map { IconAction("com.p$it") }
        assertEquals(listOf(items), IconRequestWriter.chunk(items, size = 10))
    }

    @Test
    fun chunkSplitsAtLimit() {
        val items = (1..5).map { IconAction("com.p$it") }
        val chunks = IconRequestWriter.chunk(items, size = 2)
        assertEquals(3, chunks.size)
        assertEquals(2, chunks[0].size)
        assertEquals(1, chunks[2].size)
        assertEquals(items, chunks.flatten())
    }
}
