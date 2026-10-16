package com.example.railtap

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

// base for About / Terms / Privacy: title, back button, insets
abstract class LegalBaseActivity : AppCompatActivity() {

    @get:LayoutRes
    protected abstract val screenLayout: Int
    protected abstract val screenTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(screenLayout)

        findViewById<TextView>(R.id.tvTitle).text = screenTitle
        findViewById<View>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val header = findViewById<View>(R.id.header)
        val scroll = findViewById<View>(R.id.legalScroll)
        val headerTop = header.paddingTop
        val scrollBottom = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(header.parent as View) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = headerTop + bars.top)
            scroll.updatePadding(bottom = scrollBottom + bars.bottom)
            insets
        }
    }
}