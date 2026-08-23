package com.bee.gameplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(18, 18, 24))
        }

        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        })

        fun gameButton(label: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                textSize = 20f
                setOnClickListener { onClick() }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 12) })
        }

        gameButton("Brick Breaker") {
            startActivity(Intent(this, com.bee.brickbreaker.MainActivity::class.java))
        }
        gameButton("Flowit") {
            startActivity(Intent(this, com.bytehamster.flowitgame.Main::class.java))
        }
        gameButton("Arrow Escape") {
            startActivity(WebGameActivity.intent(this, "Arrow Escape", "arrowescape/index.html"))
        }
        gameButton("Idle Brick Breaker") {
            startActivity(WebGameActivity.intent(this, "Idle Brick Breaker", "idlebrickbreaker/index.html"))
        }

        setContentView(layout)
    }
}
