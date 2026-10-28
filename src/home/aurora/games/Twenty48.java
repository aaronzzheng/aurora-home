package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 2048: arrows slide every tile; equal tiles merge. Grid is 5x5, 4x4 or 3x3 by level. */
public class Twenty48 extends Game {

    private static final long POP_MS = 160;
    private int S = 4;
    private int[] g = new int[16];
    private long[] popAt = new long[16];
    private final Random rnd = new Random();
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int score;
    private boolean reached;

    public Twenty48(Context c) {
        super(c);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
    }

    @Override public String title() { return "2048"; }
    @Override public boolean solo() { return true; }
    @Override protected boolean alternateStart() { return false; }
    @Override public String hint() { return "Arrows slide the tiles  ·  Back for menu"; }

    @Override
    protected void newGame() {
        S = level == 0 ? 5 : level == 1 ? 4 : 3;
        g = new int[S * S];
        popAt = new long[S * S];
        score = 0;
        reached = false;
        spawn();
        spawn();
        report();
        if (host != null) host.onInfo("Join the numbers to reach " + goal());
    }

    /** The tile to aim for: 2048 can't be reached on the 3x3 grid, so it aims for 256. */
    private int goal() { return S == 3 ? 256 : 2048; }

    private String bestKey() { return S == 4 ? "best_2048" : "best_2048_" + S; }

    private int best() { return prefs().getInt(bestKey(), 0); }

    private void report() {
        if (score > best()) prefs().edit().putInt(bestKey(), score).apply();
        if (host != null) host.onScore(String.valueOf(score), "Best  " + best());
    }

    private void spawn() {
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < g.length; i++) if (g[i] == 0) empty.add(i);
        if (empty.isEmpty()) return;
        int i = empty.get(rnd.nextInt(empty.size()));
        g[i] = rnd.nextInt(10) == 0 ? 4 : 2;
        popAt[i] = SystemClock.uptimeMillis();
    }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        boolean moved = false;
        long now = SystemClock.uptimeMillis();
        for (int line = 0; line < S; line++) {
            // indexes of this row/column in the direction tiles travel toward
            int[] idx = new int[S];
            for (int k = 0; k < S; k++) {
                int pos = (dx > 0 || dy > 0) ? S - 1 - k : k;
                idx[k] = dx != 0 ? line * S + pos : pos * S + line;
            }
            int[] vals = new int[S];
            int n = 0;
            for (int k = 0; k < S; k++) if (g[idx[k]] != 0) vals[n++] = g[idx[k]];
            int[] out = new int[S];
            boolean[] merged = new boolean[S];
            int o = 0;
            for (int k = 0; k < n; k++) {
                if (o > 0 && out[o - 1] == vals[k] && !merged[o - 1]) {
                    out[o - 1] *= 2;
                    merged[o - 1] = true;
                    score += out[o - 1];
                    if (out[o - 1] == goal() && !reached) { reached = true; host.onInfo(goal() + "! Keep going for a high score"); }
                } else {
                    out[o++] = vals[k];
                }
            }
            for (int k = 0; k < S; k++) {
                if (g[idx[k]] != out[k]) moved = true;
                g[idx[k]] = out[k];
                if (merged[k]) popAt[idx[k]] = now;
            }
        }
        if (moved) {
            spawn();
            report();
            if (!canMove()) {
                host.onInfo("No moves left");
                finish(0);
            }
        }
        return moved;
    }

    private boolean canMove() {
        for (int i = 0; i < g.length; i++) {
            if (g[i] == 0) return true;
            if (i % S < S - 1 && g[i] == g[i + 1]) return true;
            if (i / S < S - 1 && g[i] == g[i + S]) return true;
        }
        return false;
    }

    @Override protected boolean playAtCursor() { return false; }
    @Override protected void cpuMove() { }
    @Override protected int winner() { return -1; }

    // ------------------------------------------------------------------ drawing: one board per look

    private BoardLook look;

    private BoardLook look() {
        GameSkin s = skin();
        if (look == null || look.s != s) look = new BoardLook(this, s);
        return look;
    }

    private static final int[] RETRO = {0xFFF4E8CC, 0xFFEBD3A0, 0xFFE0A526, 0xFFE58A2E, 0xFFD2601F, 0xFFB4441A,
            0xFF8A9A3A, 0xFF6E7B2B, 0xFF2F6A6A, 0xFF7A4422, 0xFF4A2410};
    private static final int[] ART = {0xFFF8F2E6, 0xFFEFE3C8, 0xFFE2CFA0, 0xFFD8B868, 0xFFC9A24A, 0xFFB8893A,
            0xFF9A2A22, 0xFF7E1F1C, 0xFF2F4A35, 0xFF1F3325, 0xFF1C1612};
    private static final int[] COZY = {0xFFEED8B0, 0xFFE6C690, 0xFFDDB070, 0xFFD09A58, 0xFFC08048, 0xFFA86838,
            0xFF8A5030, 0xFF6E3C22, 0xFFD9A84E, 0xFFE8B860, 0xFFF2D080};
    private static final int[] SPACE = {0xFF7CF29A, 0xFF7CF29A, 0xFFB8F27C, 0xFFE8E070, 0xFFFFC56A, 0xFFFFA24A,
            0xFFFF8A3A, 0xFFFF6A3A, 0xFFFFD8A0, 0xFFFFF0D0, 0xFFFFFFFF};

    private static int step(int v) { return Math.min(10, Integer.numberOfTrailingZeros(v) - 1); }

    private static boolean dark(int col) {
        int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
        return r * 0.299 + g * 0.587 + b * 0.114 < 150;
    }

    @Override
    protected void onDraw(Canvas c) {
        BoardLook k = look();
        String id = k.id();
        float[] m = k.margins();
        float W = getWidth(), H = getHeight(), u = k.u;
        float side = Math.min(W - 2 * m[1], H - m[0] - m[2]) * 0.92f, gap = side * (id.equals("cs") ? 0.015f : 0.03f);
        float cell = (side - gap * (S + 1)) / S;
        if (id.equals("classic")) { cell = (float) Math.floor(cell / k.px) * k.px; gap = Math.round(gap / k.px) * k.px; side = cell * S + gap * (S + 1); }
        float left = Math.round((W - side) / 2), top = Math.round((H - side + m[0] - m[2]) / 2);
        k.board(c, left, top, left + side, top + side, title());
        if (id.equals("classic")) {
            k.crisp.setColor(BoardLook.W_SHADOW);
            c.drawRect(left, top, left + side, top + side, k.crisp);
        }
        long now = SystemClock.uptimeMillis();
        boolean anim = false;
        if (id.equals("cs")) k.tx.setTypeface(BoardLook.MONO_BOLD);
        for (int i = 0; i < g.length; i++) {
            float x = left + gap + (i % S) * (cell + gap), y = top + gap + (i / S) * (cell + gap);
            empty(c, k, id, x, y, cell);
            int v = g[i];
            if (v == 0) continue;
            float t = Math.min(1f, (now - popAt[i]) / (float) POP_MS);
            if (t < 1f) anim = true;
            float scale = t < 1f ? 0.8f + 0.3f * (float) Math.sin(t * Math.PI) + 0.2f * t : 1f;
            c.save();
            c.scale(scale, scale, x + cell / 2, y + cell / 2);
            tile(c, k, id, x, y, cell, v);
            c.restore();
        }
        k.screenGlare(c, left, top, left + side, top + side);
        if (id.equals("cs")) k.scanlines(c, left - u * 14, top - u * 30, left + side + u * 14, top + side + u * 14);
        k.reset();
        if (anim) postInvalidateOnAnimation();
    }

    private float numSize(String s, float cell) {
        return cell * (s.length() <= 2 ? 0.5f : s.length() == 3 ? 0.4f : s.length() == 4 ? 0.31f : 0.25f);
    }

    private void empty(Canvas c, BoardLook k, String id, float x, float y, float cell) {
        float u = k.u, rad = cell * 0.08f;
        k.reset();
        k.r.set(x, y, x + cell, y + cell);
        switch (id) {
            case "classic":
                k.crisp.setColor(BoardLook.W_FACE);
                c.drawRect(x, y, x + cell, y + cell, k.crisp);
                k.bevel(c, x, y, x + cell, y + cell, false);
                return;
            case "cs": {
                float cw = cell / 8f, rh = cell / 4f;
                k.tx.setTextSize(Math.min(cw / 0.6f, rh * 0.9f));
                k.glow(true, BoardLook.a(skin().inkDim, 0xB3));
                k.glyphs(c, "+------+", x, y + rh * 0.5f, cw);
                k.glyphs(c, "|      |", x, y + rh * 1.5f, cw);
                k.glyphs(c, "|  .   |", x, y + rh * 2.5f, cw);
                k.glyphs(c, "+------+", x, y + rh * 3.5f, cw);
                k.glow(false, 0);
                return;
            }
            case "retro":
                k.p.setColor(0x26593418);
                c.drawRoundRect(k.r, cell * 0.16f, cell * 0.16f, k.p);
                k.ln.setStrokeWidth(u * 1.5f);
                k.ln.setColor(0x1F000000);
                c.drawRoundRect(k.r, cell * 0.16f, cell * 0.16f, k.ln);
                return;
            case "art":
                k.ln.setStrokeWidth(u);
                k.ln.setColor(0x4D6A4A2A);
                c.drawRect(k.r, k.ln);
                k.r.inset(u * 4, u * 4);
                k.ln.setColor(0x266A4A2A);
                c.drawRect(k.r, k.ln);
                return;
            case "space":
                k.p.setColor(0xCC0A1210);
                c.drawRoundRect(k.r, u * 4, u * 4, k.p);
                k.ln.setStrokeWidth(Math.max(1f, u));
                k.ln.setColor(0x337CF29A);
                c.drawRoundRect(k.r, u * 4, u * 4, k.ln);
                k.seven(c, "8", x + cell / 2, y + cell / 2, cell * 0.46f, 0, 0x0F7CF29A, false);
                return;
            case "cozy":   // a recess in the felt
                k.p.setColor(0x40000000);
                c.drawRoundRect(k.r, u * 6, u * 6, k.p);
                k.ln.setStrokeWidth(u);
                k.ln.setColor(0x1FFFE0B0);
                k.r.offset(0, u);
                c.drawRoundRect(k.r, u * 6, u * 6, k.ln);
                return;
            case "minimal":
                k.ln.setStrokeWidth(Math.max(1f, u * 0.75f));
                k.ln.setColor(BoardLook.a(skin().ink, 0x26));
                c.drawRect(k.r, k.ln);
                return;
            case "light":
                k.p.setColor(0xFFEFEBE5);
                c.drawRoundRect(k.r, cell * 0.12f, cell * 0.12f, k.p);
                return;
            default:
                k.p.setColor(0x1FFFFFFF);
                c.drawRoundRect(k.r, cell * 0.12f, cell * 0.12f, k.p);
                k.ln.setStrokeWidth(u);
                k.ln.setColor(0x26FFFFFF);
                c.drawRoundRect(k.r, cell * 0.12f, cell * 0.12f, k.ln);
        }
    }

    private void tile(Canvas c, BoardLook k, String id, float x, float y, float cell, int v) {
        float u = k.u, cx = x + cell / 2, cy = y + cell / 2;
        String s = String.valueOf(v);
        int st = step(v);
        k.reset();
        k.r.set(x, y, x + cell, y + cell);
        switch (id) {
            case "classic": {   // a raised 95 button in the ramp's colour
                int face = skin().tile(v);
                k.button95(c, x, y, x + cell, y + cell, false, face);
                k.text95(c, s, cx, cy, numSize(s, cell) * 0.9f, dark(face) ? BoardLook.W_WHITE : BoardLook.W_DARK, true);
                return;
            }
            case "cs": {
                float cw = cell / 8f, rh = cell / 4f;
                boolean hot = st >= 7;
                GameSkin sk = skin();
                int col = st < 2 ? sk.inkDim : st < 5 ? sk.accent : sk.ink;
                if (hot) {   // inverse video for the big ones
                    k.p.setColor(sk.accent);
                    c.drawRect(x + cw * 0.5f, y + rh * 0.5f, x + cell - cw * 0.5f, y + cell - rh * 0.5f, k.p);
                }
                k.tx.setTextSize(Math.min(cw / 0.6f, rh * 0.9f));
                k.glow(true, col);
                k.glyphs(c, "+------+", x, y + rh * 0.5f, cw);
                k.glyphs(c, "|      |", x, y + rh * 1.5f, cw);
                k.glyphs(c, "|      |", x, y + rh * 2.5f, cw);
                k.glyphs(c, "+------+", x, y + rh * 3.5f, cw);
                k.tx.setTextSize(Math.min(numSize(s, cell) * 0.95f, cw * 6 / (s.length() * 0.6f)));
                if (hot) { k.glow(false, 0); k.tx.setColor(sk.onAccent); } else k.glow(true, col == sk.inkDim ? sk.ink : col);
                k.text(c, s, cx, cy);
                k.glow(false, 0);
                return;
            }
            case "retro": {
                int col = RETRO[st];
                k.chunky(c, x, y, x + cell, y + cell - u * 3, cell * 0.16f, col);
                k.tx.setTypeface(BoardLook.COND);
                k.tx.setTextSize(numSize(s, cell) * 1.08f);
                k.tx.setColor(dark(col) ? BoardLook.R_CREAM : BoardLook.R_BROWN);
                k.text(c, s, cx, cy - u * 2);
                return;
            }
            case "art": {
                int col = ART[st];
                k.p.setShadowLayer(u * 6, 0, u * 3, 0x55000000);
                k.p.setShader(new android.graphics.LinearGradient(x, y, x + cell, y + cell, Game.lighten(col), col, android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(k.r, k.p);
                k.p.clearShadowLayer();
                k.p.setShader(null);
                k.ln.setStrokeWidth(u * 1.5f);
                k.ln.setColor(0xFFB8893A);
                k.r.inset(u * 4, u * 4);
                c.drawRect(k.r, k.ln);
                k.tx.setTypeface(BoardLook.SERIF);
                k.tx.setTextSize(numSize(s, cell) * 1.05f);
                k.tx.setColor(dark(col) ? 0xFFF8F0DC : BoardLook.A_INK);
                k.text(c, s, cx, cy);
                return;
            }
            case "space": {
                int col = SPACE[st];
                k.p.setColor(0xFF0A1210);
                c.drawRoundRect(k.r, u * 4, u * 4, k.p);
                k.p.setShader(new android.graphics.RadialGradient(cx, cy, cell * 0.7f, BoardLook.a(col, 0x30), BoardLook.a(col, 0x08), android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(k.r, u * 4, u * 4, k.p);
                k.p.setShader(null);
                k.ln.setStrokeWidth(u * 1.5f);
                k.ln.setColor(BoardLook.a(col, 0xCC));
                k.ln.setShadowLayer(u * 6, 0, 0, BoardLook.a(col, 0x88));
                c.drawRoundRect(k.r, u * 4, u * 4, k.ln);
                k.ln.clearShadowLayer();
                float hgt = Math.min(cell * 0.46f, cell * 0.72f / (s.length() * 0.56f + (s.length() - 1) * 0.3f + 0.14f));
                k.seven(c, s, cx, cy, hgt, col, BoardLook.a(col, 0x14), true);
                return;
            }
            case "cozy": {
                int col = COZY[st];
                boolean brass = st >= 8;
                k.r.set(x, y + u * 4, x + cell, y + cell + u * 4);
                k.p.setColor(0x80000000);
                c.drawRoundRect(k.r, u * 6, u * 6, k.p);
                k.r.set(x, y, x + cell, y + cell);
                if (brass) {
                    k.p.setShader(new android.graphics.LinearGradient(x, y, x + cell, y + cell,
                            new int[]{0xFFFFF0C0, col, 0xFF8A6020}, null, android.graphics.Shader.TileMode.CLAMP));
                    c.drawRoundRect(k.r, u * 6, u * 6, k.p);
                    k.p.setShader(null);
                } else {
                    k.wood(c, k.r, u * 6, col, 0.7f);
                    k.p.setColor(BoardLook.a(col, 0x99));
                    c.drawRoundRect(k.r, u * 6, u * 6, k.p);
                }
                // firelit edge and a soft top light
                k.p.setShader(new android.graphics.LinearGradient(x, y, x, y + cell, 0x33FFE0B0, 0x33000000, android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(k.r, u * 6, u * 6, k.p);
                k.p.setShader(null);
                k.ln.setStrokeWidth(u);
                k.ln.setColor(0x55FFD8A0);
                c.drawRoundRect(k.r, u * 6, u * 6, k.ln);
                k.tx.setTypeface(BoardLook.SERIF_BOLD);
                k.tx.setTextSize(numSize(s, cell));
                boolean light = !brass && dark(col);
                // burnt-in numerals: a light lip under a dark cut
                k.tx.setColor(0x40FFFFFF);
                k.text(c, s, cx, cy + u);
                k.tx.setColor(light ? 0xFFFFF0D8 : 0xFF3A2010);
                k.text(c, s, cx, cy);
                return;
            }
            case "minimal": {
                int col = skin().tile(v);
                k.p.setColor(col);
                c.drawRect(k.r, k.p);
                k.tx.setTypeface(st >= 3 ? BoardLook.SERIF_BOLD : BoardLook.SERIF);
                k.tx.setTextSize(numSize(s, cell));
                k.tx.setColor(dark(col) ? 0xFFF1EDE4 : 0xFF161616);
                k.text(c, s, cx, cy);
                return;
            }
            case "light": {
                int col = skin().tile(v);
                k.p.setColor(col);
                k.p.setShadowLayer(u * 6, 0, u * 2.5f, 0x2E000000);
                c.drawRoundRect(k.r, cell * 0.12f, cell * 0.12f, k.p);
                k.p.clearShadowLayer();
                k.tx.setTypeface(BoardLook.MEDIUM);
                k.tx.setTextSize(numSize(s, cell));
                k.tx.setColor(v <= 4 ? 0xFF6A6058 : 0xFFFFFFFF);
                k.text(c, s, cx, cy);
                return;
            }
            default: {   // aurora: warm glass tiles that glow
                int col = skin().tile(v);
                k.p.setShadowLayer(u * (st >= 6 ? 18 : 8), 0, 0, BoardLook.a(col, st >= 6 ? 0xAA : 0x55));
                k.p.setColor(BoardLook.a(col, 0xEB));
                c.drawRoundRect(k.r, cell * 0.12f, cell * 0.12f, k.p);
                k.p.clearShadowLayer();
                k.p.setShader(new android.graphics.LinearGradient(0, y, 0, y + cell * 0.5f, 0x40FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(k.r, cell * 0.12f, cell * 0.12f, k.p);
                k.p.setShader(null);
                k.tx.setTypeface(BoardLook.MEDIUM);
                k.tx.setTextSize(numSize(s, cell));
                k.tx.setColor(v <= 4 ? 0xFF6A5A4C : 0xFFFFFFFF);
                k.text(c, s, cx, cy);
            }
        }
    }
}
