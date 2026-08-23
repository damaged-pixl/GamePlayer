package com.bee.gameplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.flutter.embedding.android.FlutterActivity

data class Game(
    val name: String,
    val description: String,
    val emoji: String,
    val color: Int,
    val launch: ((Context) -> Intent)? = null,
)

class MainActivity : Activity() {

    private val games = listOf(
        Game("Brick Breaker", "Smash waves of bricks with a bouncing paddle", "🧱", Color.rgb(231, 76, 60)) {
            Intent(this, com.bee.brickbreaker.MainActivity::class.java)
        },
        Game("Flowit", "Flow colors across the grid to fill every box", "🌊", Color.rgb(52, 152, 219)) {
            Intent(this, com.bytehamster.flowitgame.Main::class.java)
        },
        Game("Arrow Escape", "Slide every arrow off the board without a crash", "➡️", Color.rgb(155, 89, 182)) {
            FlutterActivity.createDefaultIntent(this)
        },
        Game(
            "Idle Brick Breaker",
            "Unavailable — the original TechTreeGames game is closed-source and no faithful open-source clone exists",
            "🚫",
            Color.rgb(90, 90, 100),
        ),
        Game("Anuto TD", "Hand-drawn tower defense with devious waves", "🗼", Color.rgb(39, 174, 96)) {
            Intent(this, ch.logixisland.anuto.view.game.GameActivity::class.java)
        },
        Game("2048", "Swipe and merge tiles all the way to 2048", "🔢", Color.rgb(243, 156, 18)) {
            WebGameActivity.intent(this, "2048", "game2048/index.html")
        },
        Game("Minesweeper", "Clear the field, flag the mines. Long-press to flag", "💣", Color.rgb(127, 140, 141)) {
            WebGameActivity.intent(this, "Minesweeper", "minesweeper/index.html")
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = BG

        val adapter = GameAdapter()

        val search = EditText(this).apply {
            hint = "Search games"
            setHintTextColor(TEXT_DIM)
            setTextColor(TEXT)
            setSingleLine()
            background = rounded(CARD, dp(24))
            setPadding(dp(20), dp(12), dp(20), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = adapter.filter(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TEXT)
        }

        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            this.adapter = adapter
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            fitsSystemWindows = true
            setPadding(dp(16), dp(24), dp(16), 0)
            addView(title, lp(bottom = dp(16)))
            addView(search, lp(bottom = dp(16)))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private inner class GameAdapter : RecyclerView.Adapter<GameAdapter.Holder>() {

        private var shown = games

        fun filter(query: String) {
            shown = if (query.isBlank()) games
            else games.filter {
                it.name.contains(query, true) || it.description.contains(query, true)
            }
            notifyDataSetChanged()
        }

        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val icon = TextView(this@MainActivity).apply {
                textSize = 26f
                gravity = Gravity.CENTER
            }
            val iconTile = FrameLayout(this@MainActivity).apply {
                addView(icon, FrameLayout.LayoutParams(-1, -1))
            }
            val name = TextView(this@MainActivity).apply {
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
            }
            val desc = TextView(this@MainActivity).apply {
                textSize = 12f
                setTextColor(TEXT_DIM)
                maxLines = 3
            }
            val column = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(iconTile, LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(name, lp(top = dp(12)))
                addView(desc, lp(top = dp(4)))
            }
            val card = CardView(this@MainActivity).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(CARD)
                layoutParams = ViewGroup.MarginLayoutParams(-1, -2).apply {
                    val m = dp(6)
                    setMargins(m, m, m, m)
                }
                addView(column)
            }
            return Holder(card, icon, iconTile, name, desc)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val game = shown[position]
            holder.icon.text = game.emoji
            holder.iconTile.background = rounded(game.color, dp(14))
            holder.name.text = game.name
            holder.desc.text = game.description
            holder.card.alpha = if (game.launch == null) 0.5f else 1f
            holder.card.setOnClickListener {
                game.launch?.let { startActivity(it(this@MainActivity)) }
            }
        }

        private inner class Holder(
            val card: CardView,
            val icon: TextView,
            val iconTile: FrameLayout,
            val name: TextView,
            val desc: TextView,
        ) : RecyclerView.ViewHolder(card)
    }

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = top
            bottomMargin = bottom
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
    }

    companion object {
        private val BG = Color.rgb(18, 18, 24)
        private val CARD = Color.rgb(30, 30, 42)
        private val TEXT = Color.rgb(240, 240, 245)
        private val TEXT_DIM = Color.rgb(150, 150, 165)
    }
}
