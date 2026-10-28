package home.aurora

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Aurora-styled pop-ups to replace Android's plain dialogs: a frosted dark panel
 * with rounded corners, light type, white focus pills and the interface sounds.
 *  - [list]: pick one item
 *  - [toggles]: on/off switches
 *  - [message]: a block of text with a "Got it" button
 *  - [input]: type a line of text with the TV keyboard
 *  - [pin]: a 4-digit PIN pad (arrows + OK, or the remote's number keys)
 *  - [qr]: a QR code to scan with a phone
 */
object GlassMenu {

    fun interface OnPick { fun pick(which: Int) }
    fun interface OnToggle { fun toggled(which: Int, on: Boolean) }
    fun interface Value { fun get(): String }
    fun interface Flag { fun get(): Boolean }
    fun interface Setter { fun set(on: Boolean) }
    fun interface OnText { fun text(s: String) }

    /** One row of a settings panel: an on/off switch, a choice you cycle through, or an action. */
    class Item private constructor(
        @JvmField val label: String?,
        @JvmField val flag: Flag?, @JvmField val setter: Setter?,   // switch
        @JvmField val value: Value?, @JvmField val next: Runnable?, // choice
        @JvmField val action: Runnable?,                            // action (closes the panel first)
    ) {
        companion object {
            @JvmStatic fun toggle(label: String?, on: Flag?, set: Setter?): Item = Item(label, on, set, null, null, null)
            @JvmStatic fun choice(label: String?, value: Value?, next: Runnable?): Item = Item(label, null, null, value, next, null)
            @JvmStatic fun action(label: String?, run: Runnable?): Item = Item(label, null, null, null, null, run)
        }
    }

    private val open: MutableList<java.lang.ref.WeakReference<Dialog>> = java.util.ArrayList()

    /** Is any menu showing right now? */
    @JvmStatic
    fun anyShowing(): Boolean {
        for (r in open) {
            val d = r.get()
            if (d != null && d.isShowing) return true
        }
        return false
    }

    /** Close every menu that's up (pressing Home should land on a clean home screen). */
    @JvmStatic
    fun dismissAll() {
        for (r in open) {
            val d = r.get()
            try {
                if (d != null && d.isShowing) d.dismiss()
            } catch (ignored: RuntimeException) { }   // its screen is already gone
        }
        open.clear()
    }

    /** A settings panel mixing switches, choices and actions. */
    @JvmStatic
    fun panel(a: Activity, title: String?, items: List<Item>): Dialog {
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val rows = LinearLayout(a)
        rows.orientation = LinearLayout.VERTICAL
        for (it in items) {
            val row = LinearLayout(a)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(a, 18f), dp(a, 12f), dp(a, 14f), dp(a, 12f))
            val label = text(a, it.label, 16f, t.menuText)
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            val right = text(a, "", 11f, 0xFFFFFFFF.toInt())
            right.typeface = if (t.menuBevel) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            right.letterSpacing = 0.16f
            right.setPadding(dp(a, 14f), dp(a, 5f), dp(a, 14f), dp(a, 5f))
            row.addView(right)
            val paint = Runnable {
                if (it.flag != null) {
                    val on = it.flag.get()
                    right.text = if (on) "ON" else "OFF"
                    right.background = pill(a, if (on) t.on else t.off)
                    right.setTextColor(if (on) t.onText else t.offText)
                } else if (it.value != null) {
                    right.text = it.value.get().uppercase(Locale.getDefault())
                    right.background = pill(a, t.off)
                    right.setTextColor(t.offText)
                } else {
                    right.text = "›"
                    right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    right.background = null
                    right.setTextColor(HomeTheme.alpha(t.menuText, 0.6f))
                }
            }
            paint.run()
            focusable(a, row, label)
            row.setOnClickListener { _ ->
                Sounds.select(a)
                if (it.flag != null) { val on = !it.flag.get(); it.setter!!.set(on) }
                else if (it.next != null) it.next.run()
                else { d.dismiss(); it.action!!.run(); return@setOnClickListener }
                paint.run()
            }
            rows.addView(row)
        }
        panel.addView(scroll(a, rows))
        return show(d, panel, rows.getChildAt(0))
    }

    @JvmStatic
    fun list(a: Activity, title: String?, items: Array<String>, onPick: OnPick): Dialog {
        return list(a, title, items, onPick, null)
    }

    /** Pick one item; holding OK on an item calls `onHold` instead. */
    @JvmStatic
    fun list(a: Activity, title: String?, items: Array<String>, onPick: OnPick, onHold: OnPick?): Dialog {
        if (items.isEmpty()) return message(a, title, "Nothing here yet.", null)   // never an empty box with nothing to select
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val rows = LinearLayout(a)
        rows.orientation = LinearLayout.VERTICAL
        for (i in items.indices) {
            val which = i
            val row = row(a, items[i])
            row.setOnClickListener {
                Sounds.select(a)
                d.dismiss()
                onPick.pick(which)
            }
            if (onHold != null) row.setOnLongClickListener { d.dismiss(); onHold.pick(which); true }
            rows.addView(row)
        }
        panel.addView(scroll(a, rows))
        return show(d, panel, rows.getChildAt(0))
    }

    @JvmStatic
    fun toggles(a: Activity, title: String?, items: Array<String>, on: BooleanArray, onToggle: OnToggle): Dialog {
        if (items.isEmpty()) return message(a, title, "Nothing here yet.", null)
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val rows = LinearLayout(a)
        rows.orientation = LinearLayout.VERTICAL
        for (i in items.indices) {
            val which = i
            val row = LinearLayout(a)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(a, 18f), dp(a, 12f), dp(a, 14f), dp(a, 12f))
            val label = text(a, items[i], 16f, t.menuText)
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            val sw = text(a, "", 11f, 0xFFFFFFFF.toInt())
            sw.typeface = if (t.menuBevel) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            sw.letterSpacing = 0.2f
            sw.setPadding(dp(a, 14f), dp(a, 5f), dp(a, 14f), dp(a, 5f))
            row.addView(sw)
            val state = booleanArrayOf(on[i])
            val paint = Runnable {
                sw.text = if (state[0]) "ON" else "OFF"
                sw.background = pill(a, if (state[0]) t.on else t.off)
                sw.setTextColor(if (state[0]) t.onText else t.offText)
            }
            paint.run()
            focusable(a, row, label)
            row.setOnClickListener {
                Sounds.select(a)
                state[0] = !state[0]
                paint.run()
                onToggle.toggled(which, state[0])
            }
            rows.addView(row)
        }
        panel.addView(scroll(a, rows))
        val done = row(a, "Done")
        done.gravity = Gravity.CENTER
        done.setOnClickListener { Sounds.select(a); d.dismiss() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, 10f)
        panel.addView(done, lp)
        return show(d, panel, rows.getChildAt(0))
    }

    @JvmStatic
    fun message(a: Activity, title: String?, body: String?, onClose: Runnable?): Dialog {
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val bt = text(a, body, 16f, HomeTheme.alpha(t.menuText, 0.9f))
        bt.setLineSpacing(0f, 1.25f)
        bt.setPadding(dp(a, 18f), 0, dp(a, 18f), dp(a, 8f))
        val sv = scroll(a, bt)
        sv.isFocusable = true   // Up from "Got it" lands here; arrows then scroll long text
        panel.addView(sv)
        val ok = row(a, "Got it")
        ok.gravity = Gravity.CENTER
        ok.setOnClickListener { Sounds.select(a); d.dismiss() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, 10f)
        panel.addView(ok, lp)
        if (onClose != null) d.setOnDismissListener { onClose.run() }
        return show(d, panel, ok)
    }

    @JvmStatic
    fun input(a: Activity, title: String?, hint: String?, onDone: OnText): Dialog {
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val e = android.widget.EditText(a)
        e.isSingleLine = true
        e.hint = hint
        e.setTextColor(t.menuText)
        e.setHintTextColor(HomeTheme.alpha(t.menuText, 0.4f))
        e.typeface = t.menuFont
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        e.setPadding(dp(a, 18f), dp(a, 12f), dp(a, 18f), dp(a, 12f))
        if (t.menuBevel) e.background = HomeTheme.Bevel(a, 0xFFFFFFFF.toInt(), true)
        else {
            val bg = GradientDrawable()
            bg.cornerRadius = dp(a, t.rowRadius).toFloat()
            bg.setColor(HomeTheme.alpha(t.menuText, 0.1f))
            bg.setStroke(dp(a, 1f), HomeTheme.alpha(t.menuText, 0.2f))
            e.background = bg
        }
        e.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        val done = Runnable {
            val s = e.text.toString().trim()
            d.dismiss()
            if (s.isNotEmpty()) onDone.text(s)
        }
        e.setOnEditorActionListener { _, _, _ -> done.run(); true }
        panel.addView(e)
        val ok = row(a, "OK")
        ok.gravity = Gravity.CENTER
        ok.setOnClickListener { Sounds.select(a); done.run() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, 10f)
        panel.addView(ok, lp)
        d.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        return show(d, panel, e)
    }

    /** Four digits; calls back once all four are in. */
    @JvmStatic
    fun pin(a: Activity, title: String?, onPin: OnText): Dialog {
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val code = StringBuilder()
        val dots = text(a, "", 30f, t.menuText)
        dots.gravity = Gravity.CENTER
        dots.letterSpacing = 0.5f
        dots.setPadding(0, 0, 0, dp(a, 14f))
        val paint = Runnable {
            val b = StringBuilder()
            for (i in 0 until 4) b.append(if (i < code.length) "●" else "○")
            dots.text = b
        }
        paint.run()
        panel.addView(dots, LinearLayout.LayoutParams(-1, -2))
        val submitted = booleanArrayOf(false)   // fast typing past the 4th digit must not submit twice
        val press = OnPick { k ->   // 0-9 digit, -1 delete
            if (submitted[0]) return@OnPick
            if (k < 0) { if (code.length > 0) code.setLength(code.length - 1) }
            else if (code.length < 4) code.append(k)
            paint.run()
            if (code.length == 4) {
                submitted[0] = true
                val c = code.toString()
                dots.postDelayed({ d.dismiss(); onPin.text(c) }, 150)
            }
        }
        val keys = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        var first: View? = null
        for (r in 0 until 4) {
            val line = LinearLayout(a)
            for (c in 0 until 3) {
                val k = keys[r * 3 + c]
                val key = if (k.isEmpty()) TextView(a) else row(a, k)
                key.gravity = Gravity.CENTER
                key.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                if (!k.isEmpty()) {
                    val v = if (k == "⌫") -1 else Integer.parseInt(k)
                    key.setOnClickListener { Sounds.click(a); press.pick(v) }
                    if (first == null) first = key
                }
                val lp = LinearLayout.LayoutParams(0, -2, 1f)
                lp.setMargins(dp(a, 4f), dp(a, 2f), dp(a, 4f), dp(a, 2f))
                line.addView(key, lp)
            }
            panel.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
        d.setOnKeyListener { _, code2, e ->
            if (e.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (code2 >= android.view.KeyEvent.KEYCODE_0 && code2 <= android.view.KeyEvent.KEYCODE_9) { press.pick(code2 - android.view.KeyEvent.KEYCODE_0); return@setOnKeyListener true }
            if (code2 == android.view.KeyEvent.KEYCODE_DEL) { press.pick(-1); return@setOnKeyListener true }
            false
        }
        return show(d, panel, first)
    }

    /** A QR code for `text`, with a line of explanation under it. */
    @JvmStatic
    fun qr(a: Activity, title: String?, text: String, body: String?): Dialog {
        val t = theme(a)
        val d = base(a)
        val panel = panel(a, title)
        val q = QrCode.encode(text)
        val px = Math.max(4, dp(a, 220f) / (q.size + 8))
        val side = (q.size + 8) * px
        val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFFFFFFFF.toInt())
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        p.color = 0xFF000000.toInt()
        for (y in 0 until q.size)
            for (x in 0 until q.size)
                if (q.get(x, y)) c.drawRect(((x + 4) * px).toFloat(), ((y + 4) * px).toFloat(), ((x + 5) * px).toFloat(), ((y + 5) * px).toFloat(), p)
        val img = android.widget.ImageView(a)
        img.setImageBitmap(bmp)
        val round = GradientDrawable()
        round.cornerRadius = dp(a, 14f).toFloat()
        round.setColor(0xFFFFFFFF.toInt())
        img.background = round
        img.clipToOutline = true
        val ilp = LinearLayout.LayoutParams(side, side)
        ilp.gravity = Gravity.CENTER_HORIZONTAL
        panel.addView(img, ilp)
        val bt = text(a, body, 15f, HomeTheme.alpha(t.menuText, 0.9f))
        bt.gravity = Gravity.CENTER
        bt.setLineSpacing(0f, 1.25f)
        bt.setPadding(dp(a, 18f), dp(a, 14f), dp(a, 18f), dp(a, 4f))
        panel.addView(bt)
        val ok = row(a, "Done")
        ok.gravity = Gravity.CENTER
        ok.setOnClickListener { Sounds.select(a); d.dismiss() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(a, 8f)
        panel.addView(ok, lp)
        return show(d, panel, ok)
    }

    // ------------------------------------------------------------------ pieces

    /** Home screen menus follow its theme; everything else (the games) keeps the Aurora look. */
    @JvmStatic
    fun theme(a: Activity): HomeTheme {
        return (a as? HomeActivity)?.theme ?: HomeTheme.aurora()
    }

    private fun base(a: Activity): Dialog {
        val d = Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        val w = d.window!!
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.setDimAmount(theme(a).dim)
        if (Build.VERSION.SDK_INT >= 31) {
            // Real blur of whatever is behind, where the TV supports it; harmless where it doesn't.
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            w.attributes.setBlurBehindRadius(dp(a, 24f))
        }
        return d
    }

    private fun panel(a: Activity, title: String?): LinearLayout {
        val th = theme(a)
        val p = LinearLayout(a)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(dp(a, 18f), dp(a, 20f), dp(a, 18f), dp(a, 18f))
        if (th.menuBevel) {
            p.background = HomeTheme.Bevel(a, th.menuTop, false)
            p.setPadding(dp(a, 4f), dp(a, 4f), dp(a, 4f), dp(a, 12f))
        } else {
            val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(th.menuTop, th.menuBottom))
            bg.cornerRadius = dp(a, th.menuRadius).toFloat()
            if (th.menuEdge != 0) bg.setStroke(dp(a, if (th.id == "retro") 3f else 1f), th.menuEdge)
            p.background = bg
        }
        p.elevation = dp(a, 24f).toFloat()
        if (th.titleBar != 0) {
            // old Windows title bar: dark blue strip, bold white title, a close box
            val bar = LinearLayout(a)
            bar.gravity = Gravity.CENTER_VERTICAL
            bar.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(th.titleBar, 0xFF1084D0.toInt()))
            bar.setPadding(dp(a, 8f), dp(a, 5f), dp(a, 5f), dp(a, 5f))
            val t = text(a, title, 15f, 0xFFFFFFFF.toInt())
            t.typeface = th.menuTitle
            bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            val x = text(a, "✕", 11f, 0xFF000000.toInt())
            x.gravity = Gravity.CENTER
            x.background = HomeTheme.Bevel(a, 0xFFC0C0C0.toInt(), false)
            bar.addView(x, LinearLayout.LayoutParams(dp(a, 22f), dp(a, 20f)))
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.bottomMargin = dp(a, 10f)
            p.addView(bar, lp)
            return p
        }
        val t = text(a, title, 22f, th.menuText)
        t.typeface = th.menuTitle
        t.setPadding(dp(a, 18f), 0, dp(a, 18f), dp(a, 14f))
        p.addView(t)
        return p
    }

    /** Scrolls when content is taller than ~2/3 of the screen, so buttons stay visible. */
    private fun scroll(a: Activity, content: View): ScrollView {
        val max = (a.resources.displayMetrics.heightPixels * 0.62f).toInt()
        val sv = object : ScrollView(a) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST))
            }
        }
        sv.isVerticalScrollBarEnabled = false
        sv.clipToPadding = false
        sv.addView(content)
        return sv
    }

    private fun row(a: Activity, label: String?): TextView {
        val t = text(a, label, 16f, theme(a).menuText)
        t.setPadding(dp(a, 18f), dp(a, 13f), dp(a, 18f), dp(a, 13f))
        focusable(a, t, t)
        return t
    }

    /** White pill + dark text when focused, like the home screen buttons. */
    private fun focusable(a: Activity, v: View, label: TextView) {
        val th = theme(a)
        v.isFocusable = true
        v.isClickable = true
        val bg = GradientDrawable()
        bg.cornerRadius = dp(a, th.rowRadius).toFloat()
        bg.setColor(0x00000000)
        v.background = bg
        v.setOnFocusChangeListener { x, has ->
            if (has) Sounds.click(a)
            bg.setColor(if (has) th.menuFocus else 0x00000000)
            label.setTextColor(if (has) th.menuFocusText else th.menuText)
            x.animate().scaleX(if (has) 1.02f else 1f).scaleY(if (has) 1.02f else 1f).setDuration(140).start()
        }
    }

    private fun pill(a: Activity, color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = (if (theme(a).rowRadius == 0f) 0 else dp(a, 100f)).toFloat()
        g.setColor(color)
        return g
    }

    private fun text(a: Activity, s: String?, sp: Float, color: Int): TextView {
        val t = TextView(a)
        t.text = s
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        t.typeface = theme(a).menuFont
        return t
    }

    private fun show(d: Dialog, panel: LinearLayout, firstFocus: View?): Dialog {
        val a = if (d.ownerActivity != null) d.ownerActivity!! else panel.context as Activity
        d.setContentView(panel, android.view.ViewGroup.LayoutParams(dp(a, 460f), -2))
        Sounds.quietFor(250)   // no click for the first item getting focus
        panel.alpha = 0f
        panel.scaleX = 0.92f
        panel.scaleY = 0.92f
        d.show()
        open.add(java.lang.ref.WeakReference(d))
        d.window!!.setLayout(dp(a, 460f), WindowManager.LayoutParams.WRAP_CONTENT)
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        firstFocus?.requestFocus()
        return d
    }

    private fun dp(a: Activity, v: Float): Int {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.resources.displayMetrics))
    }
}
