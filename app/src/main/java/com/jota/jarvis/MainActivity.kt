package com.jota.jarvis

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "JARVIS online"
            textSize = 24f
            setPadding(48, 96, 48, 48)
        }

        setContentView(tv)
    }
}