package home.aurora

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * A rounded 16:9 app tile. When focused it springs up, tilts in from the
 * direction you came from (Apple TV style), glows in the app's color, and then
 * sways gently while it stays selected.
 */
open class Tile(c: Context, app: Apps.App, apps: Apps, wDp: Int, hDp: Int) : FrameLayout(c) {

    private val ring: View
    private val sheen: View
    private var radius: Float
    private val ringBg: GradientDrawable
    private var glow = 0xFFFFFFFF.toInt()
    private var sway: ValueAnimator? = null
    private var motion = HomeTheme.M_STANDARD

    init {
        radius = dp((if (wDp > 130) 13 else 10).toFloat()).toFloat()
        layoutParams = LinearLayout.LayoutParams(dp(wDp.toFloat()), dp(hDp.toFloat()))
        isFocusable = true
        isClickable = true
        tag = app.pkg
        contentDescription = app.label

        // Rounded clipping for whatever we put inside.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        clipToOutline = true

        val banner = app.banner(apps.pm())
        if (banner != null) {
            val iv = ImageView(c)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageDrawable(banner)
            addView(iv, LayoutParams(-1, -1))
            glow = vivid(averageColor(banner))
        } else {
            val icon = app.icon(apps.pm())
            val base = averageColor(icon)
            glow = vivid(base)
            val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(lighten(base, 0.18f), darken(base, 0.35f)))
            background = bg

            val col = LinearLayout(c)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            val iv = ImageView(c)
            iv.setImageDrawable(icon)
            val s = dp(hDp * 0.42f)
            col.addView(iv, LinearLayout.LayoutParams(s, s))
            val tv = TextView(c)
            tv.text = app.label
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (wDp > 130) 11 else 9).toFloat())
            tv.setSingleLine(true)
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(6f), dp(3f), dp(6f), 0)
            col.addView(tv, LinearLayout.LayoutParams(-1, -2))
            addView(col, LayoutParams(-1, -1))
        }

        // Diagonal light streak that sweeps across once when focused.
        sheen = View(c)
        sheen.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF))
        sheen.rotation = 18f
        sheen.alpha = 0f
        addView(sheen, LayoutParams(dp(wDp * 0.45f), dp(hDp * 2.2f), Gravity.CENTER_VERTICAL))

        ring = View(c)
        ringBg = GradientDrawable()
        ringBg.cornerRadius = radius
        ringBg.setStroke(dp(1.5f), 0xF2FFFFFF.toInt())
        ring.background = ringBg
        ring.alpha = 0f
        addView(ring, LayoutParams(-1, -1))

        // Colored glow: the elevation shadow takes the app's own brand color.
        outlineSpotShadowColor = glow
        outlineAmbientShadowColor = glow

        cameraDistance = 8000 * resources.displayMetrics.density
        setOnFocusChangeListener { _, has ->
            if (sway != null) { sway!!.cancel(); sway = null }
            if (has) {
                // tilt in from the side we arrived from, spring flat, then sway
                val tiltX = if (motion == HomeTheme.M_STILL) 0f else lastDy * (if (motion == HomeTheme.M_BOUNCY) 10f else 7f)
                val tiltY = if (motion == HomeTheme.M_STILL) 0f else -lastDx * (if (motion == HomeTheme.M_BOUNCY) 14f else 8f)
                val scale = if (motion == HomeTheme.M_STILL) 1.055f else if (motion == HomeTheme.M_BOUNCY) 1.12f
                        else if (motion == HomeTheme.M_FLOAT) 1.085f else if (motion == HomeTheme.M_GENTLE) 1.075f else 1.1f
                val duration: Long = if (motion == HomeTheme.M_STILL) 180 else if (motion == HomeTheme.M_FLOAT) 520
                        else if (motion == HomeTheme.M_GENTLE) 300 else 420
                rotationY = tiltY
                rotationX = tiltX
                val enter = animate().scaleX(scale).scaleY(scale).rotationX(0f).rotationY(0f)
                        .translationZ(dp((if (motion == HomeTheme.M_STILL) 14 else 22).toFloat()).toFloat()).setDuration(duration)
                if (motion == HomeTheme.M_STILL) enter.setInterpolator(DecelerateInterpolator(1.8f))
                else enter.setInterpolator(OvershootInterpolator(if (motion == HomeTheme.M_BOUNCY) 2.5f else 1.7f))
                enter.withEndAction(this::startSway).start()
            } else {
                animate().scaleX(1f).scaleY(1f).rotationX(0f).rotationY(0f).translationZ(0f)
                        .setDuration(200).setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
            ring.animate().alpha(if (has) 1f else 0f).setDuration(180).start()
            if (has) {
                if (motion == HomeTheme.M_STILL) return@setOnFocusChangeListener
                val w = if (width > 0) width else dp(wDp.toFloat())
                val leftward = lastDx < 0
                sheen.rotation = if (leftward) -18f else 18f
                sheen.translationX = if (leftward) w * 1.1f else -w * 0.6f
                sheen.alpha = 1f
                sheen.animate().translationX(if (leftward) -w * 0.6f else w * 1.1f).setDuration(750).setStartDelay(90)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { sheen.alpha = 0f }.start()
            }
        }
    }

    /** Theme touches: corner rounding in dp (-1 keeps it) and the focus ring color. */
    fun setLook(cornerDp: Float, ringColor: Int) {
        if (cornerDp >= 0) {
            radius = dp(cornerDp).toFloat()
            ringBg.cornerRadius = radius
            invalidateOutline()
        }
        ringBg.setStroke(dp(if (cornerDp == 0f) 2.5f else 1.5f), ringColor)
    }

    /** A theme can make focus feel calm, weightless, or playful without changing navigation. */
    fun setMotion(style: Int) { motion = style }

    /** Slow figure-eight tilt while selected, so the tile feels alive. */
    private fun startSway() {
        if (!hasFocus() || motion == HomeTheme.M_STILL) return
        val s = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat())
        sway = s
        s.setDuration(if (motion == HomeTheme.M_FLOAT) 7600L else if (motion == HomeTheme.M_GENTLE) 6500L else 5200L)
        s.repeatCount = ValueAnimator.INFINITE
        s.interpolator = null
        s.addUpdateListener { a ->
            val p = a.animatedValue as Float
            val amount = if (motion == HomeTheme.M_FLOAT) 1.2f else if (motion == HomeTheme.M_GENTLE) 1.5f else 2.6f
            rotationY = Math.sin(p.toDouble()).toFloat() * amount
            rotationX = Math.sin((2 * p).toDouble()).toFloat() * amount * 0.54f
        }
        s.start()
    }

    override fun onDetachedFromWindow() {
        sway?.cancel()
        super.onDetachedFromWindow()
    }

    /** Small label pinned to the top-left corner, e.g. "BACK TO". */
    fun setBadge(text: String?) {
        val b = TextView(context)
        b.text = text
        b.isAllCaps = true
        b.letterSpacing = 0.18f
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
        b.setTextColor(0xFFFFFFFF.toInt())
        b.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        b.setPadding(dp(7f), dp(2f), dp(7f), dp(2f))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(100f).toFloat()
        bg.setColor(0xD90B0D14.toInt())
        b.background = bg
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.START)
        lp.setMargins(dp(6f), dp(6f), 0, 0)
        addView(b, childCount - 1, lp)   // under the focus ring
    }

    private fun dp(v: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()
    }

    companion object {
        /** Last D-pad direction, set by the screen hosting the tiles. */
        @JvmField var lastDx = 0
        @JvmField var lastDy = 0

        @JvmStatic
        fun averageColor(d: Drawable): Int {
            val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, 8, 8)
            d.draw(c)
            var r = 0L
            var g = 0L
            var bl = 0L
            var n = 0L
            for (y in 0 until 8) for (x in 0 until 8) {
                val p = b.getPixel(x, y)
                if ((p ushr 24) < 128) continue
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
            }
            b.recycle()
            if (n == 0L) return 0xFF37474F.toInt()
            return 0xFF000000.toInt() or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
        }

        /** Push a color toward full saturation/brightness so it reads as a glow. */
        @JvmStatic
        fun vivid(c: Int): Int {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.15f) return 0xFFFFFFFF.toInt() // white/grey/black banners glow white
            hsv[1] = Math.min(1f, hsv[1] * 1.3f)
            hsv[2] = Math.max(hsv[2], 0.9f)
            return android.graphics.Color.HSVToColor(hsv)
        }

        @JvmStatic
        fun lighten(c: Int, f: Float): Int {
            return mix(c, 0xFFFFFFFF.toInt(), f)
        }

        @JvmStatic
        fun darken(c: Int, f: Float): Int {
            return mix(c, 0xFF000000.toInt(), f)
        }

        private fun mix(a: Int, b: Int, f: Float): Int {
            val r = (((a shr 16) and 0xFF) * (1 - f) + ((b shr 16) and 0xFF) * f).toInt()
            val g = (((a shr 8) and 0xFF) * (1 - f) + ((b shr 8) and 0xFF) * f).toInt()
            val bl = ((a and 0xFF) * (1 - f) + (b and 0xFF) * f).toInt()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl
        }
    }
}
