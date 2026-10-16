package com.example.railtap

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged

// show red error text
fun TextView.showError(message: String) {
    text = message
    visibility = View.VISIBLE
}

// hide it
fun TextView.hideError() {
    visibility = View.GONE
}

// hide the error once they type again
fun hideErrorWhenTyping(error: TextView, vararg inputs: EditText) {
    inputs.forEach { it.doAfterTextChanged { error.hideError() } }
}
