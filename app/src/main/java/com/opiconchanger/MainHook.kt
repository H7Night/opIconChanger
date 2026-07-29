package com.opiconchanger

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

/**
 * LSPosed 模块入口 — 仅用于注册模块，不执行 Hook
 */
@InjectYukiHookWithXposed
object MainHook : IYukiHookXposedInit {

    const val LAUNCHER_PACKAGE = "com.android.launcher"
    const val TAG = "opIconChanger"

    override fun onInit() = configs {
        isDebug = true
        @Suppress("DEPRECATION")
        debugTag = TAG
    }

    override fun onHook() {}
}
