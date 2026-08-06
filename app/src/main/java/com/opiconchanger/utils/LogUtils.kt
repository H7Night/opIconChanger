package com.opiconchanger.utils

import android.util.Log

/**
 * 统一日志工具 — 所有日志 tag = opIconChanger
 */
object LogUtils {
    const val TAG = "opIconChanger"

    fun d(msg: String) = Log.d(TAG, msg)
    fun i(msg: String) = Log.i(TAG, msg)
    fun w(msg: String) = Log.w(TAG, msg)
    fun e(msg: String, tr: Throwable? = null) = Log.e(TAG, msg, tr)
}
