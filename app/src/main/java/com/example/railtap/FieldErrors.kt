package com.example.railtap

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

/** Shows a red error message above a text box. */
fun TextView.showError(message: String) {
    text = message
    visibility = View.VISIBLE
}

/** Hides the error message. */
fun TextView.hideError() {
    visibility = View.GONE
}

/** Hides [error] as soon as the user starts typing in any of these boxes again. */
fun hideErrorWhenTyping(error: TextView, vararg inputs: EditText) {
    inputs.forEach { it.doAfterTextChanged { error.hideError() } }
}
