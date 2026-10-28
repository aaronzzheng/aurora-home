package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Minesweeper: 9 x 9 with 10 mines, 16 x 9 with 22, or 20 x 11 with 45. OK reveals, hold OK (or Menu) to flag.
 * OK on a number whose flags are all placed clears its other neighbors.
 * The first reveal is always safe.
 */
public class Minesweeper extends Game {

    private int W = 16, H = 9, MINES = 22;
    // Windows 95 colors
    private static final int[] NUM_COLORS = {0, 0xFF0000FF, 0xFF008000, 0xFFFF0000, 0xFF000080,
            0xFF800000, 0xFF008080, 0xFF000000, 0xFF808080};
    private static final int FACE = 0xFFC0C0C0, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF808080, DARK = 0xFF000000;
    private boolean[] mine, open, flag;
    private int[] near;
    private final Random rnd = new Random();
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int cr, cc, boom = -1;
    private boolean placed, longPressed, pressing;
    private long startedAt, finishedMs;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            report();
            if (placed && !over) postDelayed(this, 1000);
        }
    };

    public Minesweeper(Context c) {
        super(c);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    }

    @Override public String title() { return "Minesweeper"; }
    @Override public boolean solo() { return true; }
    @Override protected boolean alternateStart() { return false; }
    @Override public String hint() { return "OK reveals  ·  hold OK to flag  ·  Back for menu"; }

    @Override
    protected void newGame() {
        W = level == 0 ? 9 : level == 1 ? 16 : 20;
        H = level == 0 ? 9 : level == 1 ? 9 : 11;
        MINES = level == 0 ? 10 : level == 1 ? 22 : 45;
        mine = new boolean[W * H];
        open = new boolean[W * H];
        flag = new boolean[W * H];
        near = new int[W * H];
        cr = H / 2;
        cc = W / 2;
        placed = false;
        boom = -1;
        finishedMs = 0;
        removeCallbacks(tick);
        report();
        if (host != null) host.onInfo(MINES + " mines");
    }

    private String bestKey() { return level == 1 ? "best_mines_s" : "best_mines_s_" + level; }

    private long elapsed() {
        if (!placed) return 0;
        return finishedMs > 0 ? finishedMs : SystemClock.uptimeMillis() - startedAt;
    }

    private void report() {
        if (host == null) return;
        int best = prefs().getInt(bestKey(), 0);
        host.onScore(clock(elapsed() / 1000), best > 0 ? "Best  " + clock(best) : "Best  —");
    }

    private static String clock(long s) { return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60; }

    /** Place mines after the first reveal, keeping that square and its neighbors clear. */
    private void place(int safe) {
        int sr = safe / W, sc = safe % W, n = 0;
        while (n < MINES) {
            int i = rnd.nextInt(W * H), r = i / W, c = i % W;
            if (mine[i] || (Math.abs(r - sr) <= 1 && Math.abs(c - sc) <= 1)) continue;
            mine[i] = true;
            n++;
        }
        for (int i = 0; i < W * H; i++) {
            int k = 0;
            for (int j : neighbors(i)) if (mine[j]) k++;
            near[i] = k;
        }
        placed = true;
        startedAt = SystemClock.uptimeMillis();
        post(tick);
    }

    private int[] neighbors(int i) {
        int r = i / W, c = i % W, n = 0;
        int[] out = new int[8];
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            if ((dr | dc) == 0) continue;
            int rr = r + dr, cc2 = c + dc;
            if (rr >= 0 && rr < H && cc2 >= 0 && cc2 < W) out[n++] = rr * W + cc2;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private void reveal(int i) {
        if (!placed) place(i);
        if (flag[i]) return;
        if (open[i]) { chord(i); return; }
        if (mine[i]) { lose(i); return; }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(i);
        while (!q.isEmpty()) {
            int x = q.poll();
            if (open[x] || flag[x]) continue;
            open[x] = true;
            if (near[x] == 0) for (int j : neighbors(x)) if (!open[j] && !mine[j]) q.add(j);
        }
        checkWin();
    }

    private void chord(int i) {
        if (near[i] == 0) return;
        int flags = 0;
        for (int j : neighbors(i)) if (flag[j]) flags++;
        if (flags != near[i]) return;
        for (int j : neighbors(i)) {
            if (flag[j] || open[j]) continue;
            if (mine[j]) { lose(j); return; }
            reveal(j);
            if (over) return;
        }
    }

    private void lose(int i) {
        boom = i;
        finishedMs = SystemClock.uptimeMillis() - startedAt;
        removeCallbacks(tick);
        host.onInfo("Boom");
        finish(2);
    }

    private void checkWin() {
        int closed = 0;
        for (boolean o : open) if (!o) closed++;
        if (closed != MINES) return;
        finishedMs = SystemClock.uptimeMillis() - startedAt;
        removeCallbacks(tick);
        int secs = (int) (finishedMs / 1000), best = prefs().getInt(bestKey(), 0);
        if (best == 0 || secs < best) {
            prefs().edit().putInt(bestKey(), secs).apply();
            host.onInfo("New best time!");
        } else {
            host.onInfo("Cleared!");
        }
        report();
        finish(1);
    }

    private void toggleFlag() {
        int i = cr * W + cc;
        if (open[i]) return;
        flag[i] = !flag[i];
        int f = 0;
        for (boolean b : flag) if (b) f++;
        host.onInfo((MINES - f) + " mines left to flag");
        invalidate();
    }

    // ------------------------------------------------------------------ input

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (!over && isOk(code)) {
            if (e.getRepeatCount() == 0) {
                e.startTracking();
                longPressed = false;
                pressing = true;
            }
            return true;
        }
        if (!over && code == KeyEvent.KEYCODE_MENU) { toggleFlag(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (!over && isOk(code)) {
            longPressed = true;
            toggleFlag();
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (isOk(code) && pressing) {
            pressing = false;
            if (!over && !longPressed && !e.isCanceled()) {
                home.aurora.Sounds.select(getContext());
                reveal(cr * W + cc);
                invalidate();
            }
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        cr = Math.max(0, Math.min(H - 1, cr + dy));
        cc = Math.max(0, Math.min(W - 1, cc + dx));
        return true;
    }

    @Override protected boolean playAtCursor() { return false; }
    @Override protected void cpuMove() { }
    @Override protected int winner() { return -1; }

    // ------------------------------------------------------------------ drawing
    //
    // One layout, dressed per look: a case holding a counter strip (mines left, the face
    // button, time) above the field. Classic is a pixel-crisp Windows 95 Minesweeper; the
    // other looks redraw the case, counters, face, cells, numbers, mines and flags.

    private static final Typeface SANS_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface SANS_MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface SANS_LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface COND = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface SERIF = Typeface.create("serif", Typeface.NORMAL);
    private static final Typeface SERIF_BOLD = Typeface.create("serif", Typeface.BOLD);
    private static final Typeface SERIF_ITALIC = Typeface.create("serif", Typeface.ITALIC);
    private static final Typeface MONO = Typeface.create("monospace", Typeface.NORMAL);
    private static final Typeface MONO_BOLD = Typeface.create("monospace", Typeface.BOLD);

    // number colors per look (index 1..8)
    private static final int[] AURORA_NUM = {0, 0xFF9CCBFF, 0xFFA8E6B4, 0xFFFFB27A, 0xFFCDB8FF, 0xFFFF9A8A, 0xFF8EDDD6, 0xFFFFFFFF, 0xFFC8C8D0};
    private static final int[] LIGHT_NUM = {0, 0xFF2F6DB8, 0xFF3A8A4A, 0xFFD8573A, 0xFF4A3A9A, 0xFF9A2A22, 0xFF2A8A8A, 0xFF1C1C1E, 0xFF8E8E93};
    private static final int[] RETRO_NUM = {0, 0xFF9AD0C4, 0xFFC4D25A, 0xFFFF8A4A, 0xFFF2C24A, 0xFFFF6A50, 0xFF7FC8B8, 0xFFF6EBD0, 0xFFB8A890};
    private static final int[] ART_NUM = {0, 0xFFF4EEE2, 0xFFE8C87A, 0xFFE89A88, 0xFFB4C4DC, 0xFFE07060, 0xFFA8D0C0, 0xFFFFFFFF, 0xFFA89A80};
    private static final int[] SPACE_NUM = {0, 0xFF6FE08A, 0xFFFFC56A, 0xFFFFA24A, 0xFFE8EAE6, 0xFFE8705A, 0xFF8AD0C8, 0xFFFFE6B0, 0xFF8A9096};
    private static final int[] COZY_NUM = {0, 0xFFF6E6CC, 0xFFC8DC8A, 0xFFFFB04A, 0xFFA8C8E0, 0xFFFF8A6A, 0xFF90C8C0, 0xFFFFFFFF, 0xFFB8A080};

    private static final int[] SEG = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    private int[] numColors(GameSkin s) {
        switch (s.id) {
            case "classic": return NUM_COLORS;
            case "light": return LIGHT_NUM;
            case "retro": return RETRO_NUM;
            case "art": return ART_NUM;
            case "space": return SPACE_NUM;
            case "cozy": return COZY_NUM;
            case "aurora": return AURORA_NUM;
            default: return null;   // minimal and cs: all ink
        }
    }

    @Override protected int cursorColor() { return skin().bevel ? 0xFF000080 : skin().accent; }

    @Override
    protected void onDraw(Canvas c) {
        GameSkin s = skin();
        boolean win95 = s.bevel;
        fill.setShader(null);
        fill.setAntiAlias(!win95);
        stroke.setAntiAlias(!win95);
        stroke.setStrokeCap(win95 ? Paint.Cap.SQUARE : Paint.Cap.ROUND);

        float cell = Math.min(getWidth() / (W + 1.0f), getHeight() / (H + 3.1f));
        if (win95) cell = (float) Math.floor(cell);
        float pad = win95 ? Math.round(cell * 0.4f) : cell * 0.42f;
        float headH = win95 ? Math.round(cell * 1.4f) : cell * 1.4f;
        float bw = cell * W, bh = cell * H;
        float caseW = bw + pad * 2, caseH = headH + bh + pad * 3;
        float l = Math.round((getWidth() - caseW) / 2), t = Math.round((getHeight() - caseH) / 2);
        float r = l + caseW, b = t + caseH;
        float hl = l + pad, ht = t + pad, hr = r - pad, hb = ht + headH;
        float fl = l + pad, ft = hb + pad;

        drawCase(c, s, l, t, r, b, hl, ht, hr, hb, fl, ft, fl + bw, ft + bh, cell, pad);

        int flags = 0;
        for (boolean f : flag) if (f) flags++;
        float chh = win95 ? Math.round(headH * 0.6f) : headH * 0.6f, cy = (ht + hb) / 2;
        float cw = counterWidth(chh);
        float inset = win95 ? Math.round(headH * 0.22f) : headH * 0.3f;
        counter(c, s, hl + inset, Math.round(cy - chh / 2), chh, MINES - flags, false);
        counter(c, s, hr - inset - cw, Math.round(cy - chh / 2), chh, (int) Math.min(999, elapsed() / 1000), true);
        faceButton(c, s, (hl + hr) / 2, cy, win95 ? Math.round(headH * 0.74f) : headH * 0.76f);

        int[] nums = numColors(s);
        int cur = over ? -1 : cr * W + cc;
        for (int i = 0; i < W * H; i++) {
            float x = fl + (i % W) * cell, y = ft + (i / W) * cell;
            boolean showMine = mine[i] && over && !flag[i];
            boolean wrong = flag[i] && over && !mine[i];
            if (s.glow) { termCell(c, s, x, y, cell, i, showMine, wrong, i == cur); continue; }
            if (open[i] || showMine) openCell(c, s, x, y, cell, i, showMine, nums);
            else closedCell(c, s, x, y, cell, i, flag[i], wrong);
        }
        if (cur >= 0 && !s.glow) cellCursor(c, s, fl + cc * cell, ft + cr * cell, cell);

        fill.setAntiAlias(true);
        stroke.setAntiAlias(true);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setPathEffect(null);
        fill.setShader(null);
    }

    // ------------------------------------------------------------------ case

    private void drawCase(Canvas c, GameSkin s, float l, float t, float r, float b,
                          float hl, float ht, float hr, float hb, float fl, float ft, float fr, float fb,
                          float cell, float pad) {
        switch (s.id) {
            case "classic": {
                bevel(c, l, t, r, b, true, 3, true);
                bevel(c, hl, ht, hr, hb, false, 2, false);
                bevel(c, fl - 3, ft - 3, fr + 3, fb + 3, false, 3, false);
                break;
            }
            case "retro": {
                float rad = cell * 0.55f;
                shadow(c, l, t, r, b, rad, 0x66000000, cell * 0.12f);
                fill.setShader(new LinearGradient(0, t, 0, b, 0xFF7A4826, 0xFF3E2210, Shader.TileMode.CLAMP));
                rr(c, l, t, r, b, rad, 0xFF000000);
                fill.setShader(null);
                grain(c, l, t, r, b, 0x16FFE0B0, 17);
                stroke.setStrokeWidth(Math.max(2, cell * 0.04f));
                stroke.setColor(0x66FFD8A0);
                rf.set(l + cell * 0.08f, t + cell * 0.08f, r - cell * 0.08f, b - cell * 0.08f);
                c.drawRoundRect(rf, rad * 0.85f, rad * 0.85f, stroke);
                // cream enamel strip with an orange and mustard racing stripe
                rr(c, hl, ht, hr, hb, cell * 0.35f, 0xFFF2E6C8);
                fill.setColor(0xFFC4552A);
                c.drawRect(hl + cell * 0.35f, hb - cell * 0.2f, hr - cell * 0.35f, hb - cell * 0.13f, fill);
                fill.setColor(0xFFE0A526);
                c.drawRect(hl + cell * 0.35f, hb - cell * 0.12f, hr - cell * 0.35f, hb - cell * 0.06f, fill);
                rr(c, fl - cell * 0.12f, ft - cell * 0.12f, fr + cell * 0.12f, fb + cell * 0.12f, cell * 0.3f, 0xFF2E1A0C);
                break;
            }
            case "art": {
                shadow(c, l, t, r, b, 0, 0x80000000, cell * 0.18f);
                fill.setShader(new LinearGradient(l, t, r * 0.3f + l * 0.7f, b, new int[]{0xFFF2D48A, 0xFFB8893A, 0xFFE8C06A, 0xFF8A6420},
                        new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.MIRROR));
                c.drawRect(l, t, r, b, fill);
                fill.setShader(null);
                stroke.setStrokeWidth(Math.max(1, cell * 0.03f));
                stroke.setColor(0xFF5A3E14);
                c.drawRect(l + 1, t + 1, r - 1, b - 1, stroke);
                stroke.setColor(0xCCFFF0C0);
                float in = pad * 0.35f;
                c.drawRect(l + in, t + in, r - in, b - in, stroke);
                // ebony plaque for the counters, ebony under the ivory tiles
                fill.setColor(0xFF1C1612);
                c.drawRect(hl, ht, hr, hb, fill);
                c.drawRect(fl - 2, ft - 2, fr + 2, fb + 2, fill);
                stroke.setColor(0xFF8A6420);
                c.drawRect(hl + cell * 0.08f, ht + cell * 0.08f, hr - cell * 0.08f, hb - cell * 0.08f, stroke);
                break;
            }
            case "space": {
                float rad = cell * 0.2f;
                shadow(c, l, t, r, b, rad, 0x99000000, cell * 0.12f);
                fill.setShader(new LinearGradient(0, t, 0, b, new int[]{0xFF8A9096, 0xFF4E5359, 0xFF34383D},
                        new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
                rr(c, l, t, r, b, rad, 0xFF000000);
                fill.setShader(null);
                stroke.setStrokeWidth(1.5f);
                stroke.setColor(0x55FFFFFF);
                rf.set(l + 1, t + 1, r - 1, b - 1);
                c.drawRoundRect(rf, rad, rad, stroke);
                float sr = pad * 0.16f;
                screw(c, l + pad * 0.5f, t + pad * 0.5f, sr);
                screw(c, r - pad * 0.5f, t + pad * 0.5f, sr);
                screw(c, l + pad * 0.5f, b - pad * 0.5f, sr);
                screw(c, r - pad * 0.5f, b - pad * 0.5f, sr);
                screen(c, hl, ht, hr, hb, cell * 0.12f);
                screen(c, fl - cell * 0.1f, ft - cell * 0.1f, fr + cell * 0.1f, fb + cell * 0.1f, cell * 0.12f);
                // faint sensor grid
                stroke.setStrokeWidth(1);
                stroke.setColor(0x1C6FE08A);
                for (int k = 1; k < W; k++) c.drawLine(fl + k * cell, ft, fl + k * cell, fb, stroke);
                for (int k = 1; k < H; k++) c.drawLine(fl, ft + k * cell, fr, ft + k * cell, stroke);
                num.setTypeface(MONO_BOLD);
                num.setTextSize(pad * 0.55f);
                num.setColor(0xB3C8CDD2);
                num.setTextAlign(Paint.Align.LEFT);
                c.drawText("SENSOR ARRAY  " + W + "x" + H + "  ·  " + MINES + " CONTACTS", fl + pad * 0.6f, fb + pad * 0.72f, num);
                num.setTextAlign(Paint.Align.RIGHT);
                c.drawText("MSW-7", fr - pad * 0.6f, fb + pad * 0.72f, num);
                num.setTextAlign(Paint.Align.CENTER);
                break;
            }
            case "cozy": {
                float rad = cell * 0.3f;
                shadow(c, l, t, r, b, rad, 0x99000000, cell * 0.22f);
                fill.setShader(new LinearGradient(l, t, l, b, 0xFFA8703C, 0xFF5E3A1C, Shader.TileMode.CLAMP));
                rr(c, l, t, r, b, rad, 0xFF000000);
                fill.setShader(null);
                grain(c, l, t, r, b, 0x1A2A1406, 23);
                stroke.setStrokeWidth(Math.max(1, cell * 0.025f));
                stroke.setColor(0x40FFD8A0);
                rf.set(l + 1, t + 1, r - 1, b - 1);
                c.drawRoundRect(rf, rad, rad, stroke);
                float k = pad * 0.55f;
                brass(c, l + pad * 0.5f, t + pad * 0.5f, k * 0.32f);
                brass(c, r - pad * 0.5f, t + pad * 0.5f, k * 0.32f);
                brass(c, l + pad * 0.5f, b - pad * 0.5f, k * 0.32f);
                brass(c, r - pad * 0.5f, b - pad * 0.5f, k * 0.32f);
                rr(c, hl, ht, hr, hb, cell * 0.2f, 0xFF3A2212);
                rr(c, fl - cell * 0.08f, ft - cell * 0.08f, fr + cell * 0.08f, fb + cell * 0.08f, cell * 0.15f, 0xFF1E3420);
                break;
            }
            case "minimal": {
                stroke.setStrokeWidth(1);
                stroke.setColor(s.boardEdge);
                c.drawLine(hl, hb + pad * 0.5f, hr, hb + pad * 0.5f, stroke);
                break;
            }
            case "cs": {
                fill.setColor(s.board);
                c.drawRect(l, t, r, b, fill);
                stroke.setStrokeWidth(Math.max(1, dp(1.5f)));
                stroke.setColor(s.frameHi);
                stroke.setShadowLayer(dp(6), 0, 0, alpha(s.frameHi, 0x99));
                c.drawRect(l, t, r, b, stroke);
                c.drawLine(l, hb + pad * 0.5f, r, hb + pad * 0.5f, stroke);
                stroke.clearShadowLayer();
                num.setTypeface(MONO_BOLD);
                num.setTextSize(pad * 0.62f);
                num.setTextAlign(Paint.Align.LEFT);
                String title = " ~/minesweeper --grid " + W + "x" + H + " ";
                float tw = num.measureText(title);
                fill.setColor(s.board);
                c.drawRect(l + pad, t - pad * 0.4f, l + pad + tw, t + pad * 0.4f, fill);
                num.setColor(s.ink);
                num.setShadowLayer(dp(4), 0, 0, alpha(s.accent, 0x99));
                c.drawText(title, l + pad, t - (num.descent() + num.ascent()) / 2, num);
                num.clearShadowLayer();
                num.setTextAlign(Paint.Align.CENTER);
                break;
            }
            case "light": {
                float rad = cell * 0.45f;
                shadow(c, l, t, r, b, rad, 0x22000000, cell * 0.25f);
                rr(c, l, t, r, b, rad, 0xF7FFFFFF);
                rr(c, fl - cell * 0.12f, ft - cell * 0.12f, fr + cell * 0.12f, fb + cell * 0.12f, cell * 0.3f, 0xFFECE8E1);
                break;
            }
            default: { // aurora: frosted glass
                float rad = cell * 0.45f;
                rr(c, l, t, r, b, rad, 0x38FFFFFF);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x4DFFFFFF);
                rf.set(l, t, r, b);
                c.drawRoundRect(rf, rad, rad, stroke);
                rr(c, fl - cell * 0.12f, ft - cell * 0.12f, fr + cell * 0.12f, fb + cell * 0.12f, cell * 0.3f, 0x33000814);
                break;
            }
        }
    }

    private void rr(Canvas c, float l, float t, float r, float b, float rad, int color) {
        if (fill.getShader() == null) fill.setColor(color);
        else fill.setColor(0xFF000000);
        rf.set(l, t, r, b);
        if (rad <= 0) c.drawRect(rf, fill); else c.drawRoundRect(rf, rad, rad, fill);
    }

    private void shadow(Canvas c, float l, float t, float r, float b, float rad, int color, float blur) {
        fill.setColor(0xFF000000);
        fill.setShadowLayer(blur, 0, blur * 0.4f, color);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, fill);
        fill.clearShadowLayer();
    }

    /** A few long wavy lines of wood grain, the same every frame. */
    private void grain(Canvas c, float l, float t, float r, float b, int color, long seed) {
        Random g = new Random(seed);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(1, (b - t) * 0.004f));
        c.save();
        rf.set(l, t, r, b);
        c.clipRect(rf);
        for (int k = 0; k < 18; k++) {
            float y = t + (b - t) * g.nextFloat(), a = (b - t) * 0.01f * (1 + g.nextFloat());
            path.reset();
            path.moveTo(l, y);
            for (int j = 1; j <= 6; j++) path.quadTo(l + (r - l) * (j - 0.5f) / 6, y + (g.nextFloat() - 0.5f) * a * 4, l + (r - l) * j / 6, y);
            c.drawPath(path, stroke);
        }
        c.restore();
    }

    private void screw(Canvas c, float cx, float cy, float r) {
        fill.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFD8DCE0, 0xFF4A4F55, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(Math.max(1, r * 0.25f));
        stroke.setColor(0xFF2A2D31);
        c.drawLine(cx - r * 0.6f, cy + r * 0.6f, cx + r * 0.6f, cy - r * 0.6f, stroke);
    }

    private void brass(Canvas c, float cx, float cy, float r) {
        fill.setShader(new android.graphics.RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f,
                new int[]{0xFFFFE6A0, 0xFFC8A050, 0xFF6E4A18}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);
    }

    /** Dark glass display recessed into the bezel. */
    private void screen(Canvas c, float l, float t, float r, float b, float rad) {
        rr(c, l - 2, t - 2, r + 2, b + 2, rad + 2, 0xFF1A1D20);
        rr(c, l, t, r, b, rad, 0xFF0A0C0E);
        fill.setShader(new LinearGradient(0, t, 0, t + (b - t) * 0.5f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rr(c, l, t, r, t + (b - t) * 0.5f, rad, 0);
        fill.setShader(null);
    }

    // ------------------------------------------------------------------ counters

    private static float counterWidth(float h) { return h * 1.85f; }

    private void counter(Canvas c, GameSkin s, float x, float y, float h, int v, boolean time) {
        float w = counterWidth(h);
        switch (s.id) {
            case "classic": {
                fill.setColor(SHADOW);
                c.drawRect(x - 1, y - 1, x + w, y, fill);
                c.drawRect(x - 1, y - 1, x, y + h, fill);
                fill.setColor(LIGHT);
                c.drawRect(x, y + h, x + w + 1, y + h + 1, fill);
                c.drawRect(x + w, y, x + w + 1, y + h + 1, fill);
                fill.setColor(DARK);
                c.drawRect(x, y, x + w, y + h, fill);
                leds(c, x, y, w, h, v, 0xFFFF0000, 0xFF3A0000, false);
                return;
            }
            case "space": {
                screen(c, x, y, x + w, y + h, h * 0.08f);
                int col = time ? 0xFF6FE08A : s.accent;
                leds(c, x, y, w, h, v, col, alpha(col, 0x1F), true);
                label(c, s, time ? "T+ SEC" : "CONTACTS", x + w / 2, y - h * 0.19f, h * 0.24f, alpha(col, 0xB3), MONO_BOLD);
                return;
            }
            case "retro": {
                rr(c, x - h * 0.08f, y - h * 0.08f, x + w + h * 0.08f, y + h + h * 0.08f, h * 0.3f, 0xFF8A6A3E);
                rr(c, x, y, x + w, y + h, h * 0.24f, 0xFF241008);
                leds(c, x, y, w, h, v, 0xFFFF7A30, 0xFF3E1A0A, true);
                return;
            }
            case "art": {
                rr(c, x, y, x + w, y + h, 0, 0xFF120E0B);
                stroke.setStrokeWidth(Math.max(1, h * 0.03f));
                stroke.setColor(0xFFB8893A);
                c.drawRect(x, y, x + w, y + h, stroke);
                text(c, time ? clock(v) : String.valueOf(v), x + w / 2, y + h * 0.47f, h * 0.62f, 0xFFE8C87A, SERIF_BOLD);
                label(c, s, time ? "tempus" : "mines", x + w / 2, y + h * 0.9f, h * 0.18f, 0xFFB8893A, SERIF_ITALIC);
                return;
            }
            case "cozy": {
                fill.setShader(new LinearGradient(x, y, x, y + h, new int[]{0xFFF2D48A, 0xFFC8A050, 0xFF8A6420},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                rr(c, x, y, x + w, y + h, h * 0.14f, 0);
                fill.setShader(null);
                stroke.setStrokeWidth(Math.max(1, h * 0.03f));
                stroke.setColor(0xFF5A3A10);
                rf.set(x, y, x + w, y + h);
                c.drawRoundRect(rf, h * 0.14f, h * 0.14f, stroke);
                fill.setColor(0xFF6E4A18);
                c.drawCircle(x + h * 0.14f, y + h / 2, h * 0.04f, fill);
                c.drawCircle(x + w - h * 0.14f, y + h / 2, h * 0.04f, fill);
                text(c, time ? clock(v) : String.valueOf(v), x + w / 2, y + h * 0.5f + h * 0.02f, h * 0.62f, 0x66FFF0C0, SERIF_BOLD);
                text(c, time ? clock(v) : String.valueOf(v), x + w / 2, y + h * 0.5f, h * 0.62f, 0xFF3A2208, SERIF_BOLD);
                return;
            }
            case "minimal": {
                text(c, time ? clock(v) : String.valueOf(v), x + w / 2, y + h * 0.42f, h * 0.7f, s.ink, SANS_LIGHT);
                label(c, s, time ? "TIME" : "MINES", x + w / 2, y + h * 1.0f, h * 0.22f, s.inkDim, SANS_MED);
                return;
            }
            case "cs": {
                String txt = (time ? "T:" : "M:") + String.format(java.util.Locale.ROOT, "%03d", Math.max(-99, v));
                num.setShadowLayer(dp(5), 0, 0, alpha(s.accent, 0x99));
                text(c, txt, x + w / 2, y + h / 2, h * 0.55f, s.ink, MONO_BOLD);
                num.clearShadowLayer();
                return;
            }
            case "light": {
                rr(c, x, y, x + w, y + h, h * 0.5f, 0xFFF1EEE9);
                text(c, time ? clock(v) : String.valueOf(v), x + w / 2, y + h * 0.5f, h * 0.55f, 0xFF1C1C1E, SANS_MED);
                return;
            }
            default: {
                rr(c, x, y, x + w, y + h, h * 0.5f, 0x33000814);
                text(c, time ? clock(v) : String.valueOf(v), x + w / 2, y + h * 0.5f, h * 0.55f, 0xFFFFFFFF, SANS_LIGHT);
            }
        }
    }

    private void text(Canvas c, String str, float cx, float cy, float size, int color, Typeface tf) {
        num.setTypeface(tf);
        num.setTextSize(size);
        num.setColor(color);
        num.setTextAlign(Paint.Align.CENTER);
        c.drawText(str, cx, cy - (num.descent() + num.ascent()) / 2, num);
    }

    private void label(Canvas c, GameSkin s, String str, float cx, float cy, float size, int color, Typeface tf) {
        num.setLetterSpacing(0.15f);
        text(c, str, cx, cy, size, color, tf);
        num.setLetterSpacing(0f);
    }

    /** Three 7-segment digits centered in a box, unlit segments faintly showing. */
    private void leds(Canvas c, float x, float y, float w, float h, int v, int on, int off, boolean glow) {
        String str = String.format(java.util.Locale.ROOT, "%03d", Math.max(-99, Math.min(999, v)));
        float dh = h * 0.8f, dw = dh * 0.52f, gap = dh * 0.12f, total = dw * 3 + gap * 2;
        float sx = x + (w - total) / 2, sy = y + (h - dh) / 2;
        if (s7glow = glow) fill.setShadowLayer(dh * 0.12f, 0, 0, alpha(on, 0x99));
        for (int k = 0; k < 3; k++) {
            char ch = str.charAt(k);
            int bits = ch == '-' ? 0x40 : SEG[ch - '0'];
            digit(c, sx + k * (dw + gap), sy, dw, dh, bits, on, off);
        }
        fill.clearShadowLayer();
        s7glow = false;
    }

    private boolean s7glow;

    private void digit(Canvas c, float x, float y, float w, float h, int bits, int on, int off) {
        float t = w * 0.27f, g = t * 0.12f;
        for (int k = 0; k < 7; k++) {
            boolean lit = ((bits >> k) & 1) != 0;
            if (!lit && s7glow) fill.clearShadowLayer();
            fill.setColor(lit ? on : off);
            path.reset();
            switch (k) {
                case 0: hseg(x + t / 2 + g, x + w - t / 2 - g, y + t / 2, t); break;
                case 1: vseg(x + w - t / 2, y + t / 2 + g, y + h / 2 - g, t); break;
                case 2: vseg(x + w - t / 2, y + h / 2 + g, y + h - t / 2 - g, t); break;
                case 3: hseg(x + t / 2 + g, x + w - t / 2 - g, y + h - t / 2, t); break;
                case 4: vseg(x + t / 2, y + h / 2 + g, y + h - t / 2 - g, t); break;
                case 5: vseg(x + t / 2, y + t / 2 + g, y + h / 2 - g, t); break;
                default: hseg(x + t / 2 + g, x + w - t / 2 - g, y + h / 2, t); break;
            }
            c.drawPath(path, fill);
            if (!lit && s7glow) fill.setShadowLayer(h * 0.12f, 0, 0, alpha(on, 0x99));
        }
    }

    private void hseg(float x0, float x1, float yc, float t) {
        path.moveTo(x0, yc); path.lineTo(x0 + t / 2, yc - t / 2); path.lineTo(x1 - t / 2, yc - t / 2);
        path.lineTo(x1, yc); path.lineTo(x1 - t / 2, yc + t / 2); path.lineTo(x0 + t / 2, yc + t / 2); path.close();
    }

    private void vseg(float xc, float y0, float y1, float t) {
        path.moveTo(xc, y0); path.lineTo(xc + t / 2, y0 + t / 2); path.lineTo(xc + t / 2, y1 - t / 2);
        path.lineTo(xc, y1); path.lineTo(xc - t / 2, y1 - t / 2); path.lineTo(xc - t / 2, y0 + t / 2); path.close();
    }

    // ------------------------------------------------------------------ face button

    private void faceButton(Canvas c, GameSkin s, float cx, float cy, float size) {
        float r = size / 2;
        boolean lost = over && boom >= 0, won = over && boom < 0;
        switch (s.id) {
            case "classic": {
                float x = Math.round(cx - r), y = Math.round(cy - r);
                stroke.setStrokeWidth(1);
                fill.setColor(SHADOW);
                c.drawRect(x - 1, y - 1, x + size + 1, y + size + 1, fill);
                bevel(c, x, y, x + size, y + size, true, 3, false);
                fill.setAntiAlias(true); stroke.setAntiAlias(true);
                fill.setColor(0xFFFFFF00);
                c.drawCircle(cx, cy, r * 0.66f, fill);
                stroke.setStrokeWidth(Math.max(2, r * 0.07f));
                stroke.setColor(DARK);
                c.drawCircle(cx, cy, r * 0.66f, stroke);
                lineFace(c, cx, cy, r * 0.66f, DARK, Math.max(2, r * 0.07f));
                fill.setAntiAlias(false); stroke.setAntiAlias(false);
                return;
            }
            case "retro": {
                fill.setColor(0xFF5A3418);
                c.drawCircle(cx, cy + r * 0.06f, r, fill);
                fill.setShader(new android.graphics.RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.3f,
                        new int[]{0xFFFFD86A, 0xFFE8A922, 0xFFC07A10}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r * 0.94f, fill);
                fill.setShader(null);
                lineFace(c, cx, cy, r * 0.8f, 0xFF4A2410, r * 0.11f);
                return;
            }
            case "art": {
                fill.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFF2D48A, 0xFF8A6420, Shader.TileMode.CLAMP));
                rf.set(cx - r * 0.82f, cy - r, cx + r * 0.82f, cy + r);
                c.drawOval(rf, fill);
                fill.setShader(null);
                fill.setColor(lost ? 0xFF1C1612 : 0xFF6E1E22);
                rf.inset(r * 0.12f, r * 0.12f);
                c.drawOval(rf, fill);
                lineFace(c, cx, cy, r * 0.6f, 0xFFF4EEE2, r * 0.07f);
                return;
            }
            case "space": {
                int col = lost ? 0xFFE8705A : pressing && !over ? s.accent : 0xFF6FE08A;
                screen(c, cx - r, cy - r, cx + r, cy + r, r * 0.15f);
                stroke.setStrokeWidth(r * 0.08f);
                stroke.setColor(col);
                stroke.setShadowLayer(r * 0.25f, 0, 0, alpha(col, 0xAA));
                c.drawCircle(cx, cy - r * 0.08f, r * 0.62f, stroke);
                stroke.clearShadowLayer();
                stroke.setStrokeWidth(Math.max(1, r * 0.03f));
                stroke.setColor(alpha(col, 0x66));
                c.drawCircle(cx, cy - r * 0.08f, r * 0.45f, stroke);
                String st = lost ? "FAIL" : won ? "CLR" : pressing && !over ? "SCAN" : "OK";
                text(c, st, cx, cy - r * 0.08f, r * (st.length() > 3 ? 0.26f : 0.34f), col, MONO_BOLD);
                return;
            }
            case "cozy": {
                brass(c, cx, cy, r);
                stroke.setStrokeWidth(r * 0.05f);
                stroke.setColor(0x806E4A18);
                c.drawCircle(cx, cy, r * 0.84f, stroke);
                lineFace(c, cx, cy + r * 0.03f, r * 0.62f, 0x66FFF0C0, r * 0.09f);
                lineFace(c, cx, cy, r * 0.62f, 0xFF3A2208, r * 0.09f);
                return;
            }
            case "minimal": {
                stroke.setStrokeWidth(dp(1.2f));
                int col = lost ? 0xFFB4361E : s.ink;
                stroke.setColor(col);
                c.drawCircle(cx, cy, r * 0.6f, stroke);
                lineFace(c, cx, cy, r * 0.6f, col, dp(1.6f));
                return;
            }
            case "cs": {
                String f = lost ? "[x_x]" : won ? "[B-)]" : pressing ? "[:-O]" : "[:-)]";
                num.setShadowLayer(dp(5), 0, 0, alpha(s.accent, 0x99));
                text(c, f, cx, cy, size * 0.5f, s.accent, MONO_BOLD);
                num.clearShadowLayer();
                return;
            }
            case "light": {
                shadow(c, cx - r, cy - r, cx + r, cy + r, r, 0x26000000, r * 0.25f);
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r, fill);
                lineFace(c, cx, cy, r * 0.62f, lost ? 0xFFD8573A : 0xFF1C1C1E, r * 0.08f);
                return;
            }
            default: {
                fill.setColor(0x33FFFFFF);
                c.drawCircle(cx, cy, r, fill);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x66FFFFFF);
                c.drawCircle(cx, cy, r, stroke);
                lineFace(c, cx, cy, r * 0.62f, lost ? 0xFFFFB27A : 0xFFFFFFFF, r * 0.08f);
            }
        }
    }

    /** The face: gasps while pressing, X eyes when you hit a mine, sunglasses when you win. */
    private void lineFace(Canvas c, float cx, float cy, float r, int col, float sw) {
        boolean lost = over && boom >= 0, won = over && boom < 0;
        stroke.setStrokeWidth(sw);
        stroke.setColor(col);
        fill.setColor(col);
        float ex = r * 0.36f, ey = cy - r * 0.25f;
        if (won) {
            c.drawRect(cx - r * 0.7f, ey - r * 0.12f, cx + r * 0.7f, ey + r * 0.03f, fill);
            c.drawCircle(cx - ex, ey + r * 0.05f, r * 0.2f, fill);
            c.drawCircle(cx + ex, ey + r * 0.05f, r * 0.2f, fill);
        } else if (lost) {
            float k = r * 0.13f;
            for (int s = -1; s <= 1; s += 2) {
                c.drawLine(cx + s * ex - k, ey - k, cx + s * ex + k, ey + k, stroke);
                c.drawLine(cx + s * ex - k, ey + k, cx + s * ex + k, ey - k, stroke);
            }
        } else {
            c.drawCircle(cx - ex, ey, Math.max(sw * 0.8f, r * 0.1f), fill);
            c.drawCircle(cx + ex, ey, Math.max(sw * 0.8f, r * 0.1f), fill);
        }
        if (pressing && !over) c.drawCircle(cx, cy + r * 0.38f, r * 0.17f, stroke);
        else if (lost) { rf.set(cx - r * 0.45f, cy + r * 0.3f, cx + r * 0.45f, cy + r * 0.9f); c.drawArc(rf, 200, 140, false, stroke); }
        else { rf.set(cx - r * 0.5f, cy - r * 0.1f, cx + r * 0.5f, cy + r * 0.6f); c.drawArc(rf, 20, 140, false, stroke); }
    }

    // ------------------------------------------------------------------ cells

    private void closedCell(Canvas c, GameSkin s, float x, float y, float cell, int i, boolean flagged, boolean wrong) {
        float cx = x + cell / 2, cy = y + cell / 2;
        switch (s.id) {
            case "classic":
                bevel(c, x, y, x + cell, y + cell, true, Math.max(2, Math.round(cell * 0.12f)), false);
                break;
            case "retro": {
                float g = cell * 0.07f, rad = cell * 0.24f;
                rr(c, x + g, y + g + cell * 0.05f, x + cell - g, y + cell - g + cell * 0.05f, rad, 0xFF1A0E06);
                rr(c, x + g, y + g, x + cell - g, y + cell - g, rad, 0xFFC9B285);
                rr(c, x + g, y + g, x + cell - g, y + cell - g - cell * 0.08f, rad, 0xFFF6EBD0);
                rr(c, x + g + cell * 0.12f, y + g + cell * 0.06f, x + cell - g - cell * 0.12f, y + g + cell * 0.14f, cell * 0.04f, 0x80FFFFFF);
                break;
            }
            case "art": {
                float e = Math.max(1, cell * 0.06f);
                fill.setColor(0xFFF4EEE2);
                c.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, fill);
                fill.setColor(0xFFCDBFA4);
                c.drawRect(x + 1, y + cell - 1 - e, x + cell - 1, y + cell - 1, fill);
                c.drawRect(x + cell - 1 - e, y + 1, x + cell - 1, y + cell - 1, fill);
                fill.setColor(0xFFFFFFFF);
                c.drawRect(x + 1, y + 1, x + cell - 1 - e, y + 1 + e * 0.6f, fill);
                // a faint veining of the ivory
                stroke.setStrokeWidth(1);
                stroke.setColor(0x14806040);
                c.drawLine(x + cell * 0.2f, y + cell * (0.3f + (i % 5) * 0.1f), x + cell * 0.8f, y + cell * (0.5f + (i % 3) * 0.1f), stroke);
                break;
            }
            case "space": {
                float g = cell * 0.08f;
                rr(c, x + g, y + g, x + cell - g, y + cell - g, cell * 0.06f, 0xFF1A1E23);
                stroke.setStrokeWidth(Math.max(1, cell * 0.02f));
                stroke.setColor(0xFF3E454C);
                rf.set(x + g, y + g, x + cell - g, y + cell - g);
                c.drawRoundRect(rf, cell * 0.06f, cell * 0.06f, stroke);
                fill.setColor(0x336FE08A);
                c.drawCircle(cx, cy, cell * 0.035f, fill);
                break;
            }
            case "cozy": {
                float g = cell * 0.05f, rad = cell * 0.1f;
                rr(c, x + g, y + g, x + cell - g, y + cell - g, rad, 0xFF4A2A12);
                rr(c, x + g, y + g, x + cell - g, y + cell - g - cell * 0.07f, rad, 0xFFB07840);
                rr(c, x + g + cell * 0.05f, y + g + cell * 0.04f, x + cell - g - cell * 0.05f, y + cell * 0.45f, rad * 0.6f, 0x22FFE0B0);
                stroke.setStrokeWidth(Math.max(1, cell * 0.015f));
                stroke.setColor(0x305A3010);
                float gy = y + cell * (0.3f + ((i * 7) % 5) * 0.08f);
                c.drawLine(x + g * 2, gy, x + cell - g * 2, gy + cell * 0.04f, stroke);
                break;
            }
            case "minimal":
                fill.setColor(s.squareDark);
                c.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, fill);
                break;
            case "light": {
                float g = cell * 0.06f, rad = cell * 0.2f;
                rr(c, x + g, y + g + cell * 0.03f, x + cell - g, y + cell - g + cell * 0.03f, rad, 0x1A000000);
                rr(c, x + g, y + g, x + cell - g, y + cell - g, rad, 0xFFFFFFFF);
                break;
            }
            default: {
                float g = cell * 0.06f, rad = cell * 0.2f;
                rr(c, x + g, y + g, x + cell - g, y + cell - g, rad, 0x52FFFFFF);
                rr(c, x + g, y + g, x + cell - g, y + cell * 0.5f, rad, 0x14FFFFFF);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x40FFFFFF);
                rf.set(x + g, y + g, x + cell - g, y + cell - g);
                c.drawRoundRect(rf, rad, rad, stroke);
            }
        }
        if (flagged) flag(c, s, cx, cy, cell * 0.3f, wrong);
    }

    private void openCell(Canvas c, GameSkin s, float x, float y, float cell, int i, boolean showMine, int[] nums) {
        float cx = x + cell / 2, cy = y + cell / 2;
        boolean boomed = i == boom;
        Typeface tf = SANS_BOLD;
        float ts = cell * 0.62f;
        switch (s.id) {
            case "classic":
                fill.setColor(boomed ? 0xFFFF0000 : FACE);
                c.drawRect(x, y, x + cell, y + cell, fill);
                fill.setColor(SHADOW);
                c.drawRect(x, y, x + cell, y + 1, fill);
                c.drawRect(x, y, x + 1, y + cell, fill);
                fill.setAntiAlias(true);
                break;
            case "retro":
                rr(c, x + cell * 0.07f, y + cell * 0.07f, x + cell * 0.93f, y + cell * 0.93f, cell * 0.2f, boomed ? 0xFFC4552A : 0xFF4A2C16);
                rr(c, x + cell * 0.07f, y + cell * 0.07f, x + cell * 0.93f, y + cell * 0.16f, cell * 0.06f, 0x33000000);
                tf = COND; ts = cell * 0.66f;
                break;
            case "art":
                fill.setColor(boomed ? 0xFF9A2A22 : 0xFF263A2E);
                c.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, fill);
                fill.setColor(0x40000000);
                c.drawRect(x + 1, y + 1, x + cell - 1, y + 1 + cell * 0.06f, fill);
                tf = SERIF_BOLD; ts = cell * 0.62f;
                break;
            case "space":
                if (boomed) rr(c, x + cell * 0.08f, y + cell * 0.08f, x + cell * 0.92f, y + cell * 0.92f, cell * 0.06f, 0xFF4A140A);
                tf = MONO_BOLD; ts = cell * 0.58f;
                break;
            case "cozy":
                fill.setColor(boomed ? 0xFF8A2A1E : 0xFF2E4A30);
                rf.set(x + cell * 0.05f, y + cell * 0.05f, x + cell * 0.95f, y + cell * 0.95f);
                c.drawRoundRect(rf, cell * 0.08f, cell * 0.08f, fill);
                fill.setColor(0x33000000);
                c.drawRect(x + cell * 0.05f, y + cell * 0.05f, x + cell * 0.95f, y + cell * 0.12f, fill);
                tf = SERIF_BOLD; ts = cell * 0.62f;
                break;
            case "minimal":
                if (boomed) { fill.setColor(0xFFB4361E); c.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, fill); }
                tf = SANS_LIGHT; ts = cell * 0.6f;
                break;
            case "light":
                if (boomed) rr(c, x + cell * 0.06f, y + cell * 0.06f, x + cell * 0.94f, y + cell * 0.94f, cell * 0.2f, 0xFFF4C8BC);
                tf = SANS_MED; ts = cell * 0.56f;
                break;
            default:
                rr(c, x + cell * 0.06f, y + cell * 0.06f, x + cell * 0.94f, y + cell * 0.94f, cell * 0.2f, boomed ? 0x99D8402F : 0x14000000);
                tf = SANS_MED; ts = cell * 0.56f;
        }
        if (showMine) mineGlyph(c, s, cx, cy, cell * 0.24f, boomed);
        else if (near[i] > 0) {
            int col = nums == null ? s.ink : nums[near[i]];
            if (s.id.equals("space")) num.setShadowLayer(cell * 0.12f, 0, 0, alpha(col, 0x88));
            text(c, String.valueOf(near[i]), cx, cy, ts, col, tf);
            num.clearShadowLayer();
        }
        if (s.bevel) fill.setAntiAlias(false);
    }

    /** Computer Science: the field as text, [ ] closed, digits open, * a mine. */
    private void termCell(Canvas c, GameSkin s, float x, float y, float cell, int i, boolean showMine, boolean wrong, boolean cursorHere) {
        float cx = x + cell / 2, cy = y + cell / 2;
        int col;
        String str;
        if (showMine) { str = "*"; col = s.ink; }
        else if (open[i]) { str = near[i] > 0 ? String.valueOf(near[i]) : "·"; col = near[i] > 0 ? s.ink : s.inkDim; }
        else if (flag[i]) { str = wrong ? "[x]" : "[F]"; col = s.accent; }
        else { str = "[ ]"; col = s.inkDim; }
        if (i == boom) {
            fill.setColor(s.accent);
            c.drawRect(x + cell * 0.04f, y + cell * 0.08f, x + cell * 0.96f, y + cell * 0.92f, fill);
            col = s.onAccent;
        }
        if (cursorHere) {
            float p = pulse();
            fill.setColor(alpha(s.accent, (int) (190 + 65 * p)));
            fill.setShadowLayer(dp(6), 0, 0, alpha(s.accent, 0x88));
            c.drawRect(x + cell * 0.04f, y + cell * 0.08f, x + cell * 0.96f, y + cell * 0.92f, fill);
            fill.clearShadowLayer();
            col = s.onAccent;
            if (str.equals("·")) str = " ";
        } else if (col != s.inkDim) num.setShadowLayer(dp(5), 0, 0, alpha(s.accent, 0x88));
        text(c, str, cx, cy, str.length() == 3 ? cell * 0.48f : cell * 0.62f, col, MONO_BOLD);
        num.clearShadowLayer();
    }

    private void mineGlyph(Canvas c, GameSkin s, float cx, float cy, float r, boolean boomed) {
        switch (s.id) {
            case "classic": drawMine(c, cx, cy, r, DARK, LIGHT); break;
            case "space": {
                int col = 0xFFE8705A;
                stroke.setStrokeWidth(Math.max(2, r * 0.16f));
                stroke.setColor(col);
                stroke.setShadowLayer(r * 0.4f, 0, 0, alpha(col, 0xAA));
                c.drawCircle(cx, cy, r * 0.75f, stroke);
                c.drawLine(cx - r * 1.3f, cy, cx - r * 0.4f, cy, stroke);
                c.drawLine(cx + r * 0.4f, cy, cx + r * 1.3f, cy, stroke);
                c.drawLine(cx, cy - r * 1.3f, cx, cy - r * 0.4f, stroke);
                c.drawLine(cx, cy + r * 0.4f, cx, cy + r * 1.3f, stroke);
                stroke.clearShadowLayer();
                fill.setColor(col);
                c.drawCircle(cx, cy, r * 0.2f, fill);
                break;
            }
            case "minimal":
                fill.setColor(boomed ? s.board : 0xFFB4361E);
                c.drawCircle(cx, cy, r * 0.7f, fill);
                break;
            case "retro": drawMine(c, cx, cy, r, 0xFFF2E6C8, 0xFFC4552A); break;
            case "art": drawMine(c, cx, cy, r, 0xFFC9A24A, 0xFFF8F2E6); break;
            case "cozy": drawMine(c, cx, cy, r, 0xFF1E1A16, 0xFFFFB04A); break;
            case "light": drawMine(c, cx, cy, r, 0xFF2A2A2E, 0xFFFFFFFF); break;
            default: drawMine(c, cx, cy, r, 0xFF1E1E22, 0xFFFFFFFF);
        }
    }

    private void flag(Canvas c, GameSkin s, float cx, float cy, float r, boolean wrong) {
        switch (s.id) {
            case "classic": drawFlag(c, cx, cy, r, wrong, DARK, 0xFFFF0000, 0xFFFF0000); break;
            case "space": {
                int col = s.accent;
                path.reset();
                path.moveTo(cx, cy - r * 0.85f);
                path.lineTo(cx + r * 0.8f, cy + r * 0.55f);
                path.lineTo(cx - r * 0.8f, cy + r * 0.55f);
                path.close();
                stroke.setStrokeWidth(Math.max(2, r * 0.14f));
                stroke.setColor(col);
                stroke.setShadowLayer(r * 0.35f, 0, 0, alpha(col, 0xAA));
                c.drawPath(path, stroke);
                stroke.clearShadowLayer();
                fill.setColor(col);
                c.drawRect(cx - r * 0.07f, cy - r * 0.35f, cx + r * 0.07f, cy + r * 0.12f, fill);
                c.drawCircle(cx, cy + r * 0.3f, r * 0.08f, fill);
                if (wrong) cross(c, cx, cy, r, 0xFFE8705A);
                break;
            }
            case "minimal": {
                stroke.setStrokeWidth(dp(1.2f));
                stroke.setColor(s.ink);
                c.drawLine(cx - r * 0.3f, cy - r * 0.8f, cx - r * 0.3f, cy + r * 0.8f, stroke);
                path.reset();
                path.moveTo(cx - r * 0.3f, cy - r * 0.8f);
                path.lineTo(cx + r * 0.7f, cy - r * 0.42f);
                path.lineTo(cx - r * 0.3f, cy - r * 0.04f);
                path.close();
                fill.setColor(0xFFB4361E);
                c.drawPath(path, fill);
                if (wrong) cross(c, cx, cy, r, s.ink);
                break;
            }
            case "retro": drawFlag(c, cx, cy, r, wrong, 0xFF4A2410, 0xFFC4552A, 0xFF4A2410); break;
            case "art": drawFlag(c, cx, cy, r, wrong, 0xFFB8893A, 0xFF9A2A22, 0xFF1C1612); break;
            case "cozy": drawFlag(c, cx, cy, r, wrong, 0xFFE0B860, 0xFFC8301E, 0xFFFFE6A0); break;
            case "light": drawFlag(c, cx, cy, r, wrong, 0xFF1C1C1E, 0xFFD8573A, 0xFF1C1C1E); break;
            default: drawFlag(c, cx, cy, r, wrong, 0xFFFFFFFF, 0xFFFFB27A, 0xFFFFFFFF);
        }
    }

    private void cross(Canvas c, float cx, float cy, float r, int col) {
        stroke.setStrokeWidth(r * 0.2f);
        stroke.setColor(col);
        c.drawLine(cx - r, cy - r, cx + r, cy + r, stroke);
        c.drawLine(cx + r, cy - r, cx - r, cy + r, stroke);
    }

    private void cellCursor(Canvas c, GameSkin s, float x, float y, float cell) {
        float p = pulse();
        switch (s.id) {
            case "classic": {
                // navy selection frame with the dotted focus rectangle inside
                float w = Math.max(3, Math.round(cell * 0.07f));
                fill.setColor(alpha(0xFF000080, (int) (170 + 85 * p)));
                c.drawRect(x, y, x + cell, y + w, fill);
                c.drawRect(x, y + cell - w, x + cell, y + cell, fill);
                c.drawRect(x, y, x + w, y + cell, fill);
                c.drawRect(x + cell - w, y, x + cell, y + cell, fill);
                stroke.setStrokeWidth(2);
                stroke.setColor(DARK);
                stroke.setPathEffect(new android.graphics.DashPathEffect(new float[]{2, 2}, 0));
                float k = w + 3;
                c.drawRect(x + k, y + k, x + cell - k, y + cell - k, stroke);
                stroke.setPathEffect(null);
                return;
            }
            case "space": {
                // a targeting reticle: four corner brackets
                int col = alpha(s.accent, (int) (170 + 85 * p));
                stroke.setStrokeWidth(Math.max(2, cell * 0.06f));
                stroke.setColor(col);
                stroke.setShadowLayer(cell * 0.12f, 0, 0, alpha(s.accent, 0x99));
                float k = cell * 0.3f, o = cell * 0.02f;
                float l = x + o, t = y + o, r = x + cell - o, b = y + cell - o;
                path.reset();
                path.moveTo(l, t + k); path.lineTo(l, t); path.lineTo(l + k, t);
                path.moveTo(r - k, t); path.lineTo(r, t); path.lineTo(r, t + k);
                path.moveTo(r, b - k); path.lineTo(r, b); path.lineTo(r - k, b);
                path.moveTo(l + k, b); path.lineTo(l, b); path.lineTo(l, b - k);
                c.drawPath(path, stroke);
                stroke.clearShadowLayer();
                return;
            }
            default: {
                int col = s.accent;
                if (s.id.equals("aurora")) col = 0xFFFFFFFF;
                if (s.id.equals("art")) col = 0xFF8A1E1A;   // oxblood reads on ivory where gilt would not
                float rad = s.id.equals("minimal") ? 0 : cell * Math.max(0.12f, s.round + 0.04f);
                stroke.setStrokeWidth(Math.max(4, cell * 0.085f));
                stroke.setColor(alpha(col, (int) (170 + 85 * p)));
                if (!s.id.equals("minimal")) stroke.setShadowLayer(cell * 0.15f, 0, 0, alpha(col, 0x80));
                rf.set(x + cell * 0.02f, y + cell * 0.02f, x + cell * 0.98f, y + cell * 0.98f);
                c.drawRoundRect(rf, rad, rad, stroke);
                stroke.clearShadowLayer();
            }
        }
    }

    /** Win95 3D edge: raised (light top-left) or sunken (dark top-left); whole pixels, no smoothing. */
    private void bevel(Canvas c, float l, float t, float r, float b, boolean raised, float e, boolean window) {
        fill.setShader(null);
        fill.setColor(FACE);
        c.drawRect(l, t, r, b, fill);
        int tl = raised ? LIGHT : SHADOW, br = raised ? SHADOW : LIGHT;
        if (window) {
            // the window edge: grey/white outer, white/grey inner, black outermost bottom-right
            fill.setColor(DARK);
            c.drawRect(l, b - 1, r, b, fill);
            c.drawRect(r - 1, t, r, b, fill);
            fill.setColor(0xFFDFDFDF);
            c.drawRect(l, t, r - 1, t + 1, fill);
            c.drawRect(l, t, l + 1, b - 1, fill);
            fill.setColor(SHADOW);
            c.drawRect(l + 1, b - 2, r - 1, b - 1, fill);
            c.drawRect(r - 2, t + 1, r - 1, b - 1, fill);
            fill.setColor(LIGHT);
            c.drawRect(l + 1, t + 1, r - 2, t + 2, fill);
            c.drawRect(l + 1, t + 1, l + 2, b - 2, fill);
            return;
        }
        // stair-stepped corners exactly like the 95 bitmaps
        for (int k = 0; k < (int) e; k++) {
            fill.setColor(tl);
            c.drawRect(l, t + k, r - k - 1, t + k + 1, fill);
            c.drawRect(l + k, t, l + k + 1, b - k - 1, fill);
            fill.setColor(br);
            c.drawRect(l + k + 1, b - k - 1, r, b - k, fill);
            c.drawRect(r - k - 1, t + k + 1, r - k, b, fill);
        }
    }

    private void drawMine(Canvas c, float cx, float cy, float r, int body, int hi) {
        stroke.setStrokeWidth(Math.max(2, r * 0.22f));
        stroke.setColor(body);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 4;
            float dx = (float) Math.cos(a) * r * 1.35f, dy = (float) Math.sin(a) * r * 1.35f;
            c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, stroke);
        }
        fill.setColor(body);
        c.drawCircle(cx, cy, r, fill);
        fill.setColor(hi);
        c.drawRect(cx - r * 0.5f, cy - r * 0.5f, cx - r * 0.15f, cy - r * 0.15f, fill);
    }

    /** A flag on a pole and base; crossed out after the game if it was wrong. */
    private void drawFlag(Canvas c, float cx, float cy, float r, boolean wrong, int pole, int cloth, int crossCol) {
        fill.setColor(pole);
        c.drawRect(cx - r * 0.7f, cy + r * 0.65f, cx + r * 0.7f, cy + r * 0.9f, fill);
        c.drawRect(cx - r * 0.35f, cy + r * 0.45f, cx + r * 0.35f, cy + r * 0.65f, fill);
        c.drawRect(cx - r * 0.06f, cy - r * 0.9f, cx + r * 0.08f, cy + r * 0.5f, fill);
        path.reset();
        path.moveTo(cx + r * 0.08f, cy - r * 0.95f);
        path.lineTo(cx - r * 0.8f, cy - r * 0.5f);
        path.lineTo(cx + r * 0.08f, cy - r * 0.05f);
        path.close();
        fill.setColor(cloth);
        c.drawPath(path, fill);
        if (wrong) cross(c, cx, cy, r, crossCol);
    }
}
