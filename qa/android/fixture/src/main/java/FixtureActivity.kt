package org.chimahon.qa.fixture

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class FixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff123456.toInt())
            setOnClickListener { Log.i("ScrollProbe", "APP_CLICK") }
        }
        repeat(80) { index ->
            content.addView(TextView(this).apply {
                text = "原文 $index"
                textSize = 24f
                setTextColor(Color.WHITE)
                setPadding(20, 20, 20, 20)
            }, LinearLayout.LayoutParams(-1, 130))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xff123456.toInt())
            addView(content)
            setOnScrollChangeListener { _, _, y, _, oldY ->
                if (y != oldY) Log.i("ScrollProbe", "SCROLL $y")
            }
        }
        setContentView(scroll)
        Log.i("ScrollProbe", "APP_READY")
    }
}
