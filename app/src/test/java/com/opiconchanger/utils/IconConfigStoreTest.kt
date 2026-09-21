package com.opiconchanger.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconConfigStoreTest {

    @Test
    fun parseCfgReadsBothKeys() {
        val cfg = IconApplier.buildCfgText("app.lawnchair.lawnicons", "activobank")
        assertEquals(
            IconConfig("app.lawnchair.lawnicons", "activobank"),
            IconConfigStore.parseCfg(cfg)
        )
    }

    @Test
    fun parseCfgRejectsMissingField() {
        assertNull(IconConfigStore.parseCfg("chosse_icon_pack_name=com.pack\n"))
        assertNull(IconConfigStore.parseCfg("choose_drawable_res_name=foo\n"))
    }

    @Test
    fun parseCfgRejectsBlankText() {
        assertNull(IconConfigStore.parseCfg(""))
    }

    @Test
    fun parseCfgRejectsInvalidNames() {
        assertNull(IconConfigStore.parseCfg("chosse_icon_pack_name=../evil\nchoose_drawable_res_name=foo\n"))
        assertNull(IconConfigStore.parseCfg("chosse_icon_pack_name=com.pack\nchoose_drawable_res_name=../evil\n"))
        assertNull(IconConfigStore.parseCfg("chosse_icon_pack_name=com.pack\nchoose_drawable_res_name=has space\n"))
    }
}
