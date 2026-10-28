package home.aurora.games;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Random;

import home.aurora.R;

/**
 * The 8x8 board, chessmen and checkers drawn in each home look, shared by Chess and
 * Checkers (Connect 4 borrows the pixel discs). The board and every piece are rendered
 * once per look and size into bitmaps, so a frame is just a few bitmap blits.
 *
 * aurora: frosted glass; classic: Windows 95 bevels and pixel art; retro: a 70s walnut
 * set with mustard squares and cream/orange enamel men; art: ivory and ebony on marble
 * in gilt; space: holographic line art on a lit grid in a brushed bezel; cozy: boxwood
 * and rosewood on maple and walnut; minimal: ink on paper; cs: ASCII on phosphor;
 * light: porcelain on frosted white.
 */
final class BoardArt {

    private final Game g;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crisp = new Paint();          // no smoothing, no filtering: pixel art
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    BoardArt(Game g) {
        this.g = g;
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeJoin(Paint.Join.ROUND);
        st.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        crisp.setAntiAlias(false);
        crisp.setFilterBitmap(false);
        crisp.setDither(false);
    }

    GameSkin s() { return g.skin(); }
    String look() { return s().id; }

    /** Minimal by night: light ink on black stock. */
    boolean night() {
        int c = s().ink;
        return ((c >> 16 & 0xFF) + (c >> 8 & 0xFF) + (c & 0xFF)) > 384;
    }

    private String key() { return look() + (night() ? "N" : "D"); }

    // ================================================================== textures

    private static Bitmap walnut;

    Shader wood(float scale, float dx, float dy) {
        if (walnut == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            walnut = BitmapFactory.decodeResource(g.getResources(), R.drawable.tx_walnut, o);
        }
        BitmapShader sh = new BitmapShader(walnut, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        sh.setLocalMatrix(m);
        return sh;
    }

    /** Veined marble filling the current clip. */
    private void marble(Canvas b, RectF r, int base, int vein, int mottle, long seed) {
        Random rnd = new Random(seed);
        p.setShader(null);
        p.setColor(base);
        b.drawRect(r, p);
        for (int i = 0; i < 7; i++) {
            float x = r.left + rnd.nextFloat() * r.width(), y = r.top + rnd.nextFloat() * r.height();
            float rad = r.width() * (0.15f + rnd.nextFloat() * 0.3f);
            p.setShader(new RadialGradient(x, y, rad, mottle, mottle & 0xFFFFFF, Shader.TileMode.CLAMP));
            b.drawCircle(x, y, rad, p);
        }
        p.setShader(null);
        Paint v = new Paint(Paint.ANTI_ALIAS_FLAG);
        v.setStyle(Paint.Style.STROKE);
        v.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 9; i++) {
            Path path = new Path();
            float y = r.top + rnd.nextFloat() * r.height(), x = r.left - 20;
            path.moveTo(x, y);
            int segs = 6;
            for (int k = 0; k < segs; k++) {
                float nx = x + r.width() / segs + 20, ny = y + (rnd.nextFloat() - 0.35f) * r.height() * 0.22f;
                path.cubicTo(x + (nx - x) * 0.3f, y + (rnd.nextFloat() - 0.5f) * 60,
                        x + (nx - x) * 0.7f, ny + (rnd.nextFloat() - 0.5f) * 60, nx, ny);
                x = nx;
                y = ny;
            }
            float w = g.dp(0.4f + rnd.nextFloat() * (i < 3 ? 2.2f : 0.9f));
            v.setStrokeWidth(w * 3);
            v.setColor(GameSkin.alpha(vein, 0.18f));
            b.drawPath(path, v);
            v.setStrokeWidth(w);
            v.setColor(vein);
            b.drawPath(path, v);
        }
    }

    // ================================================================== the board

    private Bitmap bg;
    private String bgKey;

    /** The frame and squares; n squares a side starting at (left, top). */
    void board(Canvas c, float left, float top, float side, int n) {
        int w = g.getWidth(), h = g.getHeight();
        String k = key() + w + "x" + h + ":" + left + "," + top + "," + side;
        if (!k.equals(bgKey) || bg == null) {
            if (bg != null) bg.recycle();
            bg = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
            renderBoard(new Canvas(bg), left, top, side, n);
            bgKey = k;
        }
        c.drawBitmap(bg, 0, 0, null);
    }

    private Path squares(float l, float t, float cell, int n, boolean dark) {
        Path q = new Path();
        for (int r = 0; r < n; r++) for (int k = 0; k < n; k++)
            if (((r + k) % 2 == 1) == dark) q.addRect(l + k * cell, t + r * cell, l + (k + 1) * cell, t + (r + 1) * cell, Path.Direction.CW);
        return q;
    }

    private void renderBoard(Canvas b, float l, float t, float side, int n) {
        GameSkin s = s();
        float cell = side / n, m = cell * 0.28f, r = l + side, bt = t + side;
        RectF sq = new RectF(l, t, r, bt);
        Path light = squares(l, t, cell, n, false), dark = squares(l, t, cell, n, true);
        p.setShader(null);
        p.clearShadowLayer();
        switch (look()) {
            case "classic": {
                int u = Math.max(1, Math.round(g.dp(1)));
                int L = Math.round(l), T = Math.round(t), R = Math.round(r), B = Math.round(bt);
                int M = Math.round(m * 0.7f);
                // a raised silver panel...
                bevel(b, L - M - 4 * u, T - M - 4 * u, R + M + 4 * u, B + M + 4 * u, u, true);
                // ...with a sunken well holding the squares
                bevel(b, L - 2 * u, T - 2 * u, R + 2 * u, B + 2 * u, u, false);
                crisp.setColor(0xFFFFFFFF);
                for (int rr = 0; rr < n; rr++) for (int kk = 0; kk < n; kk++) {
                    crisp.setColor((rr + kk) % 2 == 1 ? 0xFF808080 : 0xFFFFFFFF);
                    b.drawRect(xi(l, cell, kk), xi(t, cell, rr), xi(l, cell, kk + 1), xi(t, cell, rr + 1), crisp);
                }
                break;
            }
            case "retro": {
                rf.set(l - m, t - m, r + m, bt + m);
                float rad = m * 1.1f;
                p.setShadowLayer(m * 0.6f, 0, m * 0.25f, 0x99000000);
                p.setColor(0xFF4A2A16);
                b.drawRoundRect(rf, rad, rad, p);
                p.clearShadowLayer();
                p.setShader(wood(1.3f, 0, 0));
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0x30FFE6C8, 0x40000000, Shader.TileMode.CLAMP));
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(null);
                // the 70s stripes inlaid round the playing field: orange, mustard, avocado
                int[] stripes = {0xFFC4552A, 0xFFE0A030, 0xFF6B7A2A};
                float sw = m * 0.13f;
                for (int i = 0; i < 3; i++) {
                    float o = m * 0.78f - i * sw * 1.15f;
                    rf.set(l - o, t - o, r + o, bt + o);
                    st.setStrokeWidth(sw);
                    st.setColor(stripes[i]);
                    b.drawRoundRect(rf, o * 0.9f, o * 0.9f, st);
                }
                st.setStrokeWidth(Math.max(2, m * 0.08f));
                st.setColor(0x80FFE6C8);
                rf.set(l - m + 2, t - m + 2, r + m - 2, bt + m - 2);
                b.drawRoundRect(rf, rad, rad, st);
                // squares: mustard enamel and walnut
                b.save();
                b.clipPath(light);
                p.setShader(new LinearGradient(0, t, 0, bt, 0xFFEFCF7E, 0xFFD9A84A, Shader.TileMode.CLAMP));
                b.drawRect(sq, p);
                b.restore();
                b.save();
                b.clipPath(dark);
                p.setShader(wood(0.9f, 40, 0));
                b.drawRect(sq, p);
                p.setShader(null);
                p.setColor(0x26000000);
                b.drawRect(sq, p);
                b.restore();
                p.setShader(new LinearGradient(0, t, 0, bt, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                b.drawRect(sq, p);
                p.setShader(null);
                st.setStrokeWidth(Math.max(2, m * 0.1f));
                st.setColor(0xFF2A160A);
                b.drawRect(sq, st);
                break;
            }
            case "art": {
                rf.set(l - m, t - m, r + m, bt + m);
                p.setShadowLayer(m * 0.9f, 0, m * 0.35f, 0xB3000000);
                p.setColor(0xFF6A4A1A);
                b.drawRect(rf, p);
                p.clearShadowLayer();
                // gilt: a wide gold band, a bead, a dark fillet
                p.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom,
                        new int[]{0xFFF6DC94, 0xFFB8893A, 0xFFE8C470, 0xFF8A6A2A, 0xFFD8B060},
                        null, Shader.TileMode.CLAMP));
                b.drawRect(rf, p);
                p.setShader(null);
                for (int i = 0; i < 2; i++) {
                    float o = i == 0 ? m * 0.97f : m * 0.45f;
                    rf.set(l - o, t - o, r + o, bt + o);
                    st.setStrokeWidth(m * 0.07f);
                    st.setColor(0x995A3A10);
                    b.drawRect(rf, st);
                    rf.offset(-m * 0.03f, -m * 0.03f);
                    st.setColor(0x99FFF2C8);
                    st.setStrokeWidth(m * 0.035f);
                    b.drawRect(rf, st);
                }
                rf.set(l - m * 0.16f, t - m * 0.16f, r + m * 0.16f, bt + m * 0.16f);
                p.setColor(0xFF2A1A08);
                b.drawRect(rf, p);
                // marble squares: ivory and verde antico
                b.save();
                b.clipPath(light);
                marble(b, sq, 0xFFF0E9DA, 0x8C8A8478, 0x26B0A890, 11);
                b.restore();
                b.save();
                b.clipPath(dark);
                marble(b, sq, 0xFF2C4436, 0x99B8CCBC, 0x400E1A12, 23);
                b.restore();
                // a gentle gallery spotlight
                p.setShader(new RadialGradient((l + r) / 2, t + side * 0.3f, side * 0.85f,
                        new int[]{0x26FFF4DC, 0x00000000, 0x4D000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                b.drawRect(sq, p);
                p.setShader(null);
                labels(b, l, t, cell, n, m, Typeface.create("serif", Typeface.ITALIC), 0xFF3A2408, 0.42f);
                break;
            }
            case "space": {
                rf.set(l - m, t - m, r + m, bt + m);
                float rad = m * 0.35f;
                p.setShadowLayer(m * 0.6f, 0, m * 0.2f, 0xCC000000);
                p.setColor(0xFF3A3E44);
                b.drawRoundRect(rf, rad, rad, p);
                p.clearShadowLayer();
                p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, new int[]{0xFF7A8088, 0xFF4A4F55, 0xFF2E3236},
                        null, Shader.TileMode.CLAMP));
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(null);
                brushed(b, rf);
                st.setStrokeWidth(Math.max(1, g.dp(1)));
                st.setColor(0x66FFFFFF);
                b.drawRoundRect(rf, rad, rad, st);
                for (int i = 0; i < 4; i++) screw(b, i % 2 == 0 ? l - m * 0.5f : r + m * 0.5f, i < 2 ? t - m * 0.5f : bt + m * 0.5f, m * 0.17f);
                // the dark glass display
                rf.set(l - m * 0.12f, t - m * 0.12f, r + m * 0.12f, bt + m * 0.12f);
                p.setColor(0xFF07090A);
                b.drawRect(rf, p);
                p.setColor(0xFF0B110E);
                b.drawPath(dark, p);
                p.setColor(0xFF132019);
                b.drawPath(light, p);
                p.setShader(new RadialGradient((l + r) / 2, (t + bt) / 2, side * 0.7f, 0x1A6FE08A, 0x006FE08A, Shader.TileMode.CLAMP));
                b.drawRect(sq, p);
                p.setShader(null);
                st.setStrokeWidth(Math.max(1, g.dp(0.7f)));
                st.setColor(0x406FE08A);
                for (int i = 1; i < n; i++) {
                    b.drawLine(l + i * cell, t, l + i * cell, bt, st);
                    b.drawLine(l, t + i * cell, r, t + i * cell, st);
                }
                // fine sub-grid ticks at every intersection
                st.setColor(0x806FE08A);
                float tk = cell * 0.06f;
                for (int i = 1; i < n; i++) for (int j = 1; j < n; j++) {
                    float x = l + i * cell, y = t + j * cell;
                    b.drawLine(x - tk, y, x + tk, y, st);
                    b.drawLine(x, y - tk, x, y + tk, st);
                }
                st.setColor(0x996FE08A);
                b.drawRect(sq, st);
                // amber corner brackets
                st.setStrokeWidth(g.dp(2));
                st.setColor(s.accent);
                float bk = cell * 0.35f, o = m * 0.06f;
                corners(b, l - o, t - o, r + o, bt + o, bk);
                // glass reflection
                p.setShader(new LinearGradient(l, t, l + side * 0.6f, t + side * 0.6f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                b.drawRect(sq, p);
                p.setShader(null);
                labels(b, l, t, cell, n, m, Typeface.MONOSPACE, s.accent, 0.4f);
                break;
            }
            case "cozy": {
                rf.set(l - m, t - m, r + m, bt + m);
                float rad = m * 0.45f;
                p.setShadowLayer(m * 1.1f, 0, m * 0.45f, 0xCC000000);
                p.setColor(0xFF3A2010);
                b.drawRoundRect(rf, rad, rad, p);
                p.clearShadowLayer();
                p.setShader(wood(1.1f, 0, 0));
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0x1AFFD8A0, 0x66000000, Shader.TileMode.CLAMP));
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(null);
                // a brass rim round the field
                rf.set(l - m * 0.22f, t - m * 0.22f, r + m * 0.22f, bt + m * 0.22f);
                st.setStrokeWidth(m * 0.16f);
                st.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom,
                        new int[]{0xFFF2D48A, 0xFFB8893A, 0xFFE0B060, 0xFF7A5A20}, null, Shader.TileMode.CLAMP));
                b.drawRect(rf, st);
                st.setShader(null);
                // maple and walnut squares, varnished
                b.save();
                b.clipPath(light);
                p.setColor(0xFFE6C48E);
                b.drawRect(sq, p);
                Paint grain = new Paint(Paint.ANTI_ALIAS_FLAG);
                grain.setShader(wood(1.6f, 10, 0));
                grain.setAlpha(55);
                b.drawRect(sq, grain);
                b.restore();
                b.save();
                b.clipPath(dark);
                p.setShader(wood(1.0f, 60, 30));
                b.drawRect(sq, p);
                p.setShader(null);
                b.restore();
                p.setShader(new LinearGradient(l, t, r, bt, new int[]{0x1FFFFFFF, 0x00000000, 0x33000000}, null, Shader.TileMode.CLAMP));
                b.drawRect(sq, p);
                // firelight from the lower right
                p.setShader(new RadialGradient(r + side * 0.2f, bt + side * 0.1f, side * 1.1f, 0x33FFA040, 0x00FFA040, Shader.TileMode.CLAMP));
                rf.set(l - m, t - m, r + m, bt + m);
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(null);
                labels(b, l, t, cell, n, m, Typeface.create("serif", Typeface.BOLD), 0xCCF2D48A, 0.42f);
                break;
            }
            case "minimal": {
                p.setColor(s.squareLight);
                b.drawPath(light, p);
                p.setColor(s.squareDark);
                b.drawPath(dark, p);
                st.setStrokeWidth(Math.max(1, g.dp(0.75f)));
                st.setColor(GameSkin.alpha(s.ink, 0.85f));
                b.drawRect(sq, st);
                float o = m * 0.35f;
                st.setColor(GameSkin.alpha(s.ink, 0.35f));
                b.drawRect(l - o, t - o, r + o, bt + o, st);
                labels(b, l, t, cell, n, m * 0.85f, Typeface.create("sans-serif-light", Typeface.NORMAL), s.inkDim, 0.36f);
                break;
            }
            case "cs": {
                int gr = s.accent;
                rf.set(l - m, t - m, r + m, bt + m);
                p.setColor(0xFF020A04);
                b.drawRect(rf, p);
                // double rule, like ╔══╗
                st.setStrokeWidth(Math.max(1, g.dp(1.3f)));
                st.setColor(gr);
                st.setShadowLayer(g.dp(5), 0, 0, 0x9946F07A);
                float o1 = g.dp(3), o2 = g.dp(8);
                b.drawRect(l - o1, t - o1, r + o1, bt + o1, st);
                b.drawRect(l - o2, t - o2, r + o2, bt + o2, st);
                st.clearShadowLayer();
                st.setColor(0xFF1A7A38);
                st.setStrokeWidth(Math.max(1, g.dp(0.8f)));
                for (int i = 1; i < n; i++) {
                    b.drawLine(l + i * cell, t, l + i * cell, bt, st);
                    b.drawLine(l, t + i * cell, r, t + i * cell, st);
                }
                // dark squares: a ░ dither of dots
                p.setColor(0x6622A048);
                float step = cell / 7f, dot = Math.max(1.5f, g.dp(1.1f));
                for (int rr = 0; rr < n; rr++) for (int kk = 0; kk < n; kk++) {
                    if ((rr + kk) % 2 == 0) continue;
                    for (int a = 1; a < 7; a++) for (int bb = 1; bb < 7; bb++)
                        if ((a + bb) % 2 == 0)
                            b.drawRect(l + kk * cell + a * step - dot / 2, t + rr * cell + bb * step - dot / 2,
                                    l + kk * cell + a * step + dot / 2, t + rr * cell + bb * step + dot / 2, p);
                }
                labels(b, l, t, cell, n, m, Typeface.MONOSPACE, 0xFF22A048, 0.38f);
                break;
            }
            case "light": {
                rf.set(l - m, t - m, r + m, bt + m);
                float rad = m * 0.8f;
                p.setShadowLayer(m * 1.2f, 0, m * 0.35f, 0x29000000);
                p.setColor(0xF7FFFFFF);
                b.drawRoundRect(rf, rad, rad, p);
                p.clearShadowLayer();
                st.setStrokeWidth(Math.max(1, g.dp(0.75f)));
                st.setColor(0x14000000);
                b.drawRoundRect(rf, rad, rad, st);
                b.save();
                Path clip = new Path();
                clip.addRoundRect(sq, m * 0.3f, m * 0.3f, Path.Direction.CW);
                b.clipPath(clip);
                p.setColor(0xFFF6F3EE);
                b.drawPath(light, p);
                p.setColor(0xFFD3C8B8);
                b.drawPath(dark, p);
                b.restore();
                break;
            }
            default: { // aurora: frosted glass
                rf.set(l - m, t - m, r + m, bt + m);
                float rad = m * 0.9f;
                p.setColor(0x2EFFFFFF);
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                b.drawRoundRect(rf, rad, rad, p);
                p.setShader(null);
                st.setStrokeWidth(Math.max(1, g.dp(1)));
                st.setColor(0x73FFFFFF);
                b.drawRoundRect(rf, rad, rad, st);
                b.save();
                Path clip = new Path();
                clip.addRoundRect(sq, m * 0.4f, m * 0.4f, Path.Direction.CW);
                b.clipPath(clip);
                p.setColor(0x47FFFFFF);
                b.drawPath(light, p);
                p.setColor(0x24000000);
                b.drawPath(dark, p);
                b.restore();
                break;
            }
        }
        p.setShader(null);
    }

    private static int xi(float origin, float cell, int i) { return Math.round(origin + i * cell); }

    /** Windows 95 two-line bevel on a silver face. */
    void bevel(Canvas b, int l, int t, int r, int bt, int u, boolean raised) {
        crisp.setColor(0xFFC0C0C0);
        if (raised) b.drawRect(l, t, r, bt, crisp);
        int o1 = raised ? 0xFFFFFFFF : 0xFF808080, i1 = raised ? 0xFFDFDFDF : 0xFF000000;
        int o2 = raised ? 0xFF000000 : 0xFFFFFFFF, i2 = raised ? 0xFF808080 : 0xFFDFDFDF;
        crisp.setColor(o1);
        b.drawRect(l, t, r, t + u, crisp);
        b.drawRect(l, t, l + u, bt, crisp);
        crisp.setColor(o2);
        b.drawRect(l, bt - u, r, bt, crisp);
        b.drawRect(r - u, t, r, bt, crisp);
        crisp.setColor(i1);
        b.drawRect(l + u, t + u, r - u, t + 2 * u, crisp);
        b.drawRect(l + u, t + u, l + 2 * u, bt - u, crisp);
        crisp.setColor(i2);
        b.drawRect(l + u, bt - 2 * u, r - u, bt - u, crisp);
        b.drawRect(r - 2 * u, t + u, r - u, bt - u, crisp);
    }

    void brushed(Canvas b, RectF r) {
        Random rnd = new Random(7);
        Paint line = new Paint();
        b.save();
        Path clip = new Path();
        clip.addRoundRect(r, r.width() * 0.01f, r.width() * 0.01f, Path.Direction.CW);
        b.clipPath(clip);
        for (float y = r.top; y < r.bottom; y += 1.5f) {
            line.setColor(((4 + rnd.nextInt(18)) << 24) | (rnd.nextBoolean() ? 0xFFFFFF : 0));
            b.drawRect(r.left, y, r.right, y + 1, line);
        }
        b.restore();
    }

    void screw(Canvas b, float x, float y, float rad) {
        p.setShader(new RadialGradient(x - rad * 0.3f, y - rad * 0.3f, rad * 1.3f, 0xFFD8DCE0, 0xFF50555B, Shader.TileMode.CLAMP));
        b.drawCircle(x, y, rad, p);
        p.setShader(null);
        st.setStrokeWidth(rad * 0.25f);
        st.setColor(0xCC202326);
        b.drawLine(x - rad * 0.6f, y + rad * 0.3f, x + rad * 0.6f, y - rad * 0.3f, st);
    }

    void corners(Canvas c, float l, float t, float r, float b, float k) {
        c.drawLine(l, t, l + k, t, st); c.drawLine(l, t, l, t + k, st);
        c.drawLine(r, t, r - k, t, st); c.drawLine(r, t, r, t + k, st);
        c.drawLine(l, b, l + k, b, st); c.drawLine(l, b, l, b - k, st);
        c.drawLine(r, b, r - k, b, st); c.drawLine(r, b, r, b - k, st);
    }

    /** a–h along the bottom and 8–1 down the left, in the frame. */
    private void labels(Canvas b, float l, float t, float cell, int n, float m, Typeface tf, int color, float size) {
        text.setTypeface(tf);
        text.setTextSize(m * size * 1.6f);
        text.setColor(color);
        text.clearShadowLayer();
        float base = -(text.ascent() + text.descent()) / 2;
        for (int i = 0; i < n; i++) {
            b.drawText(String.valueOf((char) ('a' + i)), l + (i + 0.5f) * cell, t + n * cell + m * 0.55f + base, text);
            b.drawText(String.valueOf(n - i), l - m * 0.55f, t + (i + 0.5f) * cell + base, text);
        }
    }

    // ================================================================== square marks

    /** The last move's from and to squares. */
    void lastMove(Canvas c, float x, float y, float cell, boolean darkSq) {
        switch (look()) {
            case "classic":
                crisp.setColor(darkSq ? 0xFF808040 : 0xFFFFFFC0);
                c.drawRect(Math.round(x), Math.round(y), Math.round(x + cell), Math.round(y + cell), crisp);
                return;
            case "cs": square(c, x, y, cell, 0x3046F07A); return;
            case "space": {
                square(c, x, y, cell, 0x1FFFC56A);
                st.setStrokeWidth(g.dp(1.5f));
                st.setColor(0x99FFC56A);
                corners(c, x + cell * 0.08f, y + cell * 0.08f, x + cell * 0.92f, y + cell * 0.92f, cell * 0.16f);
                return;
            }
            case "minimal": square(c, x, y, cell, GameSkin.alpha(0xFFB4361E, 0.14f)); return;
            case "retro": square(c, x, y, cell, 0x59FFB347); return;
            case "art": square(c, x, y, cell, 0x4DE2C27A); return;
            case "cozy": square(c, x, y, cell, 0x4DFFB04A); return;
            case "light": square(c, x, y, cell, 0x4DF2D36A); return;
            default: square(c, x, y, cell, 0x40FFFFFF);
        }
    }

    /** The king in check. */
    void check(Canvas c, float x, float y, float cell) {
        switch (look()) {
            case "classic":
                crisp.setColor(0xFFFF0000);
                c.drawRect(Math.round(x), Math.round(y), Math.round(x + cell), Math.round(y + cell), crisp);
                return;
            case "cs": {
                st.setStrokeWidth(g.dp(2));
                st.setColor(s().ink);
                st.setPathEffect(new DashPathEffect(new float[]{g.dp(6), g.dp(4)}, 0));
                c.drawRect(x + g.dp(4), y + g.dp(4), x + cell - g.dp(4), y + cell - g.dp(4), st);
                st.setPathEffect(null);
                return;
            }
            case "minimal": {
                st.setStrokeWidth(g.dp(2));
                st.setColor(0xFFB4361E);
                c.drawRect(x + g.dp(3), y + g.dp(3), x + cell - g.dp(3), y + cell - g.dp(3), st);
                return;
            }
            default: {
                int red = look().equals("space") ? 0xFFD8582E : look().equals("art") ? 0xFF9A2A22 : 0xFFD8402F;
                p.setShader(new RadialGradient(x + cell / 2, y + cell / 2, cell * 0.62f,
                        GameSkin.alpha(red, 0.85f), GameSkin.alpha(red, 0f), Shader.TileMode.CLAMP));
                c.drawRect(x, y, x + cell, y + cell, p);
                p.setShader(null);
            }
        }
    }

    /** The piece picked up. */
    void selected(Canvas c, float x, float y, float cell) {
        GameSkin s = s();
        switch (look()) {
            case "classic":
                crisp.setColor(0xFF000080);
                c.drawRect(Math.round(x), Math.round(y), Math.round(x + cell), Math.round(y + cell), crisp);
                return;
            case "cs": {
                st.setStrokeWidth(g.dp(2));
                st.setColor(s.ink);
                st.setShadowLayer(g.dp(4), 0, 0, 0x9946F07A);
                float k = cell * 0.22f, i = cell * 0.1f;
                // [ ] brackets
                c.drawLine(x + i, y + i, x + i, y + cell - i, st);
                c.drawLine(x + i, y + i, x + i + k * 0.5f, y + i, st);
                c.drawLine(x + i, y + cell - i, x + i + k * 0.5f, y + cell - i, st);
                c.drawLine(x + cell - i, y + i, x + cell - i, y + cell - i, st);
                c.drawLine(x + cell - i, y + i, x + cell - i - k * 0.5f, y + i, st);
                c.drawLine(x + cell - i, y + cell - i, x + cell - i - k * 0.5f, y + cell - i, st);
                st.clearShadowLayer();
                return;
            }
            case "minimal": {
                st.setStrokeWidth(g.dp(1.5f));
                st.setColor(0xFFB4361E);
                c.drawRect(x + g.dp(2), y + g.dp(2), x + cell - g.dp(2), y + cell - g.dp(2), st);
                return;
            }
            case "space": {
                square(c, x, y, cell, 0x33FFC56A);
                st.setStrokeWidth(g.dp(2));
                st.setColor(s.accent);
                st.setShadowLayer(g.dp(5), 0, 0, 0x99FFC56A);
                c.drawRect(x + g.dp(3), y + g.dp(3), x + cell - g.dp(3), y + cell - g.dp(3), st);
                st.clearShadowLayer();
                return;
            }
            default: {
                int col = look().equals("aurora") ? 0xFFFFFFFF : look().equals("light") ? 0xFF2F6DB8 : s.accent;
                square(c, x, y, cell, GameSkin.alpha(col, look().equals("light") ? 0.22f : 0.35f));
                st.setStrokeWidth(g.dp(3));
                st.setColor(col);
                rf.set(x + g.dp(3), y + g.dp(3), x + cell - g.dp(3), y + cell - g.dp(3));
                float rad = look().equals("art") ? 0 : g.dp(6);
                c.drawRoundRect(rf, rad, rad, st);
            }
        }
    }

    /** A square the picked-up piece can go to (capture rings the piece). */
    void hint(Canvas c, float cx, float cy, float cell, boolean capture) {
        GameSkin s = s();
        switch (look()) {
            case "classic": {
                int u = Math.max(2, Math.round(g.dp(2)));
                crisp.setColor(0xFF000080);
                if (capture) {
                    int x0 = Math.round(cx - cell / 2) + u, y0 = Math.round(cy - cell / 2) + u;
                    int x1 = Math.round(cx + cell / 2) - u, y1 = Math.round(cy + cell / 2) - u, k = Math.round(cell * 0.22f);
                    c.drawRect(x0, y0, x0 + k, y0 + u, crisp); c.drawRect(x0, y0, x0 + u, y0 + k, crisp);
                    c.drawRect(x1 - k, y0, x1, y0 + u, crisp); c.drawRect(x1 - u, y0, x1, y0 + k, crisp);
                    c.drawRect(x0, y1 - u, x0 + k, y1, crisp); c.drawRect(x0, y1 - k, x0 + u, y1, crisp);
                    c.drawRect(x1 - k, y1 - u, x1, y1, crisp); c.drawRect(x1 - u, y1 - k, x1, y1, crisp);
                } else {
                    int h = Math.round(cell * 0.07f);
                    c.drawRect(Math.round(cx) - h, Math.round(cy) - h, Math.round(cx) + h, Math.round(cy) + h, crisp);
                }
                return;
            }
            case "cs": {
                if (capture) {
                    st.setStrokeWidth(g.dp(1.5f));
                    st.setColor(s.ink);
                    st.setPathEffect(new DashPathEffect(new float[]{g.dp(5), g.dp(4)}, 0));
                    st.setShadowLayer(g.dp(4), 0, 0, 0xAA46F07A);
                    c.drawRect(cx - cell * 0.42f, cy - cell * 0.42f, cx + cell * 0.42f, cy + cell * 0.42f, st);
                    st.clearShadowLayer();
                    st.setPathEffect(null);
                    return;
                }
                text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                text.setTextSize(cell * (capture ? 0.32f : 0.4f));
                text.setColor(s.ink);
                text.setShadowLayer(g.dp(4), 0, 0, 0xAA46F07A);
                float by = capture ? cy - cell * 0.32f : cy;
                String ch = capture ? "x" : "·";
                c.drawText(ch, capture ? cx + cell * 0.32f : cx, by - (text.ascent() + text.descent()) / 2, text);
                text.clearShadowLayer();
                return;
            }
            case "space": {
                st.setStrokeWidth(g.dp(1.5f));
                st.setColor(s.accent);
                st.setShadowLayer(g.dp(4), 0, 0, 0x99FFC56A);
                if (capture) {
                    c.drawCircle(cx, cy, cell * 0.44f, st);
                    for (int i = 0; i < 4; i++) {
                        float dx = (i % 2 == 0 ? 1 : 0) * (i < 2 ? 1 : -1), dy = (i % 2 == 1 ? 1 : 0) * (i < 2 ? 1 : -1);
                        c.drawLine(cx + dx * cell * 0.38f, cy + dy * cell * 0.38f, cx + dx * cell * 0.5f, cy + dy * cell * 0.5f, st);
                    }
                } else {
                    float k = cell * 0.1f;
                    c.drawLine(cx - k, cy, cx + k, cy, st);
                    c.drawLine(cx, cy - k, cx, cy + k, st);
                    c.drawCircle(cx, cy, k * 0.6f, st);
                }
                st.clearShadowLayer();
                return;
            }
            case "minimal": {
                if (capture) {
                    st.setStrokeWidth(g.dp(1.5f));
                    st.setColor(0xFFB4361E);
                    c.drawCircle(cx, cy, cell * 0.44f, st);
                } else {
                    p.setColor(s.ink);
                    c.drawCircle(cx, cy, cell * 0.06f, p);
                }
                return;
            }
            default: {
                int col;
                switch (look()) {
                    case "retro": col = 0xFFE0A030; break;
                    case "art": col = 0xFFC9A24A; break;
                    case "cozy": col = 0xFFFFB04A; break;
                    case "light": col = 0xFF2F6DB8; break;
                    default: col = 0xFFFFFFFF;
                }
                if (capture) {
                    st.setStrokeWidth(g.dp(3));
                    st.setColor(GameSkin.alpha(col, 0.9f));
                    c.drawCircle(cx, cy, cell * 0.44f, st);
                } else {
                    p.setShadowLayer(g.dp(3), 0, g.dp(1), 0x66000000);
                    p.setColor(GameSkin.alpha(col, 0.85f));
                    c.drawCircle(cx, cy, cell * 0.12f, p);
                    p.clearShadowLayer();
                }
            }
        }
    }

    /** CS draws its inverse-video cursor under the pieces; true if it did. */
    boolean cursorUnder(Canvas c, float x, float y, float cell) {
        if (!look().equals("cs")) return false;
        float pl = g.pulse();
        p.setColor(GameSkin.alpha(s().accent, 0.75f + 0.25f * pl));
        p.setShadowLayer(g.dp(8), 0, 0, 0x8046F07A);
        c.drawRect(x + 1, y + 1, x + cell - 1, y + cell - 1, p);
        p.clearShadowLayer();
        return true;
    }

    /** The pulsing cursor over a square. */
    void cursor(Canvas c, float x, float y, float cell) {
        float pl = g.pulse();
        GameSkin s = s();
        switch (look()) {
            case "cs": return;
            case "classic": {
                // the dotted focus rectangle, inside a black frame
                int u = Math.max(2, Math.round(g.dp(1.5f)));
                int L = Math.round(x), T = Math.round(y), R = Math.round(x + cell), B = Math.round(y + cell);
                crisp.setColor(0xFF000000);
                c.drawRect(L, T, R, T + u, crisp); c.drawRect(L, B - u, R, B, crisp);
                c.drawRect(L, T, L + u, B, crisp); c.drawRect(R - u, T, R, B, crisp);
                int i = 3 * u;
                crisp.setColor(pl > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
                for (int px = L + i; px < R - i; px += 2 * u) {
                    c.drawRect(px, T + i, px + u, T + i + u, crisp);
                    c.drawRect(px, B - i - u, px + u, B - i, crisp);
                }
                for (int py = T + i; py < B - i; py += 2 * u) {
                    c.drawRect(L + i, py, L + i + u, py + u, crisp);
                    c.drawRect(R - i - u, py, R - i, py + u, crisp);
                }
                crisp.setColor(pl > 0.5f ? 0xFFFFFFFF : 0xFF000000);
                for (int px = L + i + u; px < R - i; px += 2 * u) {
                    c.drawRect(px, T + i, px + u, T + i + u, crisp);
                    c.drawRect(px, B - i - u, px + u, B - i, crisp);
                }
                for (int py = T + i + u; py < B - i; py += 2 * u) {
                    c.drawRect(L + i, py, L + i + u, py + u, crisp);
                    c.drawRect(R - i - u, py, R - i, py + u, crisp);
                }
                return;
            }
            case "space": {
                st.setStrokeWidth(g.dp(3));
                st.setColor(GameSkin.alpha(s.accent, 0.7f + 0.3f * pl));
                st.setShadowLayer(g.dp(6), 0, 0, 0xAAFFC56A);
                float o = g.dp(2) - pl * g.dp(2);
                corners(c, x + o, y + o, x + cell - o, y + cell - o, cell * 0.28f);
                st.clearShadowLayer();
                return;
            }
            case "minimal": {
                st.setStrokeWidth(g.dp(2.5f));
                st.setColor(GameSkin.alpha(0xFFB4361E, 0.65f + 0.35f * pl));
                c.drawRect(x + g.dp(1.5f), y + g.dp(1.5f), x + cell - g.dp(1.5f), y + cell - g.dp(1.5f), st);
                return;
            }
            case "art": {
                st.setStrokeWidth(g.dp(3));
                st.setColor(GameSkin.alpha(0xFFF2D48A, 0.7f + 0.3f * pl));
                st.setShadowLayer(g.dp(6), 0, 0, 0x99000000);
                c.drawRect(x + g.dp(2), y + g.dp(2), x + cell - g.dp(2), y + cell - g.dp(2), st);
                st.clearShadowLayer();
                st.setStrokeWidth(g.dp(1));
                st.setColor(0xCC8A6A2A);
                c.drawRect(x + g.dp(5.5f), y + g.dp(5.5f), x + cell - g.dp(5.5f), y + cell - g.dp(5.5f), st);
                return;
            }
            default: {
                int col; float w = 3.5f, rad;
                switch (look()) {
                    case "retro": col = 0xFFFFB347; w = 5f; rad = cell * 0.18f; break;
                    case "cozy": col = 0xFFFFB04A; rad = cell * 0.12f; break;
                    case "light": col = 0xFF1C1C1E; w = 3f; rad = cell * 0.14f; break;
                    default: col = 0xFFFFFFFF; rad = cell * 0.16f;
                }
                st.setStrokeWidth(g.dp(w));
                st.setColor(GameSkin.alpha(col, 0.6f + 0.4f * pl));
                if (!look().equals("light")) st.setShadowLayer(g.dp(6 + 4 * pl), 0, 0, GameSkin.alpha(col, 0.7f));
                rf.set(x + g.dp(3), y + g.dp(3), x + cell - g.dp(3), y + cell - g.dp(3));
                c.drawRoundRect(rf, rad, rad, st);
                st.clearShadowLayer();
            }
        }
    }

    private void square(Canvas c, float x, float y, float cell, int color) {
        p.setShader(null);
        p.setColor(color);
        c.drawRect(x, y, x + cell, y + cell, p);
    }

    // ================================================================== chessmen

    private static final String WHITE = " ♙♘♗♖♕♔", BLACK = " ♟♞♝♜♛♚", LETTERS = " PNBRQK";
    private static final float TS0 = 100f;
    /** Glyph outlines (white set) and their silhouettes at TS0, centered on 0,0. */
    private Path[] lines, sils, solids;
    private Boolean glyphs;
    private final HashMap<Integer, Bitmap> cache = new HashMap<>();
    private String cacheKey;

    private boolean glyphs() {
        if (glyphs == null) {
            Paint t = new Paint();
            glyphs = t.hasGlyph("♞") && t.hasGlyph("♘");
        }
        return glyphs;
    }

    private void buildPaths() {
        if (lines != null) return;
        lines = new Path[7];
        sils = new Path[7];
        solids = new Path[7];
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(TS0);
        float by = -(t.descent() + t.ascent()) / 2;
        for (int i = 1; i <= 6; i++) {
            String w = WHITE.charAt(i) + "︎", bl = BLACK.charAt(i) + "︎";
            Path lp = new Path();
            t.getTextPath(w, 0, w.length(), 0, by, lp);
            lines[i] = lp;
            Path sp = new Path();
            t.getTextPath(bl, 0, bl.length(), 0, by, sp);
            solids[i] = sp;
            sils[i] = silhouette(lp);
        }
    }

    /** The outer contours of a glyph, filled: the piece's shape without its inner lines. */
    private static Path silhouette(Path glyph) {
        PathMeasure pm = new PathMeasure(glyph, false);
        java.util.List<Path> cs = new java.util.ArrayList<>();
        java.util.List<RectF> bs = new java.util.ArrayList<>();
        do {
            Path seg = new Path();
            if (pm.getLength() <= 0) continue;
            pm.getSegment(0, pm.getLength(), seg, true);
            seg.close();
            RectF r = new RectF();
            seg.computeBounds(r, true);
            cs.add(seg);
            bs.add(r);
        } while (pm.nextContour());
        Path out = new Path();
        for (int i = 0; i < cs.size(); i++) {
            boolean inside = false;
            RectF a = bs.get(i);
            for (int j = 0; j < cs.size() && !inside; j++) {
                if (i == j) continue;
                RectF b = bs.get(j);
                if (b.left <= a.left + 0.01f && b.top <= a.top + 0.01f && b.right >= a.right - 0.01f && b.bottom >= a.bottom - 0.01f
                        && (b.width() * b.height() > a.width() * a.height() + 0.01f || j < i)) inside = true;
            }
            if (!inside) {
                Path c = cs.get(i);
                c.setFillType(Path.FillType.WINDING);
                out.op(c, Path.Op.UNION);
            }
        }
        return out;
    }

    private void checkCache(float cell) {
        String k = key() + (int) cell;
        if (!k.equals(cacheKey)) {
            for (Bitmap b : cache.values()) b.recycle();
            cache.clear();
            cacheKey = k;
        }
    }

    /** A chessman (type 1 pawn … 6 king; side 1 White, 2 Black) centered in its square. */
    void chessman(Canvas c, float cx, float cy, float cell, int type, int side, boolean onCursor) {
        if (look().equals("cs") || !glyphs()) {
            letter(c, cx, cy, cell, type, side, onCursor);
            return;
        }
        checkCache(cell);
        int k = side * 8 + type;
        Bitmap b = cache.get(k);
        if (b == null) {
            buildPaths();
            b = look().equals("classic") ? pixelMan(type, side == 1) : renderMan(cell, type, side == 1);
            cache.put(k, b);
        }
        blit(c, b, cx, cy, cell);
    }

    private void blit(Canvas c, Bitmap b, float cx, float cy, float cell) {
        if (look().equals("classic")) {
            int k = Math.max(1, (int) (cell * 0.92f / b.getWidth()));
            int w = b.getWidth() * k, x = Math.round(cx - w / 2f), y = Math.round(cy - w / 2f);
            rf.set(x, y, x + w, y + w);
            c.drawBitmap(b, null, rf, crisp);
        } else {
            c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, null);
        }
    }

    private void letter(Canvas c, float cx, float cy, float cell, int type, int side, boolean inverse) {
        GameSkin s = s();
        boolean cs = look().equals("cs");
        text.setTypeface(Typeface.create(cs ? Typeface.MONOSPACE : Typeface.SANS_SERIF, Typeface.BOLD));
        text.setTextSize(cell * 0.56f);
        String ch = String.valueOf(LETTERS.charAt(type));
        if (side == 2 && cs) ch = ch.toLowerCase();
        int col = cs ? (side == 1 ? s.ink : s.accent) : (side == 1 ? s.pieceLight : s.pieceDark);
        if (inverse) col = s.onAccent;
        else if (cs) text.setShadowLayer(g.dp(side == 1 ? 7 : 4), 0, 0, side == 1 ? 0xCC46F07A : 0x8846F07A);
        text.setColor(col);
        float by = cy - (text.ascent() + text.descent()) / 2;
        c.drawText(ch, cx, by, text);
        text.clearShadowLayer();
        if (cs && side == 2 && !inverse) {
            // black men are dimmer, underlined like a cursor block
            p.setColor(0x9946F07A);
            c.drawRect(cx - cell * 0.15f, cy + cell * 0.26f, cx + cell * 0.15f, cy + cell * 0.29f, p);
        }
    }

    /** One chessman painted in the look's material, at full resolution. */
    private Bitmap renderMan(float cell, int type, boolean white) {
        int size = (int) Math.ceil(cell);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas b = new Canvas(bmp);
        float sc = cell * 0.8f / TS0;
        b.translate(size / 2f, size / 2f);
        b.scale(sc, sc);
        float u = 1f / sc;   // one screen pixel in glyph units
        Path sil = sils[type], ln = lines[type];
        GameSkin s = s();
        switch (look()) {
            case "space": {
                int col = white ? s.p1 : s.p2;
                // projector base
                p.setShader(new RadialGradient(0, 36, 30, GameSkin.alpha(col, 0.45f), GameSkin.alpha(col, 0f), Shader.TileMode.CLAMP));
                b.save();
                b.scale(1f, 0.22f, 0, 36);
                b.drawCircle(0, 36, 30, p);
                b.restore();
                p.setShader(null);
                p.setColor(GameSkin.alpha(col, 0.16f));
                b.drawPath(sil, p);
                b.save();
                b.clipPath(sil);
                p.setColor(GameSkin.alpha(col, 0.14f));
                for (float y = -50; y < 50; y += 3.2f) b.drawRect(-50, y, 50, y + 1.1f, p);
                b.restore();
                p.setColor(col);
                p.setShadowLayer(5 * u, 0, 0, GameSkin.alpha(col, 0.9f));
                b.drawPath(ln, p);
                p.clearShadowLayer();
                p.setColor(GameSkin.alpha(0xFFFFFFFF, 0.5f));
                b.drawPath(ln, p);
                return bmp;
            }
            case "minimal": {
                boolean solid = white == night();
                if (solid) {
                    p.setColor(s.ink);
                    b.drawPath(solids[type], p);
                } else {
                    p.setColor(s.board);
                    b.drawPath(sil, p);
                    p.setColor(s.ink);
                    b.drawPath(ln, p);
                }
                return bmp;
            }
            case "retro":
                material(b, sil, ln, u, white ? 0xFFFFF8E6 : 0xFFE8784A, white ? 0xFFE6D4A8 : 0xFFA8401C,
                        white ? 0xFF3A2414 : 0xFF2A160A, 0xFF2A160A, 3.2f, 0x99000000, 0x66FFFFFF, 0);
                return bmp;
            case "art":
                material(b, sil, ln, u, white ? 0xFFFFFBF0 : 0xFF4A3C32, white ? 0xFFD8CBAE : 0xFF0C0806,
                        white ? 0xFF4A3424 : 0xFFD8B060, white ? 0 : 0xFF0C0806, white ? 0 : 1.2f, 0xB3000000,
                        white ? 0x80FFFFFF : 0x40FFF2D0, 0);
                return bmp;
            case "cozy":
                material(b, sil, ln, u, white ? 0xFFF8E8C8 : 0xFF7A4228, white ? 0xFFC49E68 : 0xFF2A140A,
                        white ? 0xFF5A3A1E : 0xFFB88048, 0, 0, 0xCC000000, 0x40FFF0D0, 0x66FFA040);
                return bmp;
            case "light":
                material(b, sil, ln, u, white ? 0xFFFFFFFF : 0xFF62626A, white ? 0xFFE0E0E6 : 0xFF2A2A2E,
                        white ? 0xFF3A3A40 : 0xFFB4B4BC, 0, 0, 0x40000000, white ? 0xB3FFFFFF : 0x40FFFFFF, 0);
                return bmp;
            default: // aurora
                material(b, sil, ln, u, white ? 0xFFFFFFFF : 0xFF4A4A56, white ? 0xFFDCDAE2 : 0xFF141418,
                        white ? 0xFF2A2A34 : 0xFFA0A0AE, 0, 0, 0x66000000, white ? 0x99FFFFFF : 0x55FFFFFF, 0);
                return bmp;
        }
    }

    /**
     * A solid piece: soft shadow, an optional chunky outline, a lit gradient body, a
     * sheen, an optional firelight rim, then the glyph's carved lines on top.
     */
    private void material(Canvas b, Path sil, Path ln, float u, int top, int bot, int ink, int outline, float outlineW,
                          int shadow, int sheen, int rim) {
        RectF r = new RectF();
        sil.computeBounds(r, true);
        p.setShader(null);
        p.setColor(bot);
        p.setShadowLayer(6 * u, 0, 3 * u, shadow);
        b.drawPath(sil, p);
        p.clearShadowLayer();
        if (outlineW > 0) {
            st.setColor(outline);
            st.setStrokeWidth(outlineW * 2 * u);
            b.drawPath(sil, st);
        }
        p.setShader(new LinearGradient(r.left, r.top, r.right * 0.6f, r.bottom, top, bot, Shader.TileMode.CLAMP));
        b.drawPath(sil, p);
        b.save();
        b.clipPath(sil);
        p.setShader(new RadialGradient(r.left + r.width() * 0.3f, r.top + r.height() * 0.22f, r.width() * 0.55f,
                sheen, sheen & 0xFFFFFF, Shader.TileMode.CLAMP));
        b.drawRect(r, p);
        if (rim != 0) {
            p.setShader(new LinearGradient(r.right, 0, r.right - r.width() * 0.35f, 0, rim, rim & 0xFFFFFF, Shader.TileMode.CLAMP));
            b.drawRect(r, p);
        }
        b.restore();
        p.setShader(null);
        p.setColor(ink);
        b.drawPath(ln, p);
    }

    /** A 95-style pixel-art chessman: the glyph rasterised at a low resolution, outlined and shaded. */
    private Bitmap pixelMan(int type, boolean white) {
        final int N = 24;
        RectF all = new RectF(), one = new RectF();
        for (int i = 1; i <= 6; i++) { sils[i].computeBounds(one, true); all.union(one); }
        float sc = (N - 2) / Math.max(all.width(), all.height());
        float ox = N / 2f - all.centerX() * sc, oy = N / 2f - all.centerY() * sc;
        Bitmap a = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888), l = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setColor(0xFFFFFFFF);
        Canvas ca = new Canvas(a), cl = new Canvas(l);
        ca.translate(ox, oy); ca.scale(sc, sc);
        cl.translate(ox, oy); cl.scale(sc, sc);
        ca.drawPath(sils[type], q);
        cl.drawPath(lines[type], q);
        int[] am = new int[N * N], lm = new int[N * N], out = new int[N * N];
        a.getPixels(am, 0, N, 0, 0, N, N);
        l.getPixels(lm, 0, N, 0, 0, N, N);
        a.recycle();
        l.recycle();
        boolean[] in = new boolean[N * N], line = new boolean[N * N];
        for (int i = 0; i < N * N; i++) {
            in[i] = (am[i] >>> 24) >= 110;
            line[i] = (lm[i] >>> 24) >= 120;
            if (line[i]) in[i] = true;
        }
        for (int y = 0; y < N; y++) {
            int lo = N, hi = -1;
            for (int x = 0; x < N; x++) if (in[y * N + x]) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                if (!in[i]) continue;
                boolean edge = x == 0 || y == 0 || x == N - 1 || y == N - 1
                        || !in[i - 1] || !in[i + 1] || !in[i - N] || !in[i + N];
                if (white) {
                    if (edge || line[i]) out[i] = 0xFF000000;
                    else if (x > hi - (hi - lo) * 0.3f) out[i] = 0xFFC0C0C0;
                    else out[i] = 0xFFFFFFFF;
                } else {
                    if (edge) out[i] = 0xFF000000;
                    else if (line[i]) out[i] = 0xFF808080;
                    else if (x <= lo + 1 + (hi - lo) * 0.12f) out[i] = 0xFF808080;
                    else out[i] = 0xFF000000;
                }
            }
        }
        // white men on white squares want a little more weight: thicken the black outline outward by one pixel at the bottom
        Bitmap bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
        bmp.setPixels(out, 0, N, 0, 0, N, N);
        return bmp;
    }

    // ================================================================== checkers

    /** A checker (side 1 or 2), crowned when king. */
    void checker(Canvas c, float cx, float cy, float cell, int side, boolean king, boolean onCursor) {
        if (look().equals("cs")) {
            GameSkin s = s();
            text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            text.setTextSize(cell * 0.5f);
            String ch = side == 1 ? (king ? "@" : "o") : (king ? "X" : "x");
            if (king) ch = "[" + ch + "]";
            if (king) text.setTextSize(cell * 0.36f);
            int col = onCursor ? s.onAccent : side == 1 ? s.ink : s.accent;
            if (!onCursor) text.setShadowLayer(g.dp(side == 1 ? 7 : 4), 0, 0, 0xAA46F07A);
            text.setColor(col);
            c.drawText(ch, cx, cy - (text.ascent() + text.descent()) / 2, text);
            text.clearShadowLayer();
            return;
        }
        checkCache(cell);
        int k = 100 + side * 2 + (king ? 1 : 0);
        Bitmap b = cache.get(k);
        if (b == null) {
            b = look().equals("classic") ? pixelChecker(20, side == 1 ? 0xFFFF0000 : 0xFF000000,
                    side == 1 ? 0xFFFF8080 : 0xFF808080, side == 1 ? 0xFF800000 : 0xFF404040, true, king)
                    : renderChecker(cell, side, king);
            cache.put(k, b);
        }
        blit(c, b, cx, cy, cell);
    }

    private Bitmap renderChecker(float cell, int side, boolean king) {
        int size = (int) Math.ceil(cell);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas b = new Canvas(bmp);
        float cx = size / 2f, r = cell * 0.36f, h = r * 0.16f;
        GameSkin s = s();
        boolean one = side == 1;
        switch (look()) {
            case "space": {
                int col = one ? s.p1 : s.p2;
                p.setColor(GameSkin.alpha(col, 0.16f));
                b.drawCircle(cx, cx, r, p);
                st.setColor(col);
                st.setShadowLayer(g.dp(4), 0, 0, GameSkin.alpha(col, 0.9f));
                st.setStrokeWidth(g.dp(2.2f));
                b.drawCircle(cx, cx, r, st);
                st.setStrokeWidth(g.dp(1.2f));
                b.drawCircle(cx, cx, r * 0.62f, st);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    b.drawLine(cx + (float) Math.cos(a) * r * 0.78f, cx + (float) Math.sin(a) * r * 0.78f,
                            cx + (float) Math.cos(a) * r * 0.9f, cx + (float) Math.sin(a) * r * 0.9f, st);
                }
                if (king) {
                    st.setStrokeWidth(g.dp(1.8f));
                    b.drawPath(crown(cx, cx, r * 0.75f), st);
                }
                st.clearShadowLayer();
                return bmp;
            }
            case "minimal": {
                boolean solid = one == night();
                if (solid) {
                    p.setColor(s.ink);
                    b.drawCircle(cx, cx, r, p);
                    st.setColor(s.board);
                    st.setStrokeWidth(g.dp(1));
                    b.drawCircle(cx, cx, r * 0.7f, st);
                } else {
                    p.setColor(s.board);
                    b.drawCircle(cx, cx, r, p);
                    st.setColor(s.ink);
                    st.setStrokeWidth(g.dp(2));
                    b.drawCircle(cx, cx, r - g.dp(1), st);
                    st.setStrokeWidth(g.dp(1));
                    b.drawCircle(cx, cx, r * 0.7f, st);
                }
                if (king) {
                    p.setColor(solid ? s.board : s.ink);
                    b.drawPath(crown(cx, cx, r * 0.7f), p);
                }
                return bmp;
            }
        }
        int top, base, edge, ridge, crown, outline = 0;
        int sheen = 0x80FFFFFF, rim = 0;
        switch (look()) {
            case "retro":
                top = one ? 0xFFFFF8E6 : 0xFFEA8050; base = one ? 0xFFF0E0B8 : 0xFFC4552A; edge = one ? 0xFFC8B080 : 0xFF7A3014;
                ridge = 0x803A2414; crown = 0xFFE0A030; outline = 0xFF2A160A; break;
            case "art":
                top = one ? 0xFFFFFBF2 : 0xFF54463A; base = one ? 0xFFEDE4D0 : 0xFF221A14; edge = one ? 0xFFB8A888 : 0xFF080504;
                ridge = one ? 0x66806040 : 0xCCC9A24A; crown = 0xFFE2C27A; sheen = one ? 0x99FFFFFF : 0x40FFF0D0; break;
            case "cozy":
                top = one ? 0xFFFAEACC : 0xFF844A2C; base = one ? 0xFFDCBC8A : 0xFF4A2414; edge = one ? 0xFF9A7848 : 0xFF1A0C06;
                ridge = one ? 0x665A3A1E : 0x80E0A860; crown = 0xFFE8B860; sheen = 0x4DFFF0D0; rim = 0x66FFA040; break;
            case "light":
                top = one ? 0xFFFFFFFF : 0xFF6A6A72; base = one ? 0xFFEEEEF2 : 0xFF38383E; edge = one ? 0xFFC0C0C8 : 0xFF18181C;
                ridge = one ? 0x33000000 : 0x40FFFFFF; crown = 0xFFD8A84A; break;
            default:
                top = one ? 0xFFFFFFFF : 0xFF565662; base = one ? 0xFFE6E4EA : 0xFF26262C; edge = one ? 0xFFA8A6AE : 0xFF0C0C10;
                ridge = one ? 0x33000000 : 0x4DFFFFFF; crown = 0xFFF2D48A;
        }
        float y0 = cx + (king ? h * 1.4f : h * 0.5f);
        man(b, cx, y0, r, h, top, base, edge, ridge, outline, sheen, rim, !king);
        if (king) {
            man(b, cx, y0 - h * 2.3f, r, h, top, base, edge, ridge, outline, sheen, rim, false);
            Path cr = crown(cx, y0 - h * 2.3f, r * 0.72f);
            p.setShader(null);
            p.setColor(crown);
            p.setShadowLayer(g.dp(1.5f), 0, g.dp(0.8f), 0x80000000);
            b.drawPath(cr, p);
            p.clearShadowLayer();
            st.setStrokeWidth(g.dp(1));
            st.setColor(0x99000000 & 0xFF000000 | 0x3A2414);
            st.setColor(0xB33A2414);
            b.drawPath(cr, st);
        }
        return bmp;
    }

    /** One checker: shadow, a thick side, a lit top with two ridges. */
    private void man(Canvas b, float cx, float cy, float r, float h, int top, int base, int edge, int ridge, int outline,
                     int sheen, int rim, boolean shadow) {
        p.setShader(null);
        if (shadow) {
            p.setColor(edge);
            p.setShadowLayer(r * 0.22f, 0, r * 0.12f, 0x99000000);
            b.drawCircle(cx, cy + h, r, p);
            p.clearShadowLayer();
        }
        if (outline != 0) {
            p.setColor(outline);
            b.drawCircle(cx, cy + h * 0.5f, r + g.dp(2), p);
            b.drawCircle(cx, cy, r + g.dp(2), p);
        }
        // the side
        p.setColor(edge);
        b.drawCircle(cx, cy + h, r, p);
        b.drawRect(cx - r, cy, cx + r, cy + h, p);
        // the top face
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.6f,
                new int[]{top, base, Game.shade(base)}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        b.drawCircle(cx, cy, r, p);
        p.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 0.7f, sheen, sheen & 0xFFFFFF, Shader.TileMode.CLAMP));
        b.drawCircle(cx, cy, r, p);
        if (rim != 0) {
            p.setShader(new LinearGradient(cx + r, 0, cx + r * 0.3f, 0, rim, rim & 0xFFFFFF, Shader.TileMode.CLAMP));
            b.drawCircle(cx, cy, r, p);
        }
        p.setShader(null);
        st.setShader(null);
        st.setStrokeWidth(Math.max(1, r * 0.05f));
        st.setColor(ridge);
        b.drawCircle(cx, cy, r * 0.78f, st);
        b.drawCircle(cx, cy, r * 0.56f, st);
        st.setColor(0x33FFFFFF);
        b.drawCircle(cx + r * 0.02f, cy + r * 0.03f, r * 0.76f, st);
    }

    /** A three-point crown centered on (cx, cy), w wide. */
    static Path crown(float cx, float cy, float w) {
        float h = w * 0.62f, l = cx - w / 2, t = cy - h / 2;
        Path c = new Path();
        c.moveTo(l, t + h);
        c.lineTo(l, t + h * 0.2f);
        c.lineTo(l + w * 0.27f, t + h * 0.55f);
        c.lineTo(cx, t);
        c.lineTo(l + w * 0.73f, t + h * 0.55f);
        c.lineTo(l + w, t + h * 0.2f);
        c.lineTo(l + w, t + h);
        c.close();
        return c;
    }

    private static final String[] PIXEL_CROWN = {
            ".o...o...o.",
            "oyo.oyo.oyo",
            "oyyoyyyoyyo",
            "oyyyyyyyyyo",
            "oyyyyyyyyyo",
            "ooooooooooo"};

    /**
     * A pixel-art disc, N=20 pixels: black outline, a highlight at the top-left, shade at
     * the bottom-right, and optionally the raised ridge of a checker and a crown.
     */
    static Bitmap pixelChecker(int N, int fill, int hi, int lo, boolean ridge, boolean king) {
        int[] out = new int[N * N];
        float c = (N - 1) / 2f, R = N / 2f - 0.3f;
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            float dx = x - c, dy = y - c, d = (float) Math.hypot(dx, dy);
            if (d > R) continue;
            int col;
            if (d > R - 1.15f) col = 0xFF000000;
            else if (d > R - 2.6f && dx + dy < -R * 0.55f) col = hi;
            else if (d > R - 2.6f && dx + dy > R * 0.55f) col = lo;
            else if (ridge && d > R * 0.55f && d < R * 0.55f + 1.05f) col = dx + dy > 0 ? hi : lo;
            else col = fill;
            out[y * N + x] = col;
        }
        if (king) {
            int ox = (N - 11) / 2, oy = (N - 6) / 2;
            for (int y = 0; y < 6; y++) for (int x = 0; x < 11; x++) {
                char ch = PIXEL_CROWN[y].charAt(x);
                if (ch == 'o') out[(oy + y) * N + ox + x] = 0xFF000000;
                else if (ch == 'y') out[(oy + y) * N + ox + x] = 0xFFFFFF00;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
        bmp.setPixels(out, 0, N, 0, 0, N, N);
        return bmp;
    }
}
