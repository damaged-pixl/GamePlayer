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
import com.bee.arcade.ArcadeGames

data class Game(
    val name: String,
    val description: String,
    val emoji: String,
    val color: Int,
    val launch: ((Context) -> Intent)? = null,
)

class MainActivity : Activity() {

    private val games = listOf(
        // Tiny Arcade — the primary set (com.bee.arcade, :arcade module)
        Game("Snake", "Grid + swipe, wrap edges, glide between ticks", "🐍", Color.rgb(61, 226, 79)) {
            TinyGameActivity.intent(this, ArcadeGames.SNAKE)
        },
        Game("2048", "Swipe and merge tiles all the way to 2048", "🔢", Color.rgb(237, 194, 46)) {
            TinyGameActivity.intent(this, ArcadeGames.T2048)
        },
        Game("Flappy", "One-tap flap, constant pipe speed", "🐤", Color.rgb(255, 225, 90)) {
            TinyGameActivity.intent(this, ArcadeGames.FLAPPY)
        },
        Game("Brick Break", "Classic brick breaker, hand-built levels", "🧱", Color.rgb(79, 195, 247)) {
            TinyGameActivity.intent(this, ArcadeGames.BRICK)
        },
        Game("Whac-a-mole", "30 s arcade timer, combo chains", "🔨", Color.rgb(255, 158, 46)) {
            TinyGameActivity.intent(this, ArcadeGames.WHAC)
        },
        Game("Memory", "4×4 pairs, lowest move count wins", "🃏", Color.rgb(156, 107, 255)) {
            TinyGameActivity.intent(this, ArcadeGames.MEMORY)
        },
        Game("Flood Fill", "Tap tools to fill empty cells and match the pattern", "🌊", Color.rgb(76, 175, 80)) {
            TinyGameActivity.intent(this, ArcadeGames.FLOOD_FILL)
        },
        Game("Arrow Slide", "Slide every arrow off the board without a crash", "➡️", Color.rgb(61, 169, 226)) {
            TinyGameActivity.intent(this, ArcadeGames.ARROW_SLIDE)
        },
        Game("Idle Bricks", "Auto-battle bricks, tap for damage, buy upgrades", "🧱", Color.rgb(255, 176, 32)) {
            TinyGameActivity.intent(this, ArcadeGames.IDLE_BRICKS)
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
