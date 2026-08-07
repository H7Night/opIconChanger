package com.opiconchanger.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconApplierTest {

    @Test
    fun cfgTextContainsBothKeys() {
        val cfg = IconApplier.buildCfgText("app.lawnchair.lawnicons", "activobank")
        assertTrue(cfg.contains("chosse_icon_pack_name=app.lawnchair.lawnicons"))
        assertTrue(cfg.contains("choose_drawable_res_name=activobank"))
    }

    @Test
    fun cfgTextHasCommentHeader() {
        val cfg = IconApplier.buildCfgText("app.lawnchair.lawnicons", "activobank")
        assertTrue(cfg.startsWith("#Icon Configuration"))
    }

    @Test
    fun cfgTextEscapesValuesProperly() {
        val cfg = IconApplier.buildCfgText("com.example.pack", "ic_bank:gold")
        assertTrue(cfg.contains("chosse_icon_pack_name=com.example.pack"))
        assertTrue(cfg.contains("choose_drawable_res_name=ic_bank\\:gold"))
    }

    @Test
    fun emptyValuesStillProduceStoreFormat() {
        val cfg = IconApplier.buildCfgText("", "")
        assertFalse(cfg.isBlank())
    }
}
