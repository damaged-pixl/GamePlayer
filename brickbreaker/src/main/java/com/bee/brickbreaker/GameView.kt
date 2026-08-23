package com.bee.brickbreaker

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import kotlin.math.*

// ─── Logical canvas size (portrait phone) ─────────────────────────────────────
private const val LW = 390f
private const val LH = 700f

// ─── Layout ───────────────────────────────────────────────────────────────────
private const val PADDLE_H    = 12f
private const val PADDLE_W    = 80f
private const val PADDLE_WIDE = 140f
private const val BALL_R      = 8f
private const val COLS        = 8
private const val BW          = 42f
private const val BH          = 18f
private const val BGAP        = 3f
private val   BOX             = (LW - (COLS * (BW + BGAP) - BGAP)) / 2f
private const val BOY         = 90f

// ─── Physics ──────────────────────────────────────────────────────────────────
private const val BASE_SPEED  = 340f
private const val LASER_SPD   = 720f
private const val PU_SPD      = 120f
private const val P_GRAV      = 280f
private const val MAX_TRAIL   = 10
private const val MAX_COMBO   = 6
// Paddle hit-angle physics (Breakout-71-style intentional aiming)
private const val MAX_BOUNCE_ANGLE = 62f   // degrees from vertical at paddle edge
private const val MIN_VY_FRAC      = 0.34f  // floor on |vy|/speed so the ball never stalls horizontally
private const val PADDLE_ENGLISH   = 0.16f  // how much paddle motion nudges ball vx (spin/english)
private const val SCOREPOP_LIFE    = 0.85f  // seconds a score popup lives
private const val SHIELD_Y    = LH - 38f
private const val SHIELD_BAND = 8f
private val PU_TYPES  = listOf("wide","multi","laser","slow","fireball","shield")
private val PU_DUR    = mapOf("wide" to 8f, "laser" to 5f, "fireball" to 3.4f, "shield" to 6.5f)

// ─── Data models ─────────────────────────────────────────────────────────────

data class Ball(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var active: Boolean = true,
    val trail: ArrayDeque<PointF> = ArrayDeque(MAX_TRAIL + 1),
    var hue: Float = 0f,
    val r: Float = BALL_R,
)

data class Brick(
    val x: Float, val y: Float,
    val w: Float = BW, val h: Float = BH,
    var hp: Int, val maxHp: Int,
    val row: Int, val col: Int,
    val special: String? = null,
    var alive: Boolean = true,
    var shake: Float = 0f,
)

data class Paddle(
    var x: Float, var targetX: Float,
    val y: Float = LH - 50f,
    var w: Float = PADDLE_W,
    val h: Float = PADDLE_H,
    var vx: Float = 0f,        // smoothed horizontal velocity (for ball "english"/spin)
    var flash: Float = 0f,     // brief lit flash on ball contact (juice)
)

data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float = 1f,
    val color: Int,
    var r: Float,
)

data class Powerup(
    var x: Float, var y: Float,
    val type: String,
    var rot: Float = 0f,
)

data class Laser(var x: Float, var y: Float, var dead: Boolean = false)

// Floating "+N" score popup that rises and fades on brick destruction (juice)
data class ScorePop(var x: Float, var y: Float, val text: String, val color: Int, var life: Float = SCOREPOP_LIFE)

data class Fx(
    var wide: Float = 0f, var laser: Float = 0f,
    var fireball: Float = 0f, var shield: Float = 0f,
)

data class DiffCfg(val mul: Float, val lives: Int, val pcap: Int, val lchance: Float)

val DIFF_MAP = mapOf(
    "easy"   to DiffCfg(0.80f, 5, 140, 0.07f),
    "normal" to DiffCfg(1.00f, 3, 125, 0.09f),
    "hard"   to DiffCfg(1.28f, 2, 110, 0.11f),
)

data class GameState(
    val paddle: Paddle,
    val balls: MutableList<Ball>,
    val bricks: MutableList<Brick>,
    val powerups: MutableList<Powerup> = mutableListOf(),
    val lasers: MutableList<Laser>    = mutableListOf(),
    val particles: MutableList<Particle> = mutableListOf(),
    val scorePops: MutableList<ScorePop> = mutableListOf(),
    var flash: Float = 0f,   // full-screen color flash intensity (juice), decays fast
    var score: Int = 0,
    var lives: Int,
    val li: Int,
    var combo: Int = 0,
    var comboTimer: Float = 0f,
    var ballSpeed: Float,
    var launched: Boolean = false,
    var aimX: Float = LW / 2f,
    var screenShake: Float = 0f,
    var paused: Boolean = false,
    val fx: Fx = Fx(),
    val cfg: DiffCfg,
)

// ─── Themes ──────────────────────────────────────────────────────────────────

data class Theme(
    val bg: Int, val paddle: Int, val ball: Int,
    val text: Int, val accent: Int,
    val brickPalette: List<Int>,
)

val THEMES: Map<String, Theme> = mapOf(
    "neon" to Theme(
        bg     = Color.parseColor("#0a0a1a"),
        paddle = Color.parseColor("#00f0ff"),
        ball   = Color.WHITE,
        text   = Color.parseColor("#00f0ff"),
        accent = Color.parseColor("#ff00ff"),
        brickPalette = listOf("#ff006e","#fb5607","#ffbe0b","#8338ec","#3a86ff","#06d6a0","#ff006e").map { Color.parseColor(it) }
    ),
    "retro" to Theme(
        bg     = Color.parseColor("#1a0a00"),
        paddle = Color.parseColor("#ff8c00"),
        ball   = Color.parseColor("#ffd700"),
        text   = Color.parseColor("#ff8c00"),
        accent = Color.parseColor("#ff4500"),
        brickPalette = listOf("#ff4500","#ff6347","#ffa500","#ffd700","#adff2f","#00ced1","#da70d6").map { Color.parseColor(it) }
    ),
    "ice" to Theme(
        bg     = Color.parseColor("#0d1b2a"),
        paddle = Color.parseColor("#a8dadc"),
        ball   = Color.parseColor("#e8f4f8"),
        text   = Color.parseColor("#a8dadc"),
        accent = Color.parseColor("#457b9d"),
        brickPalette = listOf("#caf0f8","#90e0ef","#00b4d8","#0077b6","#023e8a","#48cae4","#ade8f4").map { Color.parseColor(it) }
    ),
    "fire" to Theme(
        bg     = Color.parseColor("#1a0000"),
        paddle = Color.parseColor("#ff4444"),
        ball   = Color.parseColor("#ffdd00"),
        text   = Color.parseColor("#ff6666"),
        accent = Color.parseColor("#ff8800"),
        brickPalette = listOf("#ff0000","#ff3300","#ff6600","#ff9900","#ffcc00","#ff4444","#ff2200").map { Color.parseColor(it) }
    ),
    "mono" to Theme(
        bg     = Color.parseColor("#0f0f0f"),
        paddle = Color.WHITE,
        ball   = Color.WHITE,
        text   = Color.parseColor("#cccccc"),
        accent = Color.WHITE,
        brickPalette = listOf("#ffffff","#cccccc","#aaaaaa","#888888","#666666","#444444","#ffffff").map { Color.parseColor(it) }
    ),
    "forest" to Theme(
        bg     = Color.parseColor("#0a1a0a"),
        paddle = Color.parseColor("#44ff88"),
        ball   = Color.parseColor("#ccffcc"),
        text   = Color.parseColor("#44ff88"),
        accent = Color.parseColor("#ffaa00"),
        brickPalette = listOf("#00ff44","#44ff88","#88ffaa","#ffaa00","#ff6600","#44ff88","#00ff44").map { Color.parseColor(it) }
    ),
)

// ─── Level data ───────────────────────────────────────────────────────────────

data class LevelDef(val sc: Float, val grid: Array<IntArray>)

@Suppress("ArrayInDataClass")
val LEVELS: Array<LevelDef> = arrayOf(
    LevelDef(0.08f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.09f, arrayOf(intArrayOf(1,0,1,0,1,0,1,0),intArrayOf(0,1,0,1,0,1,0,1),intArrayOf(1,0,1,0,1,0,1,0),intArrayOf(0,1,0,1,0,1,0,1),intArrayOf(1,0,1,0,1,0,1,0))),
    LevelDef(0.09f, arrayOf(intArrayOf(0,0,0,1,1,0,0,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,0,0,1,1,0,0,0))),
    LevelDef(0.10f, arrayOf(intArrayOf(0,0,0,1,1,0,0,0),intArrayOf(0,0,1,0,0,1,0,0),intArrayOf(0,1,0,0,0,0,1,0),intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(0,1,0,0,0,0,1,0),intArrayOf(0,0,1,0,0,1,0,0),intArrayOf(0,0,0,1,1,0,0,0))),
    LevelDef(0.10f, arrayOf(intArrayOf(1,1,1,0,0,1,1,1),intArrayOf(1,1,1,0,0,1,1,1),intArrayOf(1,1,1,0,0,1,1,1),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(1,1,1,0,0,1,1,1),intArrayOf(1,1,1,0,0,1,1,1))),
    LevelDef(0.10f, arrayOf(intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(1,1,0,0,0,0,1,1),intArrayOf(0,1,1,0,0,1,1,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,0,0,1,1,0,0,0))),
    LevelDef(0.11f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.11f, arrayOf(intArrayOf(1,1,0,0,1,1,0,0),intArrayOf(1,1,0,0,1,1,0,0),intArrayOf(1,1,0,0,1,1,0,0),intArrayOf(1,1,0,0,1,1,0,0),intArrayOf(1,1,0,0,1,1,0,0))),
    LevelDef(0.11f, arrayOf(intArrayOf(0,0,0,1,1,0,0,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.12f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,0,0,1,1,0,0,0))),
    LevelDef(0.12f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.12f, arrayOf(intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(0,1,0,0,0,0,1,0),intArrayOf(0,0,1,0,0,1,0,0),intArrayOf(0,0,0,1,1,0,0,0),intArrayOf(0,0,1,0,0,1,0,0),intArrayOf(0,1,0,0,0,0,1,0),intArrayOf(1,0,0,0,0,0,0,1))),
    LevelDef(0.12f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,0,0,1,1,0,0,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.13f, arrayOf(intArrayOf(1,1,1,0,0,1,1,1),intArrayOf(1,1,0,0,0,0,1,1),intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(1,1,0,0,0,0,1,1),intArrayOf(1,1,1,0,0,1,1,1))),
    LevelDef(0.13f, arrayOf(intArrayOf(1,0,0,0,0,0,0,0),intArrayOf(1,1,0,0,0,0,0,0),intArrayOf(1,1,1,0,0,0,0,0),intArrayOf(1,1,1,1,0,0,0,0),intArrayOf(1,1,1,1,1,0,0,0),intArrayOf(1,1,1,1,1,1,0,0),intArrayOf(1,1,1,1,1,1,1,0))),
    LevelDef(0.13f, arrayOf(intArrayOf(0,0,0,0,0,0,0,1),intArrayOf(0,0,0,0,0,0,1,1),intArrayOf(0,0,0,0,0,1,1,1),intArrayOf(0,0,0,0,1,1,1,1),intArrayOf(0,0,0,1,1,1,1,1),intArrayOf(0,0,1,1,1,1,1,1),intArrayOf(0,1,1,1,1,1,1,1))),
    LevelDef(0.13f, arrayOf(intArrayOf(0,1,1,0,0,1,1,0),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,0,0,1,1,0,0,0))),
    LevelDef(0.14f, arrayOf(intArrayOf(1,1,0,0,0,0,0,0),intArrayOf(0,1,1,0,0,0,0,0),intArrayOf(0,0,1,1,0,0,0,0),intArrayOf(0,0,0,1,1,0,0,0),intArrayOf(0,0,0,0,1,1,0,0),intArrayOf(0,0,0,0,0,1,1,0),intArrayOf(0,0,0,0,0,0,1,1))),
    LevelDef(0.14f, arrayOf(intArrayOf(1,0,1,0,1,0,1,0),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(0,1,0,1,0,1,0,1),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(1,0,1,0,1,0,1,0),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(0,1,0,1,0,1,0,1))),
    LevelDef(0.14f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,0,0,0,0,0,0,2),intArrayOf(2,0,1,1,1,1,0,2),intArrayOf(2,0,1,0,0,1,0,2),intArrayOf(2,0,1,1,1,1,0,2),intArrayOf(2,0,0,0,0,0,0,2),intArrayOf(2,2,2,2,2,2,2,2))),
    LevelDef(0.14f, arrayOf(intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,2,1,1,1,1,2,0),intArrayOf(2,1,1,0,0,1,1,2),intArrayOf(2,1,0,0,0,0,1,2),intArrayOf(2,2,1,1,1,1,2,2))),
    LevelDef(0.14f, arrayOf(intArrayOf(1,2,3,0,0,0,0,0),intArrayOf(0,1,2,3,0,0,0,0),intArrayOf(0,0,1,2,3,0,0,0),intArrayOf(0,0,0,1,2,3,0,0),intArrayOf(0,0,0,0,1,2,3,0),intArrayOf(0,0,0,0,0,1,2,3))),
    LevelDef(0.15f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,2,2,2,2,2,2,3),intArrayOf(3,2,1,1,1,1,2,3),intArrayOf(3,2,1,0,0,1,2,3),intArrayOf(3,2,1,1,1,1,2,3),intArrayOf(3,2,2,2,2,2,2,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.15f, arrayOf(intArrayOf(1,2,3,4,4,3,2,1),intArrayOf(1,2,3,4,4,3,2,1),intArrayOf(1,2,3,4,4,3,2,1),intArrayOf(1,2,3,4,4,3,2,1))),
    LevelDef(0.15f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.15f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,0,0,2,0,0,0))),
    LevelDef(0.15f, arrayOf(intArrayOf(1,0,0,0,0,0,0,1),intArrayOf(1,1,0,0,0,0,1,1),intArrayOf(1,1,1,0,0,1,1,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.16f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,3,1,3,1,3,1,3),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(3,1,3,1,3,1,3,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.16f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,0,2,2,0,0,0))),
    LevelDef(0.16f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,0,0,2,0,0,0,2),intArrayOf(2,0,2,2,2,2,0,2),intArrayOf(2,0,0,0,0,2,0,2),intArrayOf(2,2,2,0,2,2,0,2),intArrayOf(2,0,0,0,0,0,0,2),intArrayOf(2,2,2,2,2,2,2,2))),
    LevelDef(0.16f, arrayOf(intArrayOf(0,0,1,0,0,0,1,0),intArrayOf(0,0,1,0,1,0,1,0),intArrayOf(0,1,1,0,1,0,1,1),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.16f, arrayOf(intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,0,1,1,0,2,2),intArrayOf(2,2,1,1,1,1,2,2),intArrayOf(2,2,1,1,1,1,2,2),intArrayOf(2,2,1,1,1,1,2,2))),
    LevelDef(0.16f, arrayOf(intArrayOf(0,2,0,2,0,2,0,2),intArrayOf(2,0,2,0,2,0,2,0),intArrayOf(0,2,0,2,0,2,0,2),intArrayOf(2,0,2,0,2,0,2,0),intArrayOf(0,2,0,2,0,2,0,2))),
    LevelDef(0.17f, arrayOf(intArrayOf(0,0,1,1,1,1,1,1),intArrayOf(0,0,1,1,0,0,0,0),intArrayOf(0,0,1,1,1,1,0,0),intArrayOf(0,0,0,0,1,1,0,0),intArrayOf(1,1,1,1,1,1,0,0),intArrayOf(1,1,0,0,0,0,0,0))),
    LevelDef(0.17f, arrayOf(intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,2,2,3,3,2,2,0),intArrayOf(2,2,3,3,3,3,2,2),intArrayOf(1,2,2,2,2,2,2,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.17f, arrayOf(intArrayOf(2,2,0,2,2,0,2,2),intArrayOf(2,2,0,2,2,0,2,2),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(2,2,0,2,2,0,2,2),intArrayOf(2,2,0,2,2,0,2,2))),
    LevelDef(0.17f, arrayOf(intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,0,2,2,2,2,0,0))),
    LevelDef(0.17f, arrayOf(intArrayOf(1,0,3,0,1,0,3,0),intArrayOf(0,1,0,3,0,1,0,3),intArrayOf(3,0,1,0,3,0,1,0),intArrayOf(0,3,0,1,0,3,0,1),intArrayOf(1,0,3,0,1,0,3,0))),
    LevelDef(0.17f, arrayOf(intArrayOf(0,0,0,2,2,2,2,0),intArrayOf(0,0,2,2,2,0,0,0),intArrayOf(0,2,2,2,0,0,0,0),intArrayOf(0,0,0,2,2,2,0,0),intArrayOf(0,0,0,0,0,2,2,0),intArrayOf(0,0,0,0,0,0,2,2))),
    LevelDef(0.17f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,1,1,1,1,1,1,3),intArrayOf(3,1,0,0,0,0,1,3),intArrayOf(3,1,0,0,0,0,1,3),intArrayOf(3,1,1,1,1,1,1,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.17f, arrayOf(intArrayOf(0,1,0,1,1,0,1,0),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,1,0,1,1,0,1,0),intArrayOf(0,1,0,1,1,0,1,0),intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(0,1,0,1,1,0,1,0))),
    LevelDef(0.17f, arrayOf(intArrayOf(1,1,1,1,1,1,1,0),intArrayOf(0,0,0,0,0,0,1,0),intArrayOf(0,1,1,1,1,1,1,0),intArrayOf(0,1,0,0,0,0,0,0),intArrayOf(0,1,1,1,1,1,1,1),intArrayOf(0,0,0,0,0,0,0,1))),
    LevelDef(0.18f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,1,1,1,1,1,1,2),intArrayOf(2,1,2,2,2,2,1,2),intArrayOf(2,1,2,1,1,2,1,2),intArrayOf(2,1,2,2,2,2,1,2),intArrayOf(2,1,1,1,1,1,1,2),intArrayOf(2,2,2,2,2,2,2,2))),
    LevelDef(0.18f, arrayOf(intArrayOf(1,1,1,1,2,2,2,2),intArrayOf(1,1,1,1,2,2,2,2),intArrayOf(1,1,1,1,2,2,2,2),intArrayOf(3,3,3,3,4,4,4,4),intArrayOf(3,3,3,3,4,4,4,4),intArrayOf(3,3,3,3,4,4,4,4))),
    LevelDef(0.18f, arrayOf(intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,2,2,2,2,2,2,0),intArrayOf(2,2,0,2,2,0,2,2),intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,0,2,2,0,0,0))),
    LevelDef(0.18f, arrayOf(intArrayOf(2,0,2,0,2,0,2,0),intArrayOf(0,2,0,2,0,2,0,2),intArrayOf(2,0,2,0,2,0,2,0),intArrayOf(0,2,0,2,0,2,0,2),intArrayOf(2,0,2,0,2,0,2,0),intArrayOf(0,2,0,2,0,2,0,2))),
    LevelDef(0.18f, arrayOf(intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,0,0,0,0,2,2))),
    LevelDef(0.18f, arrayOf(intArrayOf(2,0,0,0,0,0,0,2),intArrayOf(2,0,2,0,0,2,0,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,0,0,0,0,0,0,2),intArrayOf(2,0,2,2,2,2,0,2),intArrayOf(2,0,0,0,0,0,0,2))),
    LevelDef(0.18f, arrayOf(intArrayOf(2,0,0,0,0,0,0,0),intArrayOf(2,2,0,0,0,0,0,0),intArrayOf(2,2,2,0,0,0,0,0),intArrayOf(2,2,2,2,0,0,0,0),intArrayOf(2,2,2,2,2,0,0,0),intArrayOf(2,2,2,2,2,2,0,0),intArrayOf(2,2,2,2,2,2,2,0))),
    LevelDef(0.18f, arrayOf(intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(0,2,0,0,0,0,2,0),intArrayOf(0,2,2,0,0,2,2,0))),
    LevelDef(0.19f, arrayOf(intArrayOf(1,1,1,1,1,1,1,1),intArrayOf(1,0,0,3,3,0,0,1),intArrayOf(1,0,3,3,3,3,0,1),intArrayOf(1,3,3,3,3,3,3,1),intArrayOf(1,0,3,3,3,3,0,1),intArrayOf(1,0,0,3,3,0,0,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.18f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,0,0,0,0,0,0,0),intArrayOf(3,0,0,0,0,0,0,0),intArrayOf(3,0,2,2,2,2,0,0),intArrayOf(3,0,0,0,0,0,0,0),intArrayOf(3,3,3,3,3,0,0,0))),
    LevelDef(0.18f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,0,0,0,0,0,0,3),intArrayOf(3,0,2,2,2,2,0,3),intArrayOf(3,0,2,0,0,2,0,3),intArrayOf(3,0,2,2,2,2,0,3),intArrayOf(3,0,0,0,0,0,0,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.19f, arrayOf(intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,2,2,3,3,2,2,0),intArrayOf(2,2,3,3,3,3,2,2),intArrayOf(1,2,2,2,2,2,2,1),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.19f, arrayOf(intArrayOf(3,1,3,1,3,1,3,1),intArrayOf(3,1,3,1,3,1,3,1),intArrayOf(3,1,3,1,3,1,3,1),intArrayOf(3,1,3,1,3,1,3,1),intArrayOf(3,1,3,1,3,1,3,1))),
    LevelDef(0.19f, arrayOf(intArrayOf(3,0,3,0,3,0,3,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(2,2,2,2,2,2,2,2))),
    LevelDef(0.19f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,0,0,0,0,0,0,0),intArrayOf(2,0,1,1,1,1,1,0),intArrayOf(2,0,0,0,0,0,1,0),intArrayOf(2,0,1,1,1,0,1,0),intArrayOf(2,0,1,0,0,0,0,0),intArrayOf(2,2,2,2,2,2,2,2))),
    LevelDef(0.19f, arrayOf(intArrayOf(0,0,0,3,3,0,0,0),intArrayOf(0,0,3,0,0,3,0,0),intArrayOf(0,3,0,0,0,0,3,0),intArrayOf(3,0,0,0,0,0,0,3),intArrayOf(0,3,0,0,0,0,3,0),intArrayOf(0,0,3,0,0,3,0,0),intArrayOf(0,0,0,3,3,0,0,0))),
    LevelDef(0.19f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.19f, arrayOf(intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,0,2,0,2,0,2,0),intArrayOf(0,0,2,0,2,0,0,0),intArrayOf(0,0,0,0,2,0,0,0))),
    LevelDef(0.20f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,2,2,2,2,2,2,3),intArrayOf(3,2,0,0,0,0,2,3),intArrayOf(3,2,0,4,4,0,2,3),intArrayOf(3,2,0,4,4,0,2,3),intArrayOf(3,2,0,0,0,0,2,3),intArrayOf(3,2,2,2,2,2,2,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.19f, arrayOf(intArrayOf(2,0,0,0,2,0,0,0),intArrayOf(0,2,0,0,0,2,0,0),intArrayOf(0,0,2,0,0,0,2,0),intArrayOf(0,0,0,2,0,0,0,2),intArrayOf(0,0,2,0,0,0,2,0),intArrayOf(0,2,0,0,0,2,0,0),intArrayOf(2,0,0,0,2,0,0,0))),
    LevelDef(0.19f, arrayOf(intArrayOf(4,2,2,2,2,2,2,4),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(4,2,2,2,2,2,2,4))),
    LevelDef(0.20f, arrayOf(intArrayOf(2,2,0,3,3,3,0,2),intArrayOf(2,2,0,0,3,0,0,2),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(2,0,2,2,0,0,2,2),intArrayOf(2,0,2,2,0,0,0,0),intArrayOf(0,0,0,0,0,3,3,3))),
    LevelDef(0.20f, arrayOf(intArrayOf(0,0,2,0,0,2,0,0),intArrayOf(0,0,2,0,0,2,0,0),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(0,0,2,0,0,2,0,0),intArrayOf(0,0,2,0,0,2,0,0),intArrayOf(2,2,2,2,2,2,2,2))),
    LevelDef(0.20f, arrayOf(intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,2,2,2,2,0,0),intArrayOf(0,2,2,0,0,2,2,0),intArrayOf(2,2,0,0,0,0,2,2),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,2,2,2,2,0,0))),
    LevelDef(0.20f, arrayOf(intArrayOf(3,1,2,0,3,1,0,2),intArrayOf(1,0,3,2,0,2,3,1),intArrayOf(0,2,1,3,2,0,1,3),intArrayOf(2,3,0,1,0,3,2,0),intArrayOf(1,0,2,0,1,2,0,3))),
    LevelDef(0.20f, arrayOf(intArrayOf(0,0,0,2,2,0,0,0),intArrayOf(0,0,2,1,1,2,0,0),intArrayOf(0,2,1,3,3,1,2,0),intArrayOf(2,1,3,0,0,3,1,2),intArrayOf(0,2,1,3,3,1,2,0),intArrayOf(0,0,2,1,1,2,0,0),intArrayOf(0,0,0,2,2,0,0,0))),
    LevelDef(0.20f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,0,3,0,3,0,3,0),intArrayOf(3,0,3,0,3,0,3,0),intArrayOf(0,0,3,0,3,0,0,0),intArrayOf(0,0,0,0,3,0,0,0))),
    LevelDef(0.21f, arrayOf(intArrayOf(3,0,3,0,3,0,3,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.21f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,2,2,2,2,2,2,3),intArrayOf(3,2,1,1,1,1,2,3),intArrayOf(3,2,1,4,4,1,2,3),intArrayOf(3,2,1,4,4,1,2,3),intArrayOf(3,2,1,1,1,1,2,3),intArrayOf(3,2,2,2,2,2,2,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.20f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,0,0,3,0,0,0,3),intArrayOf(3,0,3,3,3,3,0,3),intArrayOf(3,0,3,0,0,3,0,3),intArrayOf(3,0,0,0,3,0,0,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.20f, arrayOf(intArrayOf(4,0,4,0,4,0,4,0),intArrayOf(4,1,4,1,4,1,4,1),intArrayOf(4,1,4,1,4,1,4,1),intArrayOf(4,1,4,1,4,1,4,1),intArrayOf(4,0,4,0,4,0,4,0))),
    LevelDef(0.20f, arrayOf(intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,0,0,3,0,0,0,3),intArrayOf(3,0,3,3,3,3,0,3),intArrayOf(3,0,0,0,0,3,0,3),intArrayOf(3,0,0,3,0,0,0,3),intArrayOf(3,3,3,3,3,3,3,3))),
    LevelDef(0.21f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(0,0,0,0,0,0,0,0),intArrayOf(1,1,1,1,1,1,1,1))),
    LevelDef(0.21f, arrayOf(intArrayOf(2,2,4,2,2,4,2,2),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(4,2,2,2,4,2,2,4),intArrayOf(2,2,2,2,2,2,2,2),intArrayOf(2,4,2,2,2,2,4,2))),
    LevelDef(0.21f, arrayOf(intArrayOf(3,2,3,2,3,2,3,2),intArrayOf(2,3,2,3,2,3,2,3),intArrayOf(3,2,3,2,3,2,3,2),intArrayOf(2,3,2,3,2,3,2,3),intArrayOf(3,2,3,2,3,2,3,2))),
    LevelDef(0.21f, arrayOf(intArrayOf(0,0,0,3,3,0,0,0),intArrayOf(0,0,3,3,3,3,0,0),intArrayOf(0,3,3,3,3,3,3,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(0,3,3,3,3,3,3,0),intArrayOf(0,0,3,3,3,3,0,0),intArrayOf(0,0,0,3,3,0,0,0))),
    LevelDef(0.21f, arrayOf(intArrayOf(0,3,3,3,3,3,3,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,2,0,3,3,0,2,3),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(3,0,3,3,3,3,0,3),intArrayOf(0,3,3,0,0,3,3,0))),
    LevelDef(0.21f, arrayOf(intArrayOf(3,3,3,3,3,3,3,0),intArrayOf(3,3,3,3,3,3,0,3),intArrayOf(3,3,3,3,3,0,3,3),intArrayOf(3,3,3,3,0,3,3,3),intArrayOf(3,3,3,0,3,3,3,3),intArrayOf(3,3,0,3,3,3,3,3))),
    LevelDef(0.22f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,3,3,3,3,3,3,4),intArrayOf(4,3,2,2,2,2,3,4),intArrayOf(4,3,2,1,1,2,3,4),intArrayOf(4,3,2,1,1,2,3,4),intArrayOf(4,3,2,2,2,2,3,4),intArrayOf(4,3,3,3,3,3,3,4),intArrayOf(4,4,4,4,4,4,4,4))),
    LevelDef(0.21f, arrayOf(intArrayOf(3,3,3,3,0,0,0,0),intArrayOf(3,0,0,3,3,3,0,0),intArrayOf(3,0,0,3,0,3,3,3),intArrayOf(3,3,0,3,0,0,0,3),intArrayOf(0,3,0,3,3,3,3,3),intArrayOf(0,3,3,3,0,0,0,0))),
    LevelDef(0.21f, arrayOf(intArrayOf(2,2,2,4,4,2,2,2),intArrayOf(2,2,2,4,4,2,2,2),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(2,2,2,4,4,2,2,2),intArrayOf(2,2,2,4,4,2,2,2))),
    LevelDef(0.21f, arrayOf(intArrayOf(4,4,0,0,4,4,0,0),intArrayOf(0,4,4,0,0,4,4,0),intArrayOf(0,0,4,4,0,0,4,4),intArrayOf(4,0,0,4,4,0,0,4),intArrayOf(4,4,0,0,4,4,0,0))),
    LevelDef(0.22f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,0,0,4,0,0,0,4),intArrayOf(4,0,4,4,4,4,0,4),intArrayOf(4,0,0,0,0,4,0,4),intArrayOf(4,4,4,0,4,4,0,4),intArrayOf(0,0,4,0,0,0,0,4),intArrayOf(4,4,4,4,4,4,4,4))),
    LevelDef(0.22f, arrayOf(intArrayOf(4,3,2,1,1,2,3,4),intArrayOf(4,3,2,1,1,2,3,4),intArrayOf(4,3,2,1,1,2,3,4),intArrayOf(4,3,2,1,1,2,3,4),intArrayOf(4,3,2,1,1,2,3,4))),
    LevelDef(0.22f, arrayOf(intArrayOf(0,0,0,3,3,0,0,0),intArrayOf(0,3,0,3,3,0,3,0),intArrayOf(0,0,3,3,3,3,0,0),intArrayOf(3,3,3,3,3,3,3,3),intArrayOf(0,0,3,3,3,3,0,0),intArrayOf(0,3,0,3,3,0,3,0),intArrayOf(0,0,0,3,3,0,0,0))),
    LevelDef(0.22f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(0,0,4,4,4,4,0,0),intArrayOf(0,0,4,0,0,4,0,0),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(0,0,0,4,4,0,0,0))),
    LevelDef(0.22f, arrayOf(intArrayOf(4,0,4,0,4,0,4,0),intArrayOf(4,0,4,0,4,0,4,0),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(0,4,0,4,0,4,0,4),intArrayOf(0,4,0,4,0,4,0,4))),
    LevelDef(0.23f, arrayOf(intArrayOf(4,0,4,4,0,4,4,0),intArrayOf(0,4,4,0,4,4,0,4),intArrayOf(4,4,0,4,4,0,4,4),intArrayOf(4,0,4,0,4,0,4,0),intArrayOf(0,4,0,4,0,4,0,4),intArrayOf(4,4,4,4,4,4,4,4))),
    LevelDef(0.23f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,3,3,3,3,3,3,4),intArrayOf(4,3,2,2,2,2,3,4),intArrayOf(4,3,2,4,4,2,3,4),intArrayOf(4,3,2,4,4,2,3,4),intArrayOf(4,3,2,2,2,2,3,4),intArrayOf(4,3,3,3,3,3,3,4),intArrayOf(4,4,4,4,4,4,4,4))),
    LevelDef(0.23f, arrayOf(intArrayOf(4,4,4,4,0,0,0,0),intArrayOf(0,4,4,4,4,0,0,0),intArrayOf(0,0,4,4,4,4,0,0),intArrayOf(0,0,0,4,4,4,4,0),intArrayOf(0,0,4,4,4,4,0,0),intArrayOf(0,4,4,4,4,0,0,0),intArrayOf(4,4,4,4,0,0,0,0))),
    LevelDef(0.23f, arrayOf(intArrayOf(4,4,4,4,4,4,4,0),intArrayOf(4,4,4,4,4,4,0,4),intArrayOf(4,4,4,4,4,0,4,4),intArrayOf(4,4,4,4,0,4,4,4),intArrayOf(4,4,4,0,4,4,4,4),intArrayOf(4,4,0,4,4,4,4,4),intArrayOf(4,0,4,4,4,4,4,4))),
    LevelDef(0.24f, arrayOf(intArrayOf(4,2,2,2,2,2,2,4),intArrayOf(2,4,2,2,2,2,4,2),intArrayOf(2,2,4,2,2,4,2,2),intArrayOf(2,2,2,4,4,2,2,2),intArrayOf(2,2,4,2,2,4,2,2),intArrayOf(2,4,2,2,2,2,4,2),intArrayOf(4,2,2,2,2,2,2,4))),
    LevelDef(0.24f, arrayOf(intArrayOf(4,0,4,0,4,0,4,0),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(0,4,0,4,0,4,0,4))),
    LevelDef(0.24f, arrayOf(intArrayOf(0,4,4,4,0,0,0,0),intArrayOf(4,0,0,0,4,4,0,0),intArrayOf(4,0,0,4,0,0,4,0),intArrayOf(4,0,0,4,0,0,4,0),intArrayOf(4,0,0,0,4,4,0,0),intArrayOf(0,4,4,4,0,0,0,0))),
    LevelDef(0.24f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,0,0,0,4,0,0,4),intArrayOf(4,0,4,0,4,0,4,4),intArrayOf(4,0,4,0,0,0,0,4),intArrayOf(4,0,4,4,4,4,0,4),intArrayOf(4,0,0,0,0,0,0,4),intArrayOf(4,4,4,4,4,4,4,4))),
    LevelDef(0.25f, arrayOf(intArrayOf(4,4,4,0,4,4,4,4),intArrayOf(4,0,4,4,4,0,4,4),intArrayOf(4,4,0,4,0,4,4,4),intArrayOf(0,4,4,4,4,4,0,4),intArrayOf(4,4,0,4,4,0,4,4),intArrayOf(4,0,4,4,0,4,4,4))),
    LevelDef(0.26f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,0,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4))),
    LevelDef(0.28f, arrayOf(intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4),intArrayOf(4,4,4,4,4,4,4,4))),
)

val MAX_LEVEL = LEVELS.size

// ─── Sound engine ────────────────────────────────────────────────────────────

object SoundEngine {
    var enabled = true
    var volume  = 0.8f
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()

    // Pentatonic scale so brick hits play a pleasant melody (Breakout-71 "each colour is a note").
    private val NOTE_HZ = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66)

    fun play(type: String) {
        if (!enabled) return
        executor.submit { playSync(type, 0) }
    }

    /** Play a brick-hit note pitched by [note] (e.g. brick row/colour index). */
    fun playNote(note: Int) {
        if (!enabled) return
        executor.submit { playSync("note", note) }
    }

    private fun playSync(type: String, note: Int) {
        try {
            val sr = 22050
            val pcm = when (type) {
                "hit"    -> genSquare(440.0, 0.08, sr)
                "note"   -> genPluck(NOTE_HZ[note.mod(NOTE_HZ.size)], 0.10, sr)
                "wall"   -> genSine(220.0, 0.05, sr)
                "paddle" -> genSine(330.0, 0.06, sr)
                "lose"   -> genSaw(120.0, 0.45, sr)
                "powerup"-> genSweep(300.0, 750.0, 0.18, sr)
                "win"    -> genWin(sr)
                else     -> return
            }
            val buf = pcm.size * 2
            val track = AudioTrack(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build(),
                AudioFormat.Builder().setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                buf, AudioTrack.MODE_STATIC, 0
            )
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((pcm.size * 1000L / sr) + 30)
            track.stop(); track.release()
        } catch (_: Exception) {}
    }

    private fun amp(v: Float) = (v * volume * Short.MAX_VALUE).toInt().toShort()
    private fun genSine(f: Double, dur: Double, sr: Int)   = ShortArray((dur*sr).toInt()) { i -> amp((sin(2*PI*f*i/sr)).toFloat()) }
    private fun genSquare(f: Double, dur: Double, sr: Int) = ShortArray((dur*sr).toInt()) { i -> amp(if (sin(2*PI*f*i/sr) >= 0) 0.6f else -0.6f) }
    private fun genSaw(f: Double, dur: Double, sr: Int)    = ShortArray((dur*sr).toInt()) { i -> amp(((2.0*(i*f/sr - floor(i*f/sr + 0.5)))*0.5).toFloat()) }
    // Short plucked tone: sine with a fast exponential decay envelope — clean & musical.
    private fun genPluck(f: Double, dur: Double, sr: Int): ShortArray {
        val n = (dur * sr).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / sr
            val env = exp(-t / (dur * 0.32))
            amp((sin(2 * PI * f * t) * env * 0.7).toFloat())
        }
    }
    private fun genSweep(f0: Double, f1: Double, dur: Double, sr: Int): ShortArray {
        val n = (dur * sr).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / sr
            val f = f0 + (f1 - f0) * (t / dur)
            amp((sin(2 * PI * f * t) * (1.0 - t / dur)).toFloat())
        }
    }
    private fun genWin(sr: Int): ShortArray {
        val freqs = doubleArrayOf(523.0, 659.0, 784.0, 1047.0)
        val seg = sr / 6
        return ShortArray(seg * freqs.size) { i ->
            val fi = i / seg
            val t = (i % seg).toDouble() / sr
            val env = 1.0 - (i % seg).toDouble() / seg
            amp((sin(2 * PI * freqs[fi] * t) * env * 0.6).toFloat())
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun clamp(v: Float, lo: Float, hi: Float) = maxOf(lo, minOf(hi, v))
private fun lerp(a: Float, b: Float, t: Float)     = a + (b - a) * t
private fun colorAlpha(color: Int, alpha: Float) = Color.argb(
    (Color.alpha(color) * alpha).toInt().coerceIn(0, 255),
    Color.red(color), Color.green(color), Color.blue(color)
)

// ─── GameView ─────────────────────────────────────────────────────────────────

class GameView(ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback {

    enum class Screen { MENU, GAME, PAUSE, GAMEOVER, WIN, SCORES, SETTINGS, SHOP, UPGRADE }

    /** A free roguelite perk offered after each (non-shop) level. */
    data class Upgrade(val key: String, val label: String, val desc: String, val icon: String)
    private val UPGRADE_POOL = listOf(
        Upgrade("up_life",      "+1 Life",      "An extra life",             "❤"),
        Upgrade("up_wide",      "Wider Paddle", "Bigger paddle for the run", "🏓"),
        Upgrade("up_multiball", "Multiball",    "Start levels with +1 ball", "⚪"),
        Upgrade("up_slow",      "Slow Ball",    "Calmer, steadier ball",     "🐢"),
        Upgrade("up_fire",      "Fire Start",   "Begin each level on fire",  "🔥"),
        Upgrade("up_coins",     "Coin Cache",   "+25 coins for the shop",    "🪙"),
    )

    // ── State ─────────────────────────────────────────────────────────────────
    private var screen      = Screen.MENU
    private var prevScreen  = Screen.MENU
    private var gs: GameState? = null
    private var scaleX      = 1f
    private var scaleY      = 1f
    private var shopNextLi  = 0
    private var coinsBank   = 0
    // Run-scoped roguelite upgrades (persist across levels within one run, reset on new game).
    private var runPaddleBonus = 0f
    private var runExtraBalls  = 0
    private var runStartFire   = 0f
    private var upgradeChoices: List<Upgrade> = emptyList()
    // Fixed top margin so HUD clears the punch-hole camera on Pixel 9 / similar devices.
    // With system bars properly hidden via WindowInsetsController, this is all that's needed.
    private var topInsetPx = 0
    private var curThemeKey = "neon"
    private var curDiff     = "normal"
    private var hapticOn    = true
    private var particlesOn = true
    private var trailsOn    = true
    private var sfxOn       = true
    private var hiscores    = mutableListOf<Int>()
    private var hasSave     = false

    // ── Prefs ────────────────────────────────────────────────────────────────
    private val prefs: android.content.SharedPreferences =
        ctx.getSharedPreferences("bb", Context.MODE_PRIVATE)

    private fun loadPrefs() {
        curThemeKey = prefs.getString("theme", "neon") ?: "neon"
        curDiff     = prefs.getString("diff", "normal") ?: "normal"
        hapticOn    = prefs.getBoolean("haptic", true)
        particlesOn = prefs.getBoolean("particles", true)
        trailsOn    = prefs.getBoolean("trails", true)
        sfxOn       = prefs.getBoolean("sfx", true).also { SoundEngine.enabled = it }
        SoundEngine.volume = prefs.getFloat("vol", 0.8f)
        val raw = prefs.getString("hi", "") ?: ""
        hiscores = if (raw.isBlank()) mutableListOf()
                   else raw.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
        hasSave   = prefs.getBoolean("has_save", false) && prefs.getInt("saved_li", -1) >= 0
        coinsBank = prefs.getInt("coins", 0)
    }

    private fun savePrefs() {
        prefs.edit()
            .putString("theme", curThemeKey)
            .putString("diff", curDiff)
            .putBoolean("haptic", hapticOn)
            .putBoolean("particles", particlesOn)
            .putBoolean("trails", trailsOn)
            .putBoolean("sfx", sfxOn)
            .putFloat("vol", SoundEngine.volume)
            .putString("hi", hiscores.joinToString(","))
            .putInt("coins", coinsBank)
            .apply()
    }

    private fun saveScore(s: Int) {
        hiscores.add(s); hiscores.sortDescending()
        if (hiscores.size > 10) hiscores = hiscores.take(10).toMutableList()
        clearSave()
        savePrefs()
    }

    private fun saveProgress() {
        val st = gs ?: return
        if (screen != Screen.GAME && screen != Screen.PAUSE && screen != Screen.SETTINGS) return
        prefs.edit()
            .putBoolean("has_save", true)
            .putInt("saved_li", st.li)
            .putInt("saved_score", st.score)
            .putInt("saved_lives", st.lives)
            .putFloat("saved_speed", st.ballSpeed)
            .apply()
        hasSave = true
    }

    private fun clearSave() {
        prefs.edit().putBoolean("has_save", false).apply()
        hasSave = false
    }

    // ── Vibrator ──────────────────────────────────────────────────────────────
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    private fun haptic(ms: Long) {
        if (!hapticOn) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(ms)
        }
    }

    // ── Paint pool ─────────────────────────────────────────────────────────
    private val pFill  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE
    }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val shakeRng = java.util.Random()

    // ── Button rects (logical coords) ──────────────────────────────────────
    private val btnRects = mutableMapOf<String, RectF>()
    @Volatile private var hitTestRects: Map<String, RectF> = emptyMap()

    // ── Game thread ────────────────────────────────────────────────────────
    private var thread: GameThread? = null
    private var running = false

    inner class GameThread : Thread("GameThread") {
        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val dt  = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
                last = now

                if (screen == Screen.GAME) update(dt)

                val canvas = holder.lockCanvas() ?: continue
                try { render(canvas) } finally { holder.unlockCanvasAndPost(canvas) }

                val elapsed = (System.nanoTime() - now) / 1_000_000L
                val sleep   = (1000L / 60) - elapsed
                if (sleep > 1) Thread.sleep(sleep)
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    // Capture the top inset once; never let it shrink (avoids jitter as bars animate out).
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val top = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            insets.displayCutout?.safeInsetTop ?: 0
        } else 0
        if (top > topInsetPx) topInsetPx = top
        return super.onApplyWindowInsets(insets)
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        loadPrefs()
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        thread = GameThread().also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, h2: Int) {
        scaleX = w / LW; scaleY = h2 / LH
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        thread?.join(2000); thread = null
    }

    // Backgrounding: freeze the sim, show the PAUSE screen on return (so the ball
    // never resumes mid-flight before the player is ready), and persist everything —
    // savePrefs() also banks coins earned this session (they were only written on
    // shop purchases / settings changes before, so backgrounding lost them).
    fun pause() {
        gs?.paused = true
        if (screen == Screen.GAME) screen = Screen.PAUSE
        savePrefs()
        saveProgress()
    }
    fun resume() { gs?.paused = false }

    // ── Game factory ───────────────────────────────────────────────────────
    private fun startGame(li: Int = 0) {
        if (li == 0) clearSave()
        resetRunUpgrades()
        val cfg   = DIFF_MAP[curDiff] ?: DIFF_MAP["normal"]!!
        val speed = BASE_SPEED * cfg.mul
        val px    = LW / 2f
        gs = GameState(
            paddle    = Paddle(x = px - PADDLE_W / 2, targetX = px - PADDLE_W / 2),
            balls     = mutableListOf(makeBall(px, speed)),
            bricks    = makeBricks(li),
            score     = 0,
            lives     = cfg.lives,
            li        = li,
            ballSpeed = speed,
            cfg       = cfg,
        )
        screen = Screen.GAME
    }

    private fun continueGame() {
        resetRunUpgrades()
        val cfg   = DIFF_MAP[curDiff] ?: DIFF_MAP["normal"]!!
        val li    = prefs.getInt("saved_li", 0).coerceIn(0, MAX_LEVEL - 1)
        val score = prefs.getInt("saved_score", 0)
        val lives = prefs.getInt("saved_lives", cfg.lives)
        val speed = prefs.getFloat("saved_speed", BASE_SPEED * cfg.mul)
        val px    = LW / 2f
        gs = GameState(
            paddle    = Paddle(x = px - PADDLE_W / 2f, targetX = px - PADDLE_W / 2f),
            balls     = mutableListOf(makeBall(px, speed)),
            bricks    = makeBricks(li),
            score     = score,
            lives     = lives,
            li        = li,
            ballSpeed = speed,
            cfg       = cfg,
        )
        screen = Screen.GAME
    }

    private fun makeBall(px: Float, spd: Float) = Ball(
        x = px, y = LH - 80f,
        vx = (if (Math.random() < 0.5) -1 else 1) * spd * 0.5f,
        vy = -spd,
    )

    private fun makeBricks(li: Int): MutableList<Brick> {
        val def = LEVELS[li]
        val list = mutableListOf<Brick>()
        def.grid.forEachIndexed { r, row ->
            row.forEachIndexed cell@{ c, hp ->
                if (hp == 0) return@cell
                val special = if (Math.random() < def.sc) PU_TYPES[(Math.random() * PU_TYPES.size).toInt()] else null
                list += Brick(
                    x = BOX + c * (BW + BGAP), y = BOY + r * (BH + BGAP),
                    hp = hp, maxHp = hp, row = r, col = c, special = special,
                )
            }
        }
        return list
    }

    // ── Game update ────────────────────────────────────────────────────────
    private fun update(dt: Float) {
        val st = gs ?: return
        if (st.paused) return
        val T = THEMES[curThemeKey] ?: THEMES["neon"]!!

        // Snappier shake decay so it punches and settles quickly rather than wobbling.
        st.screenShake = maxOf(0f, st.screenShake - (3f + st.screenShake * 4f) * dt)
        st.flash       = maxOf(0f, st.flash - 4.5f * dt)
        st.comboTimer  = maxOf(0f, st.comboTimer - dt)
        if (st.comboTimer <= 0f) st.combo = 0

        // score popups rise and fade
        val spIter = st.scorePops.iterator()
        while (spIter.hasNext()) {
            val sp = spIter.next()
            sp.y -= 34f * dt; sp.life -= dt
            if (sp.life <= 0f) spIter.remove()
        }

        // decay powerup timers
        with(st.fx) {
            wide     = maxOf(0f, wide - dt)
            laser    = maxOf(0f, laser - dt)
            fireball = maxOf(0f, fireball - dt)
            shield   = maxOf(0f, shield - dt)
        }
        if (st.fx.wide <= 0f) {
            st.paddle.w = PADDLE_W
            st.paddle.targetX = clamp(st.paddle.targetX, 0f, LW - st.paddle.w)
        }

        // paddle lerp (track velocity for ball "english"; decay contact flash)
        val prevPx = st.paddle.x
        st.paddle.x = clamp(lerp(st.paddle.x, st.paddle.targetX, minOf(1f, 10f * dt)), 0f, LW - st.paddle.w)
        val instVx  = if (dt > 0f) (st.paddle.x - prevPx) / dt else 0f
        st.paddle.vx = lerp(st.paddle.vx, instVx, minOf(1f, 12f * dt))
        st.paddle.flash = maxOf(0f, st.paddle.flash - 5f * dt)

        // lasers
        val laserIter = st.lasers.iterator()
        while (laserIter.hasNext()) {
            val l = laserIter.next()
            l.y -= LASER_SPD * dt
            if (l.y < 0f) { laserIter.remove(); continue }
            val brickIter = st.bricks.iterator()
            while (brickIter.hasNext()) {
                val b = brickIter.next()
                if (!b.alive || l.dead) continue
                if (l.x > b.x && l.x < b.x + b.w && l.y > b.y && l.y < b.y + b.h) {
                    b.hp--; b.shake = 5f
                    if (b.hp <= 0) killBrick(st, b, T)
                    l.dead = true
                }
            }
            if (l.dead) laserIter.remove()
        }

        // powerup drops
        val puIter = st.powerups.iterator()
        while (puIter.hasNext()) {
            val p = puIter.next()
            p.y += PU_SPD * dt; p.rot += 2.5f * dt
            if (p.y > LH + 20f) { puIter.remove(); continue }
            val pd = st.paddle
            if (p.y + 10 > pd.y && p.y < pd.y + pd.h && p.x > pd.x && p.x < pd.x + pd.w) {
                applyPowerup(st, p.type); haptic(18); puIter.remove()
            }
        }

        // balls
        for (ball in st.balls) {
            if (!ball.active) continue
            if (trailsOn) {
                ball.trail.addLast(PointF(ball.x, ball.y))
                while (ball.trail.size > MAX_TRAIL) ball.trail.removeFirst()
            } else {
                ball.trail.clear()
            }

            // speed normalise
            val spd = hypot(ball.vx.toDouble(), ball.vy.toDouble()).toFloat()
            if (spd > 0 && abs(spd - st.ballSpeed) > 16f) {
                val f = st.ballSpeed / spd; ball.vx *= f; ball.vy *= f
            }

            if (!st.launched) { ball.x = st.paddle.x + st.paddle.w / 2f; ball.y = LH - 80f; continue }

            ball.x += ball.vx * dt; ball.y += ball.vy * dt

            // walls
            if (ball.x - ball.r < 0f)   { ball.x = ball.r;   ball.vx =  abs(ball.vx); SoundEngine.play("wall") }
            if (ball.x + ball.r > LW)   { ball.x = LW - ball.r; ball.vx = -abs(ball.vx); SoundEngine.play("wall") }
            if (ball.y - ball.r < 0f)   { ball.y = ball.r;   ball.vy =  abs(ball.vy); SoundEngine.play("wall") }

            // shield
            if (st.fx.shield > 0f && ball.vy > 0f &&
                ball.y + ball.r >= SHIELD_Y - SHIELD_BAND && ball.y - ball.r <= SHIELD_Y + SHIELD_BAND) {
                ball.y = SHIELD_Y - ball.r; ball.vy = -abs(ball.vy); SoundEngine.play("wall")
            }

            // paddle — hit position controls launch angle (intentional aiming).
            val pd = st.paddle
            if (ball.vy > 0f && ball.y + ball.r > pd.y && ball.y - ball.r < pd.y + pd.h &&
                ball.x > pd.x - 4f && ball.x < pd.x + pd.w + 4f) {
                ball.y = pd.y - ball.r
                // -1 (left edge) .. +1 (right edge); edges = steep angle, centre = straight up.
                var rel = (ball.x - (pd.x + pd.w / 2f)) / (pd.w / 2f)
                rel = clamp(rel, -1f, 1f)
                // Paddle "english": a moving paddle adds spin, so the player can steer the ball.
                rel = clamp(rel + (pd.vx / st.ballSpeed) * PADDLE_ENGLISH, -1.1f, 1.1f)
                val ang = rel * (MAX_BOUNCE_ANGLE * PI.toFloat() / 180f)
                val cs  = st.ballSpeed
                var nvx = sin(ang.toDouble()).toFloat() * cs
                var nvy = -abs(cos(ang.toDouble()).toFloat() * cs)
                // Floor the vertical component so the ball never crawls near-horizontally.
                val minVy = cs * MIN_VY_FRAC
                if (-nvy < minVy) {
                    nvy = -minVy
                    val sgn = if (nvx >= 0f) 1f else -1f
                    nvx = sgn * sqrt((cs * cs - minVy * minVy).coerceAtLeast(0f))
                }
                ball.vx = nvx; ball.vy = nvy
                pd.flash = 1f
                SoundEngine.play("paddle"); haptic(12)
            }

            if (ball.y - ball.r > LH) { ball.active = false; continue }

            // brick collision
            for (brick in st.bricks) {
                if (!brick.alive) continue
                val nx = clamp(ball.x, brick.x, brick.x + brick.w)
                val ny = clamp(ball.y, brick.y, brick.y + brick.h)
                val dx = ball.x - nx; val dy = ball.y - ny
                if (dx * dx + dy * dy >= ball.r * ball.r) continue

                if (st.fx.fireball <= 0f) {
                    val ox = if (ball.x < brick.x + brick.w / 2f) brick.x - ball.x - ball.r else brick.x + brick.w - ball.x + ball.r
                    val oy = if (ball.y < brick.y + brick.h / 2f) brick.y - ball.y - ball.r else brick.y + brick.h - ball.y + ball.r
                    if (abs(ox) < abs(oy)) ball.vx *= -1 else ball.vy *= -1
                }
                hitBrick(st, brick, T); haptic(8)
                break
            }
        }

        // spawn lasers
        if (st.fx.laser > 0f && Math.random() < st.cfg.lchance) {
            st.lasers += Laser(st.paddle.x + st.paddle.w * 0.25f, st.paddle.y)
            st.lasers += Laser(st.paddle.x + st.paddle.w * 0.75f, st.paddle.y)
        }

        // particles
        val pIter = st.particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            if (p.life <= 0f) { pIter.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vy += P_GRAV * dt; p.life -= 1.9f * dt; p.r *= 0.985f
        }

        for (b in st.bricks) b.shake = maxOf(0f, b.shake - 18f * dt)

        // transitions
        if (st.balls.all { !it.active }) {
            val nl = st.lives - 1
            SoundEngine.play("lose"); haptic(200)
            if (nl <= 0) {
                saveScore(st.score)
                screen = Screen.GAMEOVER
            } else {
                st.lives = nl
                st.launched = false
                st.balls.clear()
                st.balls += makeBall(st.paddle.x + st.paddle.w / 2f, st.ballSpeed)
            }
        }
        if (st.bricks.all { !it.alive }) {
            SoundEngine.play("win"); haptic(100)
            val nextLi = st.li + 1
            if (nextLi >= MAX_LEVEL) {
                saveScore(st.score)
                screen = Screen.WIN
            } else if (nextLi % 10 == 0) {
                // every 10th level: the coin shop (the big spend)
                shopNextLi = nextLi
                screen = Screen.SHOP
            } else {
                // every other level: a free roguelite upgrade pick
                shopNextLi = nextLi
                upgradeChoices = UPGRADE_POOL.shuffled().take(3)
                screen = Screen.UPGRADE
            }
        }
    }

    private fun hitBrick(st: GameState, brick: Brick, T: Theme) {
        brick.hp--; brick.shake = 8f
        if (st.fx.fireball <= 0f) st.screenShake = minOf(9f, st.screenShake + 3.5f)
        // Each column is a note: sweeping the ball across the wall plays a melody.
        SoundEngine.playNote(brick.col)
        val col = T.brickPalette[brick.row % T.brickPalette.size]
        if (particlesOn) spawnParticles(st, brick.x + brick.w / 2f, brick.y + brick.h / 2f, col, 6)
        if (brick.hp <= 0) killBrick(st, brick, T)
    }

    private fun killBrick(st: GameState, brick: Brick, T: Theme) {
        brick.alive = false
        val col = T.brickPalette[brick.row % T.brickPalette.size]
        val cx = brick.x + brick.w / 2f; val cy = brick.y + brick.h / 2f
        if (particlesOn) spawnParticles(st, cx, cy, col, 16)
        // Brief screen flash tinted by the brick colour — punchy destruction feedback.
        st.flash = minOf(1f, st.flash + 0.5f)
        st.screenShake = minOf(11f, st.screenShake + 2.5f)
        val gain = (10 + brick.row * 2) * (1 + minOf(st.combo, MAX_COMBO - 1))
        st.score += gain
        if (st.scorePops.size < 24) st.scorePops += ScorePop(cx, cy, "+$gain", col)
        st.combo = minOf(st.combo + 1, MAX_COMBO); st.comboTimer = 2.2f
        brick.special?.let { st.powerups += Powerup(cx, cy, it) }
        coinsBank++
    }

    private fun spawnParticles(st: GameState, x: Float, y: Float, col: Int, n: Int) {
        val cap = st.cfg.pcap
        repeat(minOf(n, maxOf(0, cap - st.particles.size))) {
            val a = (Math.random() * 2 * PI).toFloat()
            val s = 80f + (Math.random() * 170f).toFloat()
            // Slight upward bias so shards burst out then arc down under gravity (P_GRAV).
            val vy = sin(a) * s - 40f
            st.particles += Particle(x, y, cos(a) * s, vy, color = col, r = 1.5f + (Math.random() * 3.5f).toFloat())
        }
    }

    private fun applyPowerup(st: GameState, type: String) {
        SoundEngine.play("powerup")
        when (type) {
            "wide"     -> { st.paddle.w = PADDLE_WIDE; st.fx.wide = PU_DUR["wide"]!!; st.paddle.targetX = clamp(st.paddle.targetX, 0f, LW - st.paddle.w) }
            "multi"    -> { val cur = st.balls.filter { it.active }.toList(); cur.forEach { b -> st.balls += b.copy(vx = b.vx + 90f, trail = ArrayDeque()); st.balls += b.copy(vx = b.vx - 90f, trail = ArrayDeque()) } }
            "laser"    -> st.fx.laser    = PU_DUR["laser"]!!
            "fireball" -> st.fx.fireball = PU_DUR["fireball"]!!
            "shield"   -> st.fx.shield   = PU_DUR["shield"]!!
            "slow"     -> st.balls.forEach { it.vx *= 0.78f; it.vy *= 0.78f }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────
    private fun render(canvas: Canvas) {
        val T = THEMES[curThemeKey] ?: THEMES["neon"]!!
        canvas.save()
        canvas.scale(scaleX, scaleY)
        btnRects.clear()

        when (screen) {
            Screen.MENU     -> drawMenu(canvas, T)
            Screen.GAME     -> drawGame(canvas, T)
            Screen.PAUSE    -> drawPause(canvas, T)
            Screen.GAMEOVER -> drawGameOver(canvas, T)
            Screen.WIN      -> drawWin(canvas, T)
            Screen.SCORES   -> drawScores(canvas, T)
            Screen.SETTINGS -> drawSettings(canvas, T)
            Screen.SHOP     -> drawShop(canvas, T)
            Screen.UPGRADE  -> drawUpgrade(canvas, T)
        }

        canvas.restore()
        hitTestRects = HashMap(btnRects)
    }

    // ─ Shared drawing helpers ────────────────────────────────────────────────

    private fun fillBg(canvas: Canvas, bg: Int) {
        canvas.drawColor(bg)
    }

    private fun drawGrid(canvas: Canvas) {
        pFill.color = Color.argb(8, 255, 255, 255)
        pFill.style = Paint.Style.FILL
        var x = 0f; while (x <= LW) { canvas.drawRect(x, 0f, x + 0.5f, LH, pFill); x += 40f }
        var y = 0f; while (y <= LH) { canvas.drawRect(0f, y, LW, y + 0.5f, pFill); y += 40f }
    }

    private fun textBtn(canvas: Canvas, label: String, cx: Float, cy: Float, w: Float, h: Float,
                        accent: Int, key: String, active: Boolean = false): RectF {
        val r = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        pFill.style = Paint.Style.FILL
        pFill.color = if (active) colorAlpha(accent, 0.25f) else colorAlpha(accent, 0.08f)
        canvas.drawRoundRect(r, 12f, 12f, pFill)
        pStroke.color = colorAlpha(accent, 0.5f); pStroke.strokeWidth = 1.5f
        canvas.drawRoundRect(r, 12f, 12f, pStroke)
        pText.textAlign = Paint.Align.CENTER; pText.color = accent; pText.textSize = 16f
        canvas.drawText(label, cx, cy + 6f, pText)
        btnRects[key] = r
        return r
    }

    private fun sectionLabel(canvas: Canvas, text: String, cy: Float, col: Int) {
        pText.color = colorAlpha(col, 0.6f); pText.textSize = 10f
        canvas.drawText(text, LW / 2f, cy, pText)
    }

    private fun drawAimLine(canvas: Canvas, st: GameState) {
        val ball = st.balls.firstOrNull() ?: return
        val rel  = (st.aimX - LW / 2f) / (LW / 2f)
        val ang  = rel * (40f * PI.toFloat() / 180f)
        var vx   = sin(ang.toDouble()).toFloat()
        var vy   = -abs(cos(ang.toDouble()).toFloat())
        var x    = ball.x; var y = ball.y
        var dist = 0f
        val maxDist = 320f
        val step = 4f
        pFill.style = Paint.Style.FILL
        while (dist < maxDist) {
            x += vx * step; y += vy * step; dist += step
            if (x < BALL_R) { x = BALL_R; vx = abs(vx) }
            if (x > LW - BALL_R) { x = LW - BALL_R; vx = -abs(vx) }
            if (y < BALL_R) break
            if (dist.toInt() % 12 < 6) {
                val a = 0.45f * (1f - dist / maxDist)
                pFill.color = colorAlpha(Color.WHITE, a)
                canvas.drawCircle(x, y, 2f, pFill)
            }
        }
    }

    // ─ MENU ─────────────────────────────────────────────────────────────────

    private fun drawMenu(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER

        // Title
        pText.color = T.accent; pText.textSize = 42f
        canvas.drawText("BRICK", LW / 2f, 210f, pText)
        pText.color = T.text; pText.textSize = 26f
        canvas.drawText("BREAKER", LW / 2f, 248f, pText)
        pText.color = colorAlpha(T.text, 0.4f); pText.textSize = 11f
        canvas.drawText("$MAX_LEVEL LEVELS  •  6 THEMES  •  POWERUPS", LW / 2f, 272f, pText)

        if (hasSave) {
            val savedLvl = prefs.getInt("saved_li", 0) + 1
            textBtn(canvas, "▶  CONTINUE (LVL $savedLvl)", LW/2f, 330f, 250f, 48f, T.accent, "continue")
            textBtn(canvas, "▶  NEW GAME",   LW/2f, 390f, 200f, 44f, T.text,   "play")
            textBtn(canvas, "🏆  SCORES",    LW/2f, 444f, 200f, 44f, T.text,   "scores")
            textBtn(canvas, "⚙  SETTINGS",  LW/2f, 498f, 200f, 44f, T.text,   "settings")
        } else {
            textBtn(canvas, "▶  PLAY",      LW/2f, 340f, 200f, 48f, T.accent, "play")
            textBtn(canvas, "🏆  SCORES",   LW/2f, 402f, 200f, 48f, T.text,   "scores")
            textBtn(canvas, "⚙  SETTINGS", LW/2f, 464f, 200f, 48f, T.text,   "settings")
        }

        pText.color = colorAlpha(T.text, 0.25f); pText.textSize = 10f
        canvas.drawText("Drag to aim · Tap to launch", LW / 2f, 555f, pText)
    }

    // ─ GAME ─────────────────────────────────────────────────────────────────

    private fun drawGame(canvas: Canvas, T: Theme) {
        val st = gs ?: return

        val shk = st.screenShake
        val ox  = if (shk > 0) (shakeRng.nextFloat() - 0.5f) * shk else 0f
        val oy  = if (shk > 0) (shakeRng.nextFloat() - 0.5f) * shk else 0f
        canvas.save(); canvas.translate(ox, oy)

        fillBg(canvas, T.bg); drawGrid(canvas)

        // bricks
        for (brick in st.bricks) {
            if (!brick.alive) continue
            val col = T.brickPalette[brick.row % T.brickPalette.size]
            val bs  = if (brick.shake > 0) (shakeRng.nextFloat() - 0.5f) * brick.shake * 0.5f else 0f
            val alpha = 0.55f + 0.45f * (brick.hp.toFloat() / brick.maxHp)
            val bx0 = brick.x + bs; val bx1 = brick.x + brick.w + bs
            // Flat clean tile (Breakout-71 style): solid fill + soft top highlight band.
            pFill.style = Paint.Style.FILL; pFill.color = colorAlpha(col, alpha)
            canvas.drawRoundRect(bx0, brick.y, bx1, brick.y + brick.h, 3f, 3f, pFill)
            pFill.color = colorAlpha(Color.WHITE, 0.12f * alpha)
            canvas.drawRoundRect(bx0, brick.y, bx1, brick.y + brick.h * 0.42f, 3f, 3f, pFill)
            val borderAlpha = when (brick.maxHp) { 1 -> 40; 2 -> 75; 3 -> 110; else -> 150 }
            val borderWidth = when (brick.maxHp) { 1 -> 0.5f; 2 -> 1f; 3 -> 1.5f; else -> 2f }
            pStroke.color = Color.argb(borderAlpha, 255, 255, 255); pStroke.strokeWidth = borderWidth
            canvas.drawRoundRect(bx0, brick.y, bx1, brick.y + brick.h, 3f, 3f, pStroke)
            // HP pip bar for multi-hit bricks
            if (brick.maxHp > 1) {
                val bx = brick.x + bs; val by = brick.y; val bw = brick.w; val bh = brick.h
                // armour border ring (gets thicker with more HP)
                val armorAlpha = when (brick.maxHp) { 2 -> 80; 3 -> 130; else -> 190 }
                pStroke.color = Color.argb(armorAlpha, 255, 255, 255)
                pStroke.strokeWidth = when (brick.maxHp) { 2 -> 1.2f; 3 -> 2f; else -> 2.8f }
                canvas.drawRoundRect(bx + 1f, by + 1f, bx + bw - 1f, by + bh - 1f, 2f, 2f, pStroke)
                // HP pip row at bottom of brick
                val pipCount = brick.maxHp.coerceAtMost(8)
                val pipW = (bw - 8f) / pipCount - 2f
                val pipH = 3f; val pipY = by + bh - 5f
                for (pi in 0 until pipCount) {
                    val px = bx + 4f + pi * (pipW + 2f)
                    pFill.style = Paint.Style.FILL
                    pFill.color = if (pi < brick.hp) Color.argb(220, 255, 255, 255) else Color.argb(55, 0, 0, 0)
                    canvas.drawRoundRect(px, pipY, px + pipW, pipY + pipH, 1.5f, 1.5f, pFill)
                }
            }
            // powerup icon: letter on single-HP bricks, gold dot corner on multi-HP
            brick.special?.let { sp ->
                val iconMap = mapOf("wide" to "W","multi" to "M","laser" to "L","slow" to "Z","fireball" to "F","shield" to "♦")
                val icon = iconMap[sp] ?: "?"
                if (brick.maxHp == 1) {
                    pText.textAlign = Paint.Align.CENTER
                    pText.color = Color.argb(220, 255, 255, 255); pText.textSize = 9f
                    canvas.drawText(icon, brick.x + brick.w / 2f + bs, brick.y + brick.h / 2f + 3f, pText)
                } else {
                    pFill.style = Paint.Style.FILL
                    pFill.color = Color.argb(200, 255, 210, 0)
                    canvas.drawCircle(brick.x + brick.w - 3.5f + bs, brick.y + 3.5f, 2.5f, pFill)
                }
            }
        }

        // particles
        if (particlesOn) {
            for (p in st.particles) {
                pFill.color = colorAlpha(p.color, maxOf(0f, p.life))
                pFill.style = Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, maxOf(0f, p.r), pFill)
            }
        }

        // powerup drops
        val puColors = mapOf("wide" to Color.CYAN,"multi" to Color.MAGENTA,"laser" to Color.YELLOW,
            "slow" to Color.parseColor("#00ffcc"),"fireball" to Color.parseColor("#ff6600"),"shield" to Color.parseColor("#4488ff"))
        for (p in st.powerups) {
            val col = puColors[p.type] ?: Color.WHITE
            canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(Math.toDegrees(p.rot.toDouble()).toFloat())
            pFill.style = Paint.Style.FILL; pFill.color = col
            canvas.drawCircle(0f, 0f, 8f, pFill)
            pText.color = Color.BLACK; pText.textSize = 8f
            canvas.drawText(mapOf("wide" to "W","multi" to "M","laser" to "L","slow" to "S","fireball" to "F","shield" to "S")[p.type] ?: "P", 0f, 3f, pText)
            canvas.restore()
        }

        // lasers
        pFill.color = Color.YELLOW; pFill.style = Paint.Style.FILL
        for (l in st.lasers) canvas.drawRect(l.x - 1.5f, l.y - 10f, l.x + 1.5f, l.y + 4f, pFill)

        // ball trails — comet-style taper that fades and shrinks toward the tail
        if (trailsOn) {
            pFill.style = Paint.Style.FILL
            for (ball in st.balls) {
                if (!ball.active) continue
                val n = ball.trail.size
                val fireCol = st.fx.fireball > 0f
                val baseCol = if (fireCol) Color.parseColor("#ff6600") else T.ball
                ball.trail.forEachIndexed { i, tp ->
                    val f = (i + 1f) / n           // 0 (oldest) .. 1 (newest)
                    pFill.color = colorAlpha(baseCol, 0.40f * f)
                    canvas.drawCircle(tp.x, tp.y, ball.r * (0.25f + 0.6f * f), pFill)
                }
            }
        }

        // aim trajectory line (shown before launch)
        if (!st.launched) drawAimLine(canvas, st)

        // balls
        for (ball in st.balls) {
            if (!ball.active) continue
            val fb = st.fx.fireball > 0f
            pFill.style = Paint.Style.FILL
            pFill.color = if (fb) Color.parseColor("#ff6600") else T.ball
            canvas.drawCircle(ball.x, ball.y, BALL_R, pFill)
            // highlight
            pFill.color = colorAlpha(Color.WHITE, 0.6f)
            canvas.drawCircle(ball.x - 2f, ball.y - 2f, BALL_R * 0.35f, pFill)
        }

        // shield
        if (st.fx.shield > 0f) {
            val alpha = 0.4f + 0.3f * sin(System.currentTimeMillis() / 100.0).toFloat()
            pFill.style = Paint.Style.FILL
            pFill.color = colorAlpha(Color.parseColor("#4488ff"), alpha)
            canvas.drawRect(0f, SHIELD_Y - SHIELD_BAND / 2f, LW, SHIELD_Y + SHIELD_BAND / 2f, pFill)
        }

        // paddle (glows white briefly on ball contact)
        val pd = st.paddle
        pFill.style = Paint.Style.FILL; pFill.color = T.paddle
        canvas.drawRoundRect(pd.x, pd.y, pd.x + pd.w, pd.y + pd.h, 6f, 6f, pFill)
        if (pd.flash > 0f) {
            pFill.color = colorAlpha(Color.WHITE, 0.55f * pd.flash)
            canvas.drawRoundRect(pd.x, pd.y, pd.x + pd.w, pd.y + pd.h, 6f, 6f, pFill)
        }
        if (st.fx.laser > 0f) {
            val la = 0.5f + 0.5f * sin(System.currentTimeMillis() / 80.0).toFloat()
            pFill.color = colorAlpha(Color.YELLOW, la)
            canvas.drawRoundRect(pd.x, pd.y, pd.x + pd.w, pd.y + pd.h, 6f, 6f, pFill)
        }

        // score popups (rise + fade)
        pText.textAlign = Paint.Align.CENTER; pText.textSize = 12f
        for (sp in st.scorePops) {
            val a = (sp.life / SCOREPOP_LIFE).coerceIn(0f, 1f)
            pText.color = colorAlpha(sp.color, a)
            canvas.drawText(sp.text, sp.x, sp.y, pText)
        }

        // full-screen colour flash on brick destruction — quick punch of light
        if (st.flash > 0f) {
            pFill.style = Paint.Style.FILL
            pFill.color = colorAlpha(Color.WHITE, 0.10f * st.flash)
            canvas.drawRect(0f, 0f, LW, LH, pFill)
        }

        canvas.restore()  // ── end screen-shake block ─────────────────────────

        // ── HUD (drawn outside shake so btnRects always matches visual position) ──
        // topInsetPx converts the device's top inset (status bar + cutout) to logical
        // units so the HUD clears the punch-hole camera on Pixel 9 / similar devices.
        val topPad = topInsetPx / scaleY.coerceAtLeast(1f)
        val hudY   = topPad + 28f

        // Pause button: RIGHT side, inset enough to not overlap lives text.
        // Lives text is right-aligned at LW-58, giving a clear gap to the button.
        textBtn(canvas, "❚❚", LW - 20f, topPad + 18f, 38f, 32f, T.accent, "pause")
        pText.textAlign = Paint.Align.LEFT;  pText.color = T.text; pText.textSize = 14f
        canvas.drawText("★ ${st.score}", 8f, hudY, pText)
        pText.textAlign = Paint.Align.CENTER
        canvas.drawText("LVL ${st.li + 1}/$MAX_LEVEL", LW / 2f, hudY, pText)
        pText.textAlign = Paint.Align.RIGHT
        canvas.drawText("♥".repeat(st.lives), LW - 58f, hudY, pText)

        // combo
        if (st.combo >= 2 && st.comboTimer > 0f) {
            val a = minOf(1f, st.comboTimer / 1.2f)
            pText.textAlign = Paint.Align.CENTER; pText.color = colorAlpha(T.accent, a)
            pText.textSize = 18f + st.combo * 2f
            canvas.drawText("${1 + minOf(st.combo, MAX_COMBO - 1)}x COMBO!", LW / 2f, topPad + 58f, pText)
        }

        // powerup timers
        var fy = topPad + 48f
        for ((k, lbl, col) in listOf(
            Triple("laser", "LASER", Color.YELLOW),
            Triple("fireball", "FIRE", Color.parseColor("#ff6600")),
            Triple("wide", "WIDE", Color.CYAN),
            Triple("shield", "SHIELD", Color.parseColor("#4488ff")),
        )) {
            val t = when (k) { "laser" -> st.fx.laser; "fireball" -> st.fx.fireball; "wide" -> st.fx.wide; else -> st.fx.shield }
            if (t <= 0f) continue
            pText.textAlign = Paint.Align.LEFT; pText.color = col; pText.textSize = 11f
            canvas.drawText("$lbl ${String.format("%.1f", t)}s", 8f, fy, pText)
            fy += 14f
        }

        // launch hint
        if (!st.launched) {
            val a = 0.5f + 0.5f * sin(System.currentTimeMillis() / 400.0).toFloat()
            pText.textAlign = Paint.Align.CENTER; pText.color = colorAlpha(Color.WHITE, a); pText.textSize = 13f
            canvas.drawText("DRAG TO AIM  ·  TAP TO LAUNCH", LW / 2f, LH / 2f + 40f, pText)
        }
    }

    // ─ PAUSE ─────────────────────────────────────────────────────────────────

    private fun drawPause(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER; pText.color = T.accent; pText.textSize = 28f
        canvas.drawText("PAUSED", LW / 2f, 220f, pText)

        textBtn(canvas, "▶  RESUME",    LW/2f, 300f, 220f, 50f, T.accent, "resume")
        textBtn(canvas, "↺  RESTART",   LW/2f, 365f, 220f, 50f, T.text,   "restart")
        textBtn(canvas, "⚙  SETTINGS",  LW/2f, 430f, 220f, 50f, T.text,   "settings")
        textBtn(canvas, "⌂  MENU",      LW/2f, 495f, 220f, 50f, Color.parseColor("#ff4444"), "menu")
    }

    // ─ GAMEOVER ──────────────────────────────────────────────────────────────

    private fun drawGameOver(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER
        pText.color = Color.parseColor("#ff4444"); pText.textSize = 28f
        canvas.drawText("GAME OVER", LW / 2f, 200f, pText)
        pText.color = T.accent; pText.textSize = 36f
        canvas.drawText(gs?.score?.toString() ?: "0", LW / 2f, 252f, pText)
        pText.color = colorAlpha(T.text, 0.4f); pText.textSize = 12f
        canvas.drawText("BEST: ${hiscores.firstOrNull() ?: 0}", LW / 2f, 278f, pText)

        textBtn(canvas, "↺  PLAY AGAIN", LW/2f, 340f, 220f, 50f, T.accent, "play")
        textBtn(canvas, "🏆  LEADERBOARD", LW/2f, 405f, 220f, 50f, T.text, "scores")
        textBtn(canvas, "⌂  MENU",        LW/2f, 470f, 220f, 50f, T.text, "menu")
    }

    // ─ WIN ───────────────────────────────────────────────────────────────────

    private fun drawWin(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER
        pText.color = Color.parseColor("#00ff88"); pText.textSize = 28f
        canvas.drawText("YOU WIN!", LW / 2f, 200f, pText)
        pText.color = T.accent; pText.textSize = 36f
        canvas.drawText(gs?.score?.toString() ?: "0", LW / 2f, 252f, pText)
        pText.color = colorAlpha(T.text, 0.4f); pText.textSize = 12f
        canvas.drawText("ALL $MAX_LEVEL LEVELS CLEARED", LW / 2f, 278f, pText)

        textBtn(canvas, "↺  PLAY AGAIN",   LW/2f, 340f, 220f, 50f, T.accent, "play")
        textBtn(canvas, "🏆  LEADERBOARD", LW/2f, 405f, 220f, 50f, T.text,   "scores")
        textBtn(canvas, "⌂  MENU",         LW/2f, 470f, 220f, 50f, T.text,   "menu")
    }

    // ─ SCORES ────────────────────────────────────────────────────────────────

    private fun drawScores(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER; pText.color = T.accent; pText.textSize = 20f
        canvas.drawText("🏆 LEADERBOARD", LW / 2f, 70f, pText)

        if (hiscores.isEmpty()) {
            pText.color = colorAlpha(T.text, 0.35f); pText.textSize = 13f
            canvas.drawText("No scores yet — play a game!", LW / 2f, 200f, pText)
        } else {
            val medals = listOf("🥇","🥈","🥉")
            hiscores.take(10).forEachIndexed { i, s ->
                val cy = 110f + i * 42f
                pFill.style = Paint.Style.FILL
                pFill.color = when(i) { 0 -> colorAlpha(Color.parseColor("#ffd700"), 0.12f); 1, 2 -> colorAlpha(Color.WHITE, 0.05f); else -> Color.TRANSPARENT }
                canvas.drawRoundRect(20f, cy - 16f, LW - 20f, cy + 20f, 10f, 10f, pFill)
                pText.textAlign = Paint.Align.LEFT;  pText.color = T.text; pText.textSize = 14f
                canvas.drawText(if (i < 3) medals[i] else "#${i+1}", 36f, cy + 5f, pText)
                pText.textAlign = Paint.Align.CENTER; pText.color = T.accent; pText.textSize = 16f
                canvas.drawText(s.toString(), LW / 2f, cy + 5f, pText)
            }
        }

        textBtn(canvas, "← BACK", LW / 2f, LH - 70f, 180f, 44f, T.text, "back")
    }

    // ─ SETTINGS ──────────────────────────────────────────────────────────────

    private fun drawSettings(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER; pText.color = T.accent; pText.textSize = 20f
        canvas.drawText("⚙ SETTINGS", LW / 2f, 50f, pText)

        // Theme
        sectionLabel(canvas, "THEME", 85f, T.text)
        val themeKeys = listOf("neon","retro","ice","fire","mono","forest")
        val tw = 52f; val th2 = 36f; val tStartX = (LW - (themeKeys.size * tw + (themeKeys.size - 1) * 6f)) / 2f
        themeKeys.forEachIndexed { i, k ->
            val cx = tStartX + i * (tw + 6f) + tw / 2f
            val r  = RectF(cx - tw / 2f, 92f, cx + tw / 2f, 92f + th2)
            val th = THEMES[k]!!
            pFill.style = Paint.Style.FILL
            pFill.color = if (k == curThemeKey) colorAlpha(th.accent, 0.3f) else colorAlpha(Color.WHITE, 0.06f)
            canvas.drawRoundRect(r, 8f, 8f, pFill)
            pStroke.color = if (k == curThemeKey) th.accent else colorAlpha(Color.WHITE, 0.15f); pStroke.strokeWidth = 1.5f
            canvas.drawRoundRect(r, 8f, 8f, pStroke)
            // color swatches
            val sw = 7f; val sy = 92f + th2 / 2f - sw / 2f
            th.brickPalette.take(3).forEachIndexed { j, c ->
                pFill.color = c; pFill.style = Paint.Style.FILL
                canvas.drawRect(cx - sw * 1.5f + j * (sw + 1f), sy, cx - sw * 1.5f + j * (sw + 1f) + sw, sy + sw, pFill)
            }
            pText.color = if (k == curThemeKey) th.accent else colorAlpha(Color.WHITE, 0.5f); pText.textSize = 8f
            canvas.drawText(k, cx, 92f + th2 - 3f, pText)
            btnRects["theme_$k"] = r
        }

        // Difficulty
        sectionLabel(canvas, "DIFFICULTY", 153f, T.text)
        val diffs = listOf("easy" to "😌 EASY","normal" to "😤 NORMAL","hard" to "😈 HARD")
        val dw = (LW - 48f) / 3f
        diffs.forEachIndexed { i, (dk, dl) ->
            val cx = 24f + i * (dw + 6f) + dw / 2f
            val r  = RectF(cx - dw / 2f, 160f, cx + dw / 2f, 200f)
            pFill.style = Paint.Style.FILL
            pFill.color = if (dk == curDiff) colorAlpha(T.accent, 0.25f) else colorAlpha(Color.WHITE, 0.06f)
            canvas.drawRoundRect(r, 10f, 10f, pFill)
            pStroke.color = if (dk == curDiff) T.accent else colorAlpha(Color.WHITE, 0.15f); pStroke.strokeWidth = 1.5f
            canvas.drawRoundRect(r, 10f, 10f, pStroke)
            pText.color = if (dk == curDiff) T.accent else colorAlpha(Color.WHITE, 0.55f); pText.textSize = 10f
            canvas.drawText(dl, cx, 185f, pText)
            btnRects["diff_$dk"] = r
        }

        // Toggles
        val toggles = listOf(
            Triple("sfx",       "Sound FX",    sfxOn),
            Triple("haptic",    "Haptic",      hapticOn),
            Triple("particles", "Particles",   particlesOn),
            Triple("trails",    "Ball Trails", trailsOn),
        )
        toggles.forEachIndexed { i, (key, label, on) ->
            val cy = 230f + i * 46f
            pText.textAlign = Paint.Align.LEFT; pText.color = T.text; pText.textSize = 14f
            canvas.drawText(label, 30f, cy + 4f, pText)
            val tr = RectF(LW - 70f, cy - 10f, LW - 26f, cy + 14f)
            pFill.style = Paint.Style.FILL
            pFill.color = if (on) colorAlpha(T.accent, 0.8f) else colorAlpha(Color.WHITE, 0.15f)
            canvas.drawRoundRect(tr, 12f, 12f, pFill)
            val knobX = if (on) tr.right - 14f else tr.left + 14f
            pFill.color = Color.WHITE
            canvas.drawCircle(knobX, cy + 2f, 10f, pFill)
            btnRects["toggle_$key"] = tr
        }

        textBtn(canvas, "← BACK", LW / 2f, LH - 50f, 180f, 44f, T.text, "back")
    }

    // ─ SHOP ──────────────────────────────────────────────────────────────────

    private fun startNextLevel() {
        val st = gs ?: return
        val px = LW / 2f
        val pw = PADDLE_W + runPaddleBonus
        val balls = mutableListOf(makeBall(px, st.ballSpeed))
        repeat(runExtraBalls) { balls += makeBall(px, st.ballSpeed) }   // Multiball upgrade
        gs = GameState(
            paddle    = Paddle(x = px - pw / 2f, targetX = px - pw / 2f, w = pw),  // Wider Paddle upgrade
            balls     = balls,
            bricks    = makeBricks(shopNextLi),
            score     = st.score,
            lives     = st.lives,
            li        = shopNextLi,
            ballSpeed = st.ballSpeed,
            cfg       = st.cfg,
            fx        = st.fx.copy(fireball = maxOf(st.fx.fireball, runStartFire)),  // Fire Start upgrade
        )
        screen = Screen.GAME
    }

    private fun resetRunUpgrades() { runPaddleBonus = 0f; runExtraBalls = 0; runStartFire = 0f }

    private fun applyUpgrade(key: String) {
        val st = gs
        when (key) {
            "up_life"      -> st?.let { it.lives++ }
            "up_wide"      -> { runPaddleBonus = (runPaddleBonus + 16f).coerceAtMost(70f); st?.paddle?.let { it.w = PADDLE_W + runPaddleBonus } }
            "up_multiball" -> runExtraBalls = (runExtraBalls + 1).coerceAtMost(4)
            "up_slow"      -> st?.let { it.ballSpeed *= 0.88f }
            "up_fire"      -> runStartFire = maxOf(runStartFire, 6f)
            "up_coins"     -> { coinsBank += 25; savePrefs() }
        }
    }

    private fun drawShop(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER

        // heading
        pText.color = T.accent; pText.textSize = 22f
        canvas.drawText("⭐ LEVEL ${shopNextLi + 1} UNLOCKED", LW / 2f, 60f, pText)
        pText.color = colorAlpha(T.text, 0.7f); pText.textSize = 12f
        canvas.drawText("Spend your coins on an upgrade", LW / 2f, 84f, pText)

        // coin display
        pText.color = Color.parseColor("#FFD700"); pText.textSize = 18f
        canvas.drawText("🪙 $coinsBank coins", LW / 2f, 118f, pText)

        data class ShopItem(val key: String, val label: String, val desc: String, val cost: Int, val canAfford: Boolean)
        val items = listOf(
            ShopItem("shop_life",   "+1 Life",       "Restore one life",           20, coinsBank >= 20),
            ShopItem("shop_wide",   "Wide Paddle",   "Wider paddle for 15 seconds", 15, coinsBank >= 15),
            ShopItem("shop_shield", "Shield",        "Shield for 10 seconds",       12, coinsBank >= 12),
            ShopItem("shop_slow",   "Slow Ball",     "Slow ball by 18%",            10, coinsBank >= 10),
        )
        items.forEachIndexed { i, item ->
            val cy = 170f + i * 88f
            val affordCol = if (item.canAfford) T.accent else colorAlpha(T.text, 0.3f)
            val r = RectF(20f, cy - 28f, LW - 20f, cy + 46f)
            pFill.style = Paint.Style.FILL
            pFill.color = if (item.canAfford) colorAlpha(T.accent, 0.10f) else colorAlpha(Color.WHITE, 0.04f)
            canvas.drawRoundRect(r, 12f, 12f, pFill)
            pStroke.color = if (item.canAfford) colorAlpha(T.accent, 0.5f) else colorAlpha(Color.WHITE, 0.10f)
            pStroke.strokeWidth = 1.5f
            canvas.drawRoundRect(r, 12f, 12f, pStroke)
            pText.textAlign = Paint.Align.LEFT; pText.color = affordCol; pText.textSize = 15f
            canvas.drawText(item.label, 36f, cy, pText)
            pText.color = colorAlpha(T.text, 0.5f); pText.textSize = 11f
            canvas.drawText(item.desc, 36f, cy + 18f, pText)
            // cost badge
            pText.textAlign = Paint.Align.RIGHT; pText.color = Color.parseColor("#FFD700"); pText.textSize = 13f
            canvas.drawText("🪙 ${item.cost}", LW - 36f, cy + 4f, pText)
            if (item.canAfford) btnRects[item.key] = r
        }

        textBtn(canvas, "▶  CONTINUE (free)", LW / 2f, LH - 64f, 230f, 46f, colorAlpha(T.text, 0.6f), "shop_skip")
    }

    private fun drawUpgrade(canvas: Canvas, T: Theme) {
        fillBg(canvas, T.bg); drawGrid(canvas)
        pText.textAlign = Paint.Align.CENTER
        pText.color = T.accent; pText.textSize = 22f
        canvas.drawText("⭐ LEVEL ${shopNextLi + 1}", LW / 2f, 64f, pText)
        pText.color = colorAlpha(T.text, 0.7f); pText.textSize = 12f
        canvas.drawText("Choose an upgrade — free", LW / 2f, 90f, pText)

        upgradeChoices.forEachIndexed { i, up ->
            val cy = 168f + i * 100f
            val r = RectF(20f, cy - 32f, LW - 20f, cy + 54f)
            pFill.style = Paint.Style.FILL; pFill.color = colorAlpha(T.accent, 0.10f)
            canvas.drawRoundRect(r, 14f, 14f, pFill)
            pStroke.color = colorAlpha(T.accent, 0.55f); pStroke.strokeWidth = 1.5f
            canvas.drawRoundRect(r, 14f, 14f, pStroke)
            pText.textAlign = Paint.Align.LEFT; pText.color = T.accent; pText.textSize = 17f
            canvas.drawText("${up.icon}  ${up.label}", 38f, cy + 2f, pText)
            pText.color = colorAlpha(T.text, 0.55f); pText.textSize = 12f
            canvas.drawText(up.desc, 38f, cy + 26f, pText)
            btnRects[up.key] = r
        }
    }

    // ─ Touch ────────────────────────────────────────────────────────────────
    private var touchDownX = 0f; private var touchDownY = 0f; private var touchMoved = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val lx = e.x / scaleX; val ly = e.y / scaleY
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = lx; touchDownY = ly; touchMoved = false
                if (screen == Screen.GAME) {
                    val st = gs ?: return true
                    if (st.launched) st.paddle.targetX = clamp(lx - st.paddle.w / 2f, 0f, LW - st.paddle.w)
                    else st.aimX = clamp(lx, 0f, LW)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(lx - touchDownX) > 4f || abs(ly - touchDownY) > 4f) touchMoved = true
                if (screen == Screen.GAME) {
                    val st = gs ?: return true
                    if (st.launched) st.paddle.targetX = clamp(lx - st.paddle.w / 2f, 0f, LW - st.paddle.w)
                    else st.aimX = clamp(lx, 0f, LW)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!touchMoved) handleTap(lx, ly)
            }
        }
        return true
    }

    private fun handleTap(lx: Float, ly: Float) {
        // Check button rects (stable snapshot from previous frame — no race with render thread)
        for ((key, rect) in hitTestRects) {
            if (!rect.contains(lx, ly)) continue
            when (key) {
                "play"     -> startGame(0)
                "continue" -> continueGame()
                "resume"   -> { screen = Screen.GAME }
                "restart"  -> startGame(0)
                "pause"    -> { screen = Screen.PAUSE }
                "menu"     -> screen = Screen.MENU
                "scores"   -> screen = Screen.SCORES
                "settings" -> { prevScreen = screen; screen = Screen.SETTINGS }
                "back"     -> screen = prevScreen
                "shop_life"   -> { if (coinsBank >= 20) { coinsBank -= 20; gs?.lives = (gs?.lives ?: 0) + 1; savePrefs() }; startNextLevel() }
                "shop_wide"   -> { if (coinsBank >= 15) { coinsBank -= 15; gs?.fx?.wide = 15f; gs?.paddle?.w = PADDLE_WIDE; savePrefs() }; startNextLevel() }
                "shop_slow"   -> { if (coinsBank >= 10) { coinsBank -= 10; gs?.ballSpeed = (gs?.ballSpeed ?: BASE_SPEED) * 0.82f; savePrefs() }; startNextLevel() }
                "shop_shield" -> { if (coinsBank >= 12) { coinsBank -= 12; gs?.fx?.shield = 10f;             savePrefs() }; startNextLevel() }
                "shop_skip"   -> startNextLevel()
                else -> when {
                    key.startsWith("up_")    -> { applyUpgrade(key); startNextLevel() }
                    key.startsWith("theme_") -> { curThemeKey = key.removePrefix("theme_"); savePrefs() }
                    key.startsWith("diff_")  -> { curDiff     = key.removePrefix("diff_");  savePrefs() }
                    key.startsWith("toggle_") -> handleToggle(key.removePrefix("toggle_"))
                }
            }
            return
        }
        // game-specific taps
        if (screen == Screen.GAME) {
            val st = gs ?: return
            if (!st.launched) {
                st.launched = true
                val rel = (st.aimX - LW / 2f) / (LW / 2f)
                val ang = rel * (40f * PI.toFloat() / 180f)
                st.balls.forEach { b ->
                    b.vx = sin(ang.toDouble()).toFloat() * st.ballSpeed
                    b.vy = -abs(cos(ang.toDouble()).toFloat() * st.ballSpeed)
                }
            }
        }
    }

    private fun handleToggle(key: String) {
        when (key) {
            "sfx"       -> { sfxOn       = !sfxOn;       SoundEngine.enabled = sfxOn }
            "haptic"    -> { hapticOn    = !hapticOn    }
            "particles" -> { particlesOn = !particlesOn }
            "trails"    -> { trailsOn    = !trailsOn    }
        }
        savePrefs()
    }
}

// ─ HUD timer helper (avoids Kotlin "to" in Triple) ───────────────────────────
private fun Pair<String, Float>.second() = second
