package com.opiconchanger.model

/**
 * Icon Pack 中一条 appfilter 映射条目
 *
 * @param component 目标应用的 ComponentInfo 字符串，格式: "com.pkg/.Activity"
 * @param packageName 目标应用包名
 * @param drawableName 图标在 icon pack 中的 drawable 资源名
 * @param appLabel 应用显示名称（由外部查询填充，初始为空）
 */
data class AppFilterEntry(
    val component: String,
    val packageName: String,
    val drawableName: String,
    var appLabel: String = ""
)

/**
 * 搜索展示用的图标条目
 *
 * @param drawableName 图标 drawable 名称
 * @param packageName 关联的 app 包名（可能为空）
 * @param appLabel app 显示名
 * @param iconPackPackage icon pack 包名
 */
data class IconEntry(
    val drawableName: String,
    val packageName: String = "",
    val appLabel: String = "",
    val iconPackPackage: String = ""
)
