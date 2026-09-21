package com.opiconchanger.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.opiconchanger.R

object TemplateDialogs {

    fun promptName(
        activity: AppCompatActivity,
        title: String,
        defaultName: String,
        onName: (String) -> Unit
    ) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(defaultName)
            setSelection(text.length)
        }
        val pad = (activity.resources.displayMetrics.density * 20).toInt()
        val container = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(activity, R.string.template_name_empty, Toast.LENGTH_SHORT).show()
                } else {
                    onName(name)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    fun confirm(context: Context, title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.btn_confirm) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
