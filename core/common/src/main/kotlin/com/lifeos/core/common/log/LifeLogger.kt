package com.lifeos.core.common.log

import android.util.Log

/** Thin logging façade so call sites don't depend on android.util.Log directly. */
object LifeLogger {
    private const val ROOT_TAG = "LifeOS"

    fun d(tag: String, message: String) {
        Log.d("$ROOT_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$ROOT_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$ROOT_TAG:$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$ROOT_TAG:$tag", message, throwable)
    }
}
