package home.aurora.games;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.TypedValue;

import java.util.Random;

import home.aurora.R;

/**
 * Board materials shared by Tic-Tac-Toe, Dots and Boxes, Hangman and 2048:
 * the surface each look plays on (frosted glass, a Windows 95 window, a walnut
 * and enamel 70s board, parchment in a gilt frame, a console screen in a steel
 * bezel, a wooden game box, a hairline sheet, a phosphor terminal) plus the
 * small pieces those looks share (95 bevels, 7-segment digits, ASCII glyphs).
 */
final class BoardLook {

    static final Typeface MONO = Typeface.MONOSPACE;
    static final Typeface MONO_BOLD = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
    static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface SANS_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface SANS_LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface SANS_THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface COND = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    static final Typeface SERIF = Typeface.create("serif", Typeface.NORMAL);
    static final Typeface SERIF_BOLD = Typeface.create("serif", Typeface.BOLD);
    static final Typeface SERIF_ITALIC = Typeface.create("serif", Typeface.ITALIC);

    // Windows 95 system colours
    static final int W_FACE = 0xFFC0C0C0, W_LIGHT = 0xFFDFDFDF, W_WHITE = 0xFFFFFFFF, W_SHADOW = 0xFF808080,
            W_DARK = 0xFF000000, W_NAVY = 0xFF000080, W_TEAL = 0xFF008080;
    // 1970s
    static final int R_ORANGE = 0xFFD2601F, R_MUSTARD = 0xFFE0A526, R_AVOCADO = 0xFF6E7B2B, R_BROWN = 0xFF5A3418,
            R_CREAM = 0xFFF4E8CC, R_ENAMEL = 0xFFEFE2C2;
    // museum
    static final int A_INK = 0xFF231A14, A_OXBLOOD = 0xFF7E1F1C, A_GREEN = 0xFF2F4A35, A_PARCH = 0xFFF1E6CC;
    // console
    static final int S_GREEN = 0xFF7CF29A, S_AMBER = 0xFFFFC56A, S_RED = 0xFFFF7A4A;
    // cozy
    static final int C_BRASS = 0xFFD9A84E, C_FELT = 0xFF2C4A33, C_IVORY = 0xFFF2E2C4, C_EMBER = 0xFFE07A3A;

    private final Game g;
    final GameSkin s;
    final float u;     // one dp in pixels
    final float px;    // one whole "95 pixel": a dp rounded to device pixels
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint crisp = new Paint();   // no anti-aliasing, for 95 pixels
    final RectF r = new RectF();

    private static Bitmap walnut, grain, specks;

    BoardLook(Game game, GameSkin skin) {
        g = game;
        s = skin;
        u = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, game.getResources().getDisplayMetrics());
        px = Math.max(1, Math.round(u));
        ln.setStyle(Paint.Style.STROKE);
        ln.setStrokeCap(Paint.Cap.ROUND);
        ln.setStrokeJoin(Paint.Join.ROUND);
        tx.setTextAlign(Paint.Align.CENTER);
        crisp.setAntiAlias(false);
    }

    String id() { return s.id; }
    boolean is(String id) { return s.id.equals(id); }
    boolean night() { return is("minimal") && (s.board & 0xFF) < 0x80; }

    static int a(int c, int alpha) { return (c & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24); }

    static int mix(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    void reset() {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.clearShadowLayer();
        p.setColor(0xFF000000);
        ln.setShader(null);
        ln.setPathEffect(null);
        ln.clearShadowLayer();
        ln.setStrokeCap(Paint.Cap.ROUND);
        tx.setShader(null);
        tx.clearShadowLayer();
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setFakeBoldText(false);
    }

    /** Text centred vertically on cy. */
    void text(Canvas c, String t, float cx, float cy) {
        c.drawText(t, cx, cy - (tx.descent() + tx.ascent()) / 2, tx);
    }

    // ------------------------------------------------------------------ the board each look plays on

    /**
     * Draws the look's board around (l,t,r,b) — the playing area — and returns the
     * area itself. Frames and title bars are drawn outside it.
     */
    void board(Canvas c, float l, float t, float rr, float b, String title) {
        reset();
        switch (s.id) {
            case "classic": window95(c, l, t, rr, b, title); break;
            case "retro": retroBoard(c, l, t, rr, b); break;
            case "art": giltParchment(c, l, t, rr, b); break;
            case "space": bezelScreen(c, l, t, rr, b, title); break;
            case "cozy": gameBox(c, l, t, rr, b); break;
            case "minimal": sheet(c, l, t, rr, b); break;
            case "cs": terminal(c, l, t, rr, b, title); break;
            case "light": glass(c, l, t, rr, b, true); break;
            default: glass(c, l, t, rr, b, false); break;
        }
        reset();
    }

    /** How much room the look's frame needs outside the playing area (top, sides, bottom). */
    float[] margins() {
        switch (s.id) {
            case "classic": return new float[]{u * 8, u * 8, u * 8};
            case "retro": return new float[]{u * 22, u * 22, u * 22};
            case "art": return new float[]{u * 30, u * 30, u * 30};
            case "space": return new float[]{u * 34, u * 20, u * 20};
            case "cozy": return new float[]{u * 26, u * 26, u * 26};
            case "cs": return new float[]{u * 30, u * 14, u * 14};
            case "minimal": return new float[]{u * 10, u * 10, u * 10};
            default: return new float[]{u * 18, u * 18, u * 18};
        }
    }

    /** Frosted glass (Aurora) or a white daylight panel (Light). */
    void glass(Canvas c, float l, float t, float rr, float b, boolean light) {
        float pad = u * 18, rad = u * 22;
        r.set(l - pad, t - pad, rr + pad, b + pad);
        if (light) {
            p.setShadowLayer(u * 22, 0, u * 8, 0x33000000);
            p.setColor(0xF7FFFFFF);
            c.drawRoundRect(r, rad, rad, p);
            p.clearShadowLayer();
            ln.setStrokeWidth(u);
            ln.setColor(0x14000000);
            c.drawRoundRect(r, rad, rad, ln);
        } else {
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x40FFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            // a soft sheen across the top
            p.setShader(new LinearGradient(0, r.top, 0, r.top + (r.height()) * 0.35f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            ln.setStrokeWidth(u * 1.2f);
            ln.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x8CFFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, ln);
            ln.setShader(null);
        }
    }

    // --- Windows 95

    void bevel(Canvas c, float l, float t, float rr, float b, boolean raised) {
        int ot = raised ? W_WHITE : W_SHADOW, ob = raised ? W_DARK : W_WHITE;
        int it = raised ? W_LIGHT : W_DARK, ib = raised ? W_SHADOW : W_LIGHT;
        edge(c, l, t, rr, b, ot, ob);
        edge(c, l + px, t + px, rr - px, b - px, it, ib);
    }

    /** One-pixel-wide frame: top/left in one colour, bottom/right in another. */
    void edge(Canvas c, float l, float t, float rr, float b, int tl, int br) {
        crisp.setShader(null);
        crisp.setColor(tl);
        c.drawRect(l, t, rr, t + px, crisp);
        c.drawRect(l, t, l + px, b, crisp);
        crisp.setColor(br);
        c.drawRect(l, b - px, rr, b, crisp);
        c.drawRect(rr - px, t, rr, b, crisp);
    }

    /** A raised 95 button face. */
    void button95(Canvas c, float l, float t, float rr, float b, boolean pressed, int face) {
        crisp.setColor(face);
        c.drawRect(l, t, rr, b, crisp);
        bevel(c, l, t, rr, b, !pressed);
    }

    /** The dotted focus rectangle. */
    void focus95(Canvas c, float l, float t, float rr, float b, int color) {
        crisp.setColor(color);
        for (float x = l; x < rr; x += px * 2) { c.drawRect(x, t, x + px, t + px, crisp); c.drawRect(x, b - px, x + px, b, crisp); }
        for (float y = t; y < b; y += px * 2) { c.drawRect(l, y, l + px, y + px, crisp); c.drawRect(rr - px, y, rr, y + px, crisp); }
    }

    /**
     * Classic: the games screen already wraps the game in a 95 window, so the board
     * just sits sunken into the silver client area.
     */
    void window95(Canvas c, float l, float t, float rr, float b, String title) {
        float m = u * 8;
        crisp.setColor(W_FACE);
        c.drawRect(l - m, t - m, rr + m, b + m, crisp);
        bevel(c, l - px * 2, t - px * 2, rr + px * 2, b + px * 2, false);
    }

    /** 95-style text: crisp sans with no smoothing. */
    void text95(Canvas c, String t, float cx, float cy, float size, int color, boolean bold) {
        tx.setTypeface(bold ? SANS_BOLD : SANS);
        tx.setTextSize(size);
        tx.setColor(color);
        text(c, t, cx, cy);
    }

    /** Chiselled "disabled" text, as on a greyed-out 95 button. */
    void disabled95(Canvas c, String t, float cx, float cy, float size) {
        text95(c, t, cx + px, cy + px, size, W_WHITE, true);
        text95(c, t, cx, cy, size, W_SHADOW, true);
    }

    // --- 1970s board: walnut rim, cream enamel, an orange-mustard stripe

    void retroBoard(Canvas c, float l, float t, float rr, float b) {
        float m = u * 22, rad = u * 26;
        r.set(l - m, t - m, rr + m, b + m);
        p.setShadowLayer(u * 18, 0, u * 8, 0x80000000);
        p.setColor(0xFF3A2010);
        c.drawRoundRect(r, rad, rad, p);
        p.clearShadowLayer();
        wood(c, r, rad, 0xFF6A3A1C, 1.0f);
        // lacquer: light from above
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x30FFE6C0, 0x40000000, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        ln.setStrokeWidth(u * 1.5f);
        ln.setColor(0x40FFE0B0);
        r.inset(u, u);
        c.drawRoundRect(r, rad, rad, ln);
        // the enamel field, sunk a little into the wood
        float e = u * 8;
        r.set(l - e, t - e, rr + e, b + e);
        p.setColor(0xFF2A1408);
        r.offset(0, -u * 1.5f);
        c.drawRoundRect(r, rad * 0.6f, rad * 0.6f, p);
        r.offset(0, u * 1.5f);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFF7EDD3, 0xFFE8D7B0, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad * 0.6f, rad * 0.6f, p);
        p.setShader(null);
        // the stripe, a 70s box-art touch along the bottom rim
        float sy = b + m * 0.62f, sw = u * 3.2f;
        int[] cols = {R_MUSTARD, R_ORANGE, 0xFF9A3A16};
        for (int i = 0; i < 3; i++) {
            p.setColor(cols[i]);
            c.drawRect(l + u * 6, sy + i * sw - sw * 1.5f, rr - u * 6, sy + i * sw - sw * 0.5f, p);
        }
    }

    /** Walnut grain clipped to a rounded rect, tinted. */
    void wood(Canvas c, RectF area, float rad, int base, float scale) {
        if (walnut == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            walnut = BitmapFactory.decodeResource(g.getResources(), R.drawable.tx_walnut, o);
        }
        p.setColor(base);
        c.drawRoundRect(area, rad, rad, p);
        BitmapShader sh = new BitmapShader(walnut, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
        Matrix mx = new Matrix();
        mx.setScale(scale, scale);
        mx.postTranslate(area.left, area.top);
        sh.setLocalMatrix(mx);
        p.setShader(sh);
        p.setAlpha(235);
        c.drawRoundRect(area, rad, rad, p);
        p.setShader(null);
        p.setAlpha(255);
    }

    /** A chunky enamel / plastic tile with a rounded top light, as on 70s game pieces. */
    void chunky(Canvas c, float l, float t, float rr, float b, float rad, int color) {
        r.set(l, t + u * 3, rr, b + u * 3);
        p.setColor(0x55000000);
        c.drawRoundRect(r, rad, rad, p);
        r.set(l, t, rr, b);
        p.setColor(Game.shade(color));
        c.drawRoundRect(r, rad, rad, p);
        r.set(l, t, rr, b - u * 3);
        p.setShader(new LinearGradient(0, t, 0, b, Game.lighten(color), color, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        // a soft highlight band near the top
        r.set(l + (rr - l) * 0.12f, t + u * 2.5f, rr - (rr - l) * 0.12f, t + (b - t) * 0.22f);
        p.setColor(0x33FFFFFF);
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
    }

    // --- museum: parchment in a gilt frame under a gentle spotlight

    void giltParchment(Canvas c, float l, float t, float rr, float b) {
        float m = u * 30;
        r.set(l - m, t - m, rr + m, b + m);
        p.setShadowLayer(u * 24, 0, u * 10, 0x99000000);
        p.setColor(0xFF6A4A1A);
        c.drawRect(r, p);
        p.clearShadowLayer();
        gilt(c, r.left, r.top, r.right, r.bottom, m - u * 8);
        // a dark slip between frame and paper
        r.set(l - u * 8, t - u * 8, rr + u * 8, b + u * 8);
        p.setColor(0xFF2A1A10);
        c.drawRect(r, p);
        r.inset(u * 2, u * 2);
        parchment(c, r);
    }

    /** Layered gilt moulding, lit from the top left. */
    void gilt(Canvas c, float l, float t, float rr, float b, float w) {
        int[] bands = {0xFF8A6420, 0xFFE8C877, 0xFFB8893A, 0xFFF4DC98, 0xFF9A7028, 0xFFD8B060, 0xFF6A4A18};
        float bw = w / bands.length;
        for (int i = 0; i < bands.length; i++) {
            float o = i * bw;
            Path pth = new Path();
            // top + left bevel catch the light; bottom + right fall in shade
            p.setColor(bands[i]);
            pth.addRect(l + o, t + o, rr - o, b - o, Path.Direction.CW);
            c.save();
            c.clipRect(l + o, t + o, rr - o, b - o);
            c.drawRect(l + o, t + o, rr - o, b - o, p);
            c.restore();
        }
        // shade the bottom and right sides
        Path sh = new Path();
        sh.moveTo(rr, t); sh.lineTo(rr, b); sh.lineTo(l, b); sh.lineTo(l + w, b - w); sh.lineTo(rr - w, b - w); sh.lineTo(rr - w, t + w); sh.close();
        p.setColor(0x38000000);
        c.drawPath(sh, p);
        ln.setStrokeWidth(u);
        ln.setColor(0x80FFF0C0);
        c.drawRect(l + u * 0.5f, t + u * 0.5f, rr - u * 0.5f, b - u * 0.5f, ln);
    }

    /** Warm laid paper with a soft foxed edge and the spotlight on it. */
    void parchment(Canvas c, RectF area) {
        p.setColor(A_PARCH);
        c.drawRect(area, p);
        if (specks == null) specks = speckle(256);
        p.setShader(new BitmapShader(specks, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        c.drawRect(area, p);
        p.setShader(new RadialGradient(area.centerX(), area.top + area.height() * 0.38f, Math.max(area.width(), area.height()) * 0.75f,
                new int[]{0x22FFF8E0, 0x00FFFFFF, 0x4A7A5A2A}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(area, p);
        p.setShader(null);
    }

    private static Bitmap speckle(int n) {
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random rnd = new Random(4);
        for (int i = 0; i < n * n / 30; i++) {
            q.setColor(((rnd.nextInt(22) + 4) << 24) | 0x6A4A20);
            c.drawCircle(rnd.nextFloat() * n, rnd.nextFloat() * n, 0.4f + rnd.nextFloat() * 1.1f, q);
        }
        for (int i = 0; i < 6; i++) {   // faint fibres
            q.setColor(0x10593A1A);
            q.setStrokeWidth(0.8f);
            float x = rnd.nextFloat() * n, y = rnd.nextFloat() * n;
            c.drawLine(x, y, x + rnd.nextFloat() * 40 - 20, y + rnd.nextFloat() * 8 - 4, q);
        }
        return bmp;
    }

    // --- space: a dark glass display in a brushed steel bezel

    void bezelScreen(Canvas c, float l, float t, float rr, float b, String title) {
        float m = u * 20, top = u * 34, rad = u * 14;
        r.set(l - m, t - top, rr + m, b + m);
        p.setShadowLayer(u * 18, 0, u * 6, 0xAA000000);
        p.setColor(0xFF2A2E33);
        c.drawRoundRect(r, rad, rad, p);
        p.clearShadowLayer();
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0xFF6C737A, 0xFF3E444A, 0xFF5A6066, 0xFF30353A}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        if (grain == null) grain = brushed();
        BitmapShader bs = new BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix mx = new Matrix();
        mx.setScale(3f, 1f);
        bs.setLocalMatrix(mx);
        p.setShader(bs);
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        ln.setStrokeWidth(u);
        ln.setColor(0x59FFFFFF);
        r.inset(u * 0.5f, u * 0.5f);
        c.drawRoundRect(r, rad, rad, ln);
        // screws in the corners
        float sx[] = {r.left + u * 10, r.right - u * 10}, sy[] = {r.top + u * 10, r.bottom - u * 10};
        for (float x : sx) for (float y : sy) screw(c, x, y, u * 4.5f);
        // panel label and a status LED
        tx.setTypeface(COND);
        tx.setTextSize(u * 11);
        tx.setLetterSpacing(0.25f);
        tx.setColor(0xCCE8EAE6);
        tx.setTextAlign(Paint.Align.LEFT);
        c.drawText(title.toUpperCase(), l + u * 12, t - top * 0.42f, tx);
        tx.setLetterSpacing(0f);
        tx.setTextAlign(Paint.Align.CENTER);
        float lx = rr - u * 16, ly = t - top * 0.55f;
        led(c, lx, ly, u * 4, S_GREEN);
        led(c, lx - u * 14, ly, u * 4, S_AMBER);
        // the screen recess
        float e = u * 6;
        r.set(l - e, t - e, rr + e, b + e);
        p.setColor(0xFF050607);
        c.drawRoundRect(r, u * 6, u * 6, p);
        ln.setStrokeWidth(u * 1.5f);
        ln.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFF15181B, 0x80B8BEC4, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, u * 6, u * 6, ln);
        ln.setShader(null);
        r.inset(u * 2, u * 2);
        p.setShader(new RadialGradient(r.centerX(), r.centerY(), Math.max(r.width(), r.height()) * 0.7f,
                0xFF0E1A16, 0xFF06090A, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, u * 5, u * 5, p);
        p.setShader(null);
        // fine grid lines
        ln.setStrokeWidth(Math.max(1f, u * 0.6f));
        ln.setColor(0x1A7CF29A);
        float step = u * 20;
        for (float x = r.left + step; x < r.right; x += step) c.drawLine(x, r.top, x, r.bottom, ln);
        for (float y = r.top + step; y < r.bottom; y += step) c.drawLine(r.left, y, r.right, y, ln);
    }

    /** The glass reflection on a console screen; draw it last. */
    void screenGlare(Canvas c, float l, float t, float rr, float b) {
        if (!is("space")) return;
        Path gl = new Path();
        float w = rr - l;
        gl.moveTo(l, t); gl.lineTo(l + w * 0.42f, t); gl.lineTo(l + w * 0.18f, b); gl.lineTo(l, b); gl.close();
        p.setShader(new LinearGradient(l, t, l + w * 0.4f, b, 0x0FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(gl, p);
        p.setShader(null);
    }

    void screw(Canvas c, float x, float y, float rad) {
        p.setShader(new RadialGradient(x - rad * 0.3f, y - rad * 0.3f, rad * 1.4f, 0xFFD8DDE2, 0xFF4A4F55, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad, p);
        p.setShader(null);
        ln.setStrokeWidth(u * 1.2f);
        ln.setColor(0xAA22262A);
        c.drawLine(x - rad * 0.6f, y + rad * 0.3f, x + rad * 0.6f, y - rad * 0.3f, ln);
    }

    void led(Canvas c, float x, float y, float rad, int col) {
        p.setShader(new RadialGradient(x, y, rad * 3, a(col, 0x66), a(col, 0), Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad * 3, p);
        p.setShader(new RadialGradient(x - rad * 0.3f, y - rad * 0.3f, rad, 0xFFFFFFFF, col, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad, p);
        p.setShader(null);
    }

    private static Bitmap brushed() {
        int w = 256, h = 96;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] pxs = new int[w * h];
        Random r = new Random(5);
        for (int y = 0; y < h; y++) {
            int row = r.nextInt(16);
            for (int x = 0; x < w; x++) pxs[y * w + x] = ((row + r.nextInt(6)) << 24) | 0xFFFFFF;
        }
        b.setPixels(pxs, 0, w, 0, 0, w, h);
        return b;
    }

    /** A glowing line-art stroke, as on a console display. */
    void holo(Canvas c, Path path, int col, float width) {
        ln.setStrokeWidth(width * 2.6f);
        ln.setColor(a(col, 0x30));
        c.drawPath(path, ln);
        ln.setStrokeWidth(width);
        ln.setColor(col);
        c.drawPath(path, ln);
        ln.setStrokeWidth(Math.max(1f, width * 0.35f));
        ln.setColor(a(0xFFFFFFFF, 0x99));
        c.drawPath(path, ln);
    }

    /** Targeting brackets at the corners of a rect (the console cursor). */
    void brackets(Canvas c, float l, float t, float rr, float b, int col, float len) {
        ln.setStrokeCap(Paint.Cap.SQUARE);
        ln.setStrokeWidth(u * 2.5f);
        ln.setColor(col);
        ln.setShadowLayer(u * 6, 0, 0, a(col, 0x99));
        Path q = new Path();
        q.moveTo(l, t + len); q.lineTo(l, t); q.lineTo(l + len, t);
        q.moveTo(rr - len, t); q.lineTo(rr, t); q.lineTo(rr, t + len);
        q.moveTo(rr, b - len); q.lineTo(rr, b); q.lineTo(rr - len, b);
        q.moveTo(l + len, b); q.lineTo(l, b); q.lineTo(l, b - len);
        c.drawPath(q, ln);
        ln.clearShadowLayer();
        ln.setStrokeCap(Paint.Cap.ROUND);
    }

    // --- cozy: a wooden game box with a felt bed and brass corners, by the fire

    void gameBox(Canvas c, float l, float t, float rr, float b) {
        float m = u * 26, rad = u * 10;
        r.set(l - m, t - m, rr + m, b + m);
        p.setShadowLayer(u * 26, 0, u * 12, 0xCC000000);
        p.setColor(0xFF3A2414);
        c.drawRoundRect(r, rad, rad, p);
        p.clearShadowLayer();
        wood(c, r, rad, 0xFF7A4A26, 1.4f);
        // firelight from the left
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0x40FFB04A, 0x00000000, 0x55000000}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        // inner lip of the box
        float e = u * 10;
        r.set(l - e, t - e, rr + e, b + e);
        p.setColor(0xFF20120A);
        c.drawRoundRect(r, u * 4, u * 4, p);
        ln.setStrokeWidth(u * 1.5f);
        ln.setColor(0x55FFC890);
        RectF lip = new RectF(r.left - u, r.top - u, r.right + u, r.bottom + u);
        c.drawRoundRect(lip, u * 5, u * 5, ln);
        // felt bed
        r.inset(u * 3, u * 3);
        p.setShader(new RadialGradient(r.left + r.width() * 0.35f, r.top + r.height() * 0.4f, Math.max(r.width(), r.height()) * 0.8f,
                0xFF3A5E40, 0xFF1A2E1E, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, u * 3, u * 3, p);
        p.setShader(null);
        if (specks == null) specks = speckle(256);
        p.setShader(new BitmapShader(specks, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        p.setAlpha(140);
        c.drawRoundRect(r, u * 3, u * 3, p);
        p.setAlpha(255);
        p.setShader(null);
        // brass corner caps
        RectF o = new RectF(l - m, t - m, rr + m, b + m);
        float k = u * 22;
        brassCorner(c, o.left, o.top, k, 1, 1);
        brassCorner(c, o.right, o.top, k, -1, 1);
        brassCorner(c, o.left, o.bottom, k, 1, -1);
        brassCorner(c, o.right, o.bottom, k, -1, -1);
    }

    private void brassCorner(Canvas c, float x, float y, float k, int sx, int sy) {
        Path q = new Path();
        q.moveTo(x, y); q.lineTo(x + sx * k, y); q.lineTo(x + sx * k * 0.55f, y + sy * k * 0.3f);
        q.lineTo(x + sx * k * 0.3f, y + sy * k * 0.55f); q.lineTo(x, y + sy * k); q.close();
        p.setShader(new LinearGradient(x, y, x + sx * k, y + sy * k, 0xFFF2D08A, 0xFF8A6020, Shader.TileMode.CLAMP));
        c.drawPath(q, p);
        p.setShader(null);
        p.setColor(0xFF5A3A10);
        c.drawCircle(x + sx * k * 0.25f, y + sy * k * 0.25f, u * 1.8f, p);
    }

    /** A brass stud / tack. */
    void brass(Canvas c, float x, float y, float rad) {
        p.setShader(new RadialGradient(x, y + rad * 0.3f, rad * 1.6f, 0x88000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawCircle(x, y + rad * 0.3f, rad * 1.6f, p);
        p.setShader(new RadialGradient(x - rad * 0.35f, y - rad * 0.4f, rad * 1.5f,
                new int[]{0xFFFFF0C0, C_BRASS, 0xFF6A4A18}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rad, p);
        p.setShader(null);
    }

    // --- minimal: a sheet with a hairline edge

    void sheet(Canvas c, float l, float t, float rr, float b) {
        float m = u * 10;
        ln.setStrokeWidth(Math.max(1f, u * 0.75f));
        ln.setColor(a(s.ink, 0x40));
        c.drawRect(l - m, t - m, rr + m, b + m, ln);
    }

    // --- cs: a terminal window drawn in ASCII

    void terminal(Canvas c, float l, float t, float rr, float b, String title) {
        float m = u * 14, top = u * 30;
        r.set(l - m, t - top, rr + m, b + m);
        p.setColor(0xF0020A04);
        c.drawRect(r, p);
        // ASCII border: +----[ title ]----+
        float cw = u * 11;
        tx.setTypeface(MONO);
        tx.setTextSize(cw / 0.6f);
        int cols = Math.max(4, (int) (r.width() / cw));
        float x0 = r.left + (r.width() - cols * cw) / 2;
        int rows = Math.max(3, (int) (r.height() / (cw * 1.9f)));
        float rh = r.height() / rows;
        StringBuilder topRow = new StringBuilder("+");
        String label = "[ " + title.toLowerCase().replace(' ', '_') + " ]";
        for (int i = 1; i < cols - 1; i++) topRow.append('-');
        topRow.append('+');
        int at = 3;
        for (int i = 0; i < label.length() && at + i < cols - 2; i++) topRow.setCharAt(at + i, label.charAt(i));
        String bottom = topRow.toString().replaceAll("[^+]", "-").replaceFirst("-$", "+");
        bottom = "+" + bottom.substring(1, bottom.length() - 1).replace('+', '-') + "+";
        glow(true, s.inkDim);
        glyphs(c, topRow.toString(), x0, r.top + rh * 0.5f, cw);
        glyphs(c, bottom, x0, r.bottom - rh * 0.5f, cw);
        for (int i = 1; i < rows - 1; i++) {
            float y = r.top + rh * (i + 0.5f);
            glyphs(c, "|", x0, y, cw);
            glyphs(c, "|", x0 + (cols - 1) * cw, y, cw);
        }
        glow(false, 0);
    }

    /** Phosphor glow on the text paint. */
    void glow(boolean on, int col) {
        if (on) {
            tx.setColor(col);
            tx.setShadowLayer(u * 5, 0, 0, a(col, 0x99));
        } else tx.clearShadowLayer();
    }

    /** One character per fixed-width cell, so ASCII art lines up whatever the font. */
    void glyphs(Canvas c, String line, float x0, float cy, float cw) {
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == ' ') continue;
            text(c, String.valueOf(ch), x0 + cw * (i + 0.5f), cy);
        }
    }

    /** Faint scanlines over an area (CS). */
    void scanlines(Canvas c, float l, float t, float rr, float b) {
        crisp.setColor(0x26000000);
        for (float y = t; y < b; y += 3) c.drawRect(l, y + 2, rr, y + 3, crisp);
    }

    // ------------------------------------------------------------------ 7-segment digits

    private static final int[] SEG = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    /** Width of one 7-segment digit cell for a given height. */
    static float segWidth(float h) { return h * 0.56f; }

    /** Draws digits right-aligned to {@code right} (or centred when {@code center}). */
    void seven(Canvas c, String digits, float cx, float cy, float h, int on, int off, boolean glowOn) {
        float w = segWidth(h), gap = h * 0.3f, total = digits.length() * w + (digits.length() - 1) * gap;
        float x = cx - total / 2;
        for (int i = 0; i < digits.length(); i++) {
            char ch = digits.charAt(i);
            int mask = ch >= '0' && ch <= '9' ? SEG[ch - '0'] : ch == '-' ? 0x40 : 0;
            digit(c, mask, x, cy - h / 2, w, h, on, off, glowOn);
            x += w + gap;
        }
    }

    private void digit(Canvas c, int mask, float x, float y, float w, float h, int on, int off, boolean glowOn) {
        float t = h * 0.14f, hh = h / 2, gp = t * 0.22f;
        float[][] segs = {
                {x + gp, y, x + w - gp, y, 0}, {x + w, y + gp, x + w, y + hh - gp, 1}, {x + w, y + hh + gp, x + w, y + h - gp, 1},
                {x + gp, y + h, x + w - gp, y + h, 0}, {x, y + hh + gp, x, y + h - gp, 1}, {x, y + gp, x, y + hh - gp, 1},
                {x + gp, y + hh, x + w - gp, y + hh, 0}};
        for (int i = 0; i < 7; i++) {
            boolean lit = (mask & (1 << i)) != 0;
            if (!lit && off == 0) continue;
            float[] sg = segs[i];
            Path q = new Path();
            float d = t / 2;
            if (sg[4] == 0) {   // horizontal hexagon
                q.moveTo(sg[0], sg[1]); q.lineTo(sg[0] + d, sg[1] - d); q.lineTo(sg[2] - d, sg[1] - d);
                q.lineTo(sg[2], sg[1]); q.lineTo(sg[2] - d, sg[1] + d); q.lineTo(sg[0] + d, sg[1] + d); q.close();
            } else {
                q.moveTo(sg[0], sg[1]); q.lineTo(sg[0] + d, sg[1] + d); q.lineTo(sg[0] + d, sg[3] - d);
                q.lineTo(sg[0], sg[3]); q.lineTo(sg[0] - d, sg[3] - d); q.lineTo(sg[0] - d, sg[1] + d); q.close();
            }
            p.setColor(lit ? on : off);
            if (lit && glowOn) p.setShadowLayer(h * 0.12f, 0, 0, a(on, 0xAA));
            c.drawPath(q, p);
            p.clearShadowLayer();
        }
    }

    // ------------------------------------------------------------------ pixel art (95)

    /** Draws a bitmap pattern ('#' = on) as crisp squares centred on (cx,cy). */
    void pixels(Canvas c, String[] rows, float cx, float cy, float cellPx, int color) {
        float w = rows[0].length() * cellPx, h = rows.length * cellPx, x0 = Math.round(cx - w / 2), y0 = Math.round(cy - h / 2);
        crisp.setColor(color);
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++)
                if (rows[y].charAt(x) == '#') c.drawRect(x0 + x * cellPx, y0 + y * cellPx, x0 + (x + 1) * cellPx, y0 + (y + 1) * cellPx, crisp);
    }

    /** A 2x2 dither of a colour over white, the 95 way of tinting an area. */
    Shader dither(int col, int bg) {
        int n = (int) px;
        Bitmap b = Bitmap.createBitmap(n * 2, n * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint q = new Paint();
        q.setColor(bg);
        c.drawRect(0, 0, n * 2, n * 2, q);
        q.setColor(col);
        c.drawRect(0, 0, n, n, q);
        c.drawRect(n, n, n * 2, n * 2, q);
        return new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    // ------------------------------------------------------------------ ink

    /** A calligraphic stroke: thin at the ends, swelling in the middle. */
    Path taper(float x1, float y1, float x2, float y2, float w) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.hypot(dx, dy);
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        float mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
        Path q = new Path();
        q.moveTo(x1, y1);
        q.quadTo(mx + nx * 1.3f, my + ny * 1.3f, x2, y2);
        q.quadTo(mx - nx * 1.3f, my - ny * 1.3f, x1, y1);
        q.close();
        return q;
    }

    /** A broad-nib ring: thick on the sides, thin top and bottom. */
    Path nibRing(float cx, float cy, float rad, float w) {
        Path q = new Path();
        q.addOval(new RectF(cx - rad - w / 2, cy - rad, cx + rad + w / 2, cy + rad), Path.Direction.CW);
        q.addOval(new RectF(cx - rad + w / 2, cy - rad + w * 0.18f, cx + rad - w / 2, cy + rad - w * 0.18f), Path.Direction.CCW);
        Matrix m = new Matrix();
        m.setRotate(-18, cx, cy);
        q.transform(m);
        return q;
    }

    /** A dashed pulsing preview line. */
    void dashed(Canvas c, float x1, float y1, float x2, float y2, int col, float w) {
        ln.setStrokeWidth(w);
        ln.setColor(col);
        ln.setPathEffect(new DashPathEffect(new float[]{w * 2.4f, w * 2f}, 0));
        c.drawLine(x1, y1, x2, y2, ln);
        ln.setPathEffect(null);
    }

    static Resources res(Game g) { return g.getResources(); }
}
