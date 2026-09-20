package com.bee.gameplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.bee.arcade.ArcadeGames
import com.bee.arcade.BaseGameView

/**
 * Hosts a single Tiny Arcade game (Snake, 2048, Flappy, Brick Break,
 * Whac-a-mole, Memory) fullscreen. The game's own home button and the system
 * back both return to the launcher. Scores persist under the "arcade" prefs
 * file, same as the standalone TinyArcade app.
 */
class TinyGameActivity : Activity() {

    private var game: BaseGameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w = window
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val id = intent.getIntExtra(EXTRA_GAME, ArcadeGames.SNAKE)
        game = ArcadeGames.create(id, this) { finish() } as BaseGameView
        setContentView(game)
        hideSystemUI()
    }

    override fun onBackPressed() {
        // Game chrome handles its own home; back always leaves to the launcher.
        finish()
    }

    override fun onPause() {
        super.onPause()
        game?.activityPause()
    }

    override fun onResume() {
        super.onResume()
        game?.activityResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { c ->
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    companion object {
        private const val EXTRA_GAME = "game"

        fun intent(context: Context, gameId: Int) =
            Intent(context, TinyGameActivity::class.java).putExtra(EXTRA_GAME, gameId)
    }
}