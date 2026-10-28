package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 5x5 boxes. With a remote, pick a box, press OK, then press the arrow for the
 * side to draw. Close a box to score it and go again. Most boxes wins.
 * The computer takes free boxes, avoids handing any over, and when it must,
 * gives away the fewest.
 */
public class DotsAndBoxes extends Game {

    private static final int R = 5, C = 5;
    private static final int HN = (R + 1) * C, EDGES = HN + R * (C + 1);
    private final int[] edge = new int[EDGES];   // 0 = not drawn, else the player who drew it
    private final int[] box = new int[R * C];    // owner
    private final Random rnd = new Random();
    private int br = 2, bc = 2, lastEdge = -1;
    private boolean choosing, extra;

    public DotsAndBoxes(Context c) { super(c); }

    @Override public String title() { return "Dots and Boxes"; }

    @Override
    protected void newGame() {
        java.util.Arrays.fill(edge, 0);
        java.util.Arrays.fill(box, 0);
        br = bc = 2;
        lastEdge = -1;
        choosing = false;
        info();
    }

    static int h(int r, int c) { return r * C + c; }
    static int v(int r, int c) { return HN + r * (C + 1) + c; }
    static int[] sides(int r, int c) { return new int[]{h(r, c), h(r + 1, c), v(r, c), v(r, c + 1)}; }

    private static int drawn(int[] e, int r, int c) {
        int n = 0;
        for (int s : sides(r, c)) if (e[s] != 0) n++;
        return n;
    }

    /** Boxes touching an edge (one or two). */
    private static List<int[]> boxesOf(int id) {
        List<int[]> out = new ArrayList<>();
        if (id < HN) {
            int r = id / C, c = id % C;
            if (r > 0) out.add(new int[]{r - 1, c});
            if (r < R) out.add(new int[]{r, c});
        } else {
            int k = id - HN, r = k / (C + 1), c = k % (C + 1);
            if (c > 0) out.add(new int[]{r, c - 1});
            if (c < C) out.add(new int[]{r, c});
        }
        return out;
    }

    private void draw(int id) {
        edge[id] = turn;
        lastEdge = id;
        int closed = 0;
        for (int[] b : boxesOf(id)) {
            if (box[b[0] * C + b[1]] == 0 && drawn(edge, b[0], b[1]) == 4) {
                box[b[0] * C + b[1]] = turn;
                closed++;
            }
        }
        extra = closed > 0;
        choosing = false;
        info();
        endTurn();
    }

    private int score(int p) {
        int n = 0;
        for (int o : box) if (o == p) n++;
        return n;
    }

    private void info() {
        if (host == null) return;
        host.onInfo(choosing ? "Press an arrow to draw that side  ·  Back to cancel"
                : "Boxes   " + score(1) + "  vs  " + score(2));
    }

    @Override protected int nextPlayer() { return extra ? turn : 3 - turn; }

    @Override
    protected int winner() {
        for (int o : box) if (o == 0) return -1;
        int a = score(1), b = score(2);
        return a == b ? 0 : a > b ? 1 : 2;
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean moveCursor(int dx, int dy) {
        if (choosing) {
            int id = dy < 0 ? h(br, bc) : dy > 0 ? h(br + 1, bc) : dx < 0 ? v(br, bc) : v(br, bc + 1);
            if (edge[id] == 0) draw(id);
            return true;
        }
        br = Math.max(0, Math.min(R - 1, br + dy));
        bc = Math.max(0, Math.min(C - 1, bc + dx));
        return true;
    }

    @Override
    protected boolean playAtCursor() {
        if (!choosing && drawn(edge, br, bc) == 4) return false;
        choosing = !choosing;
        info();
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && choosing) {
            choosing = false;
            info();
            invalidate();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    // ------------------------------------------------------------------ computer

    @Override
    protected void cpuMove() {
        List<Integer> free = new ArrayList<>(), safe = new ArrayList<>();
        for (int id = 0; id < EDGES; id++) if (edge[id] == 0) free.add(id);
        boolean takeBoxes = level > 0 || rnd.nextBoolean();
        if (takeBoxes) for (int id : free) {
            for (int[] b : boxesOf(id)) if (drawn(edge, b[0], b[1]) == 3) { draw(id); return; }
        }
        if (level == 0) { draw(free.get(rnd.nextInt(free.size()))); return; }
        for (int id : free) {
            boolean ok = true;
            for (int[] b : boxesOf(id)) if (drawn(edge, b[0], b[1]) == 2) ok = false;
            if (ok) safe.add(id);
        }
        if (!safe.isEmpty()) { draw(safe.get(rnd.nextInt(safe.size()))); return; }
        if (level == 1) { draw(free.get(rnd.nextInt(free.size()))); return; }
        int best = free.get(0), fewest = Integer.MAX_VALUE;
        for (int id : free) {
            int given = giveaway(id);
            if (given < fewest) { fewest = given; best = id; }
        }
        draw(best);
    }

    /** How many boxes the opponent could chain-capture if we drew this edge. */
    private int giveaway(int id) {
        int[] e = edge.clone();
        e[id] = 2;
        int taken = 0;
        boolean again = true;
        while (again) {
            again = false;
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) {
                if (drawn(e, r, c) == 3) {
                    for (int s : sides(r, c)) if (e[s] == 0) e[s] = 1;
                    taken++;
                    again = true;
                }
            }
        }
        return taken;
    }

    // ------------------------------------------------------------------ drawing: one board per look

    private BoardLook look;

    private BoardLook look() {
        GameSkin s = skin();
        if (look == null || look.s != s) look = new BoardLook(this, s);
        return look;
    }

    /** A player's line colour on the board. */
    private int pen(int p) {
        switch (skin().id) {
            case "retro": return p == 1 ? BoardLook.R_ORANGE : BoardLook.R_AVOCADO;
            case "art": return p == 1 ? BoardLook.A_INK : BoardLook.A_OXBLOOD;
            case "space": return p == 1 ? BoardLook.S_GREEN : BoardLook.S_AMBER;
            case "cozy": return p == 1 ? BoardLook.C_EMBER : 0xFF7FA6B0;
            default: return color(p);
        }
    }

    @Override public int uiColor(int p) {
        switch (skin().id) {
            case "art": return p == 1 ? 0xFFF3E7CF : 0xFFD9806E;
            case "retro": return p == 1 ? 0xFFF08A4A : 0xFFB8C46A;
            default: return pen(p);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        BoardLook k = look();
        String id = k.id();
        float[] m = k.margins();
        float w = getWidth(), h = getHeight();
        float cell = Math.min((w - 2 * m[1]) / (C + 0.6f), (h - m[0] - m[2]) / (R + 0.6f)) * 0.95f;
        if (id.equals("classic")) cell = (float) Math.floor(cell / k.px) * k.px;
        float bw = cell * C, bh = cell * R, pad = cell * 0.3f;
        float left = Math.round((w - bw) / 2), top = Math.round((h - bh + m[0] - m[2]) / 2);
        boolean mine = !over && !cpuTurn();
        float pulse = mine ? pulse() : 0;
        k.board(c, left - pad, top - pad, left + bw + pad, top + bh + pad, title());
        if (id.equals("classic")) {   // the white canvas, as in Paint
            k.crisp.setColor(BoardLook.W_WHITE);
            c.drawRect(left - pad, top - pad, left + bw + pad, top + bh + pad, k.crisp);
        }
        if (id.equals("cs")) { drawTerminal(c, k, left, top, cell, mine, pulse); return; }

        // won boxes
        for (int r = 0; r < R; r++) for (int q = 0; q < C; q++) {
            int o = box[r * C + q];
            if (o != 0) drawBox(c, k, id, left + q * cell, top + r * cell, cell, o);
        }
        // the chosen box
        if (mine) {
            float x = left + bc * cell, y = top + br * cell;
            cursorBox(c, k, id, x, y, cell, pulse);
        }
        // lines, and the sides you could draw while choosing
        for (int e = 0; e < EDGES; e++) {
            float[] xy = ends(e, left, top, cell);
            if (edge[e] != 0) drawEdge(c, k, id, xy, edge[e], e == lastEdge, e);
            else if (mine && choosing && isSideOfCursor(e)) {
                int col = id.equals("classic") ? BoardLook.W_SHADOW : BoardLook.a(pen(turn), (int) (90 + 140 * pulse));
                k.dashed(c, xy[0], xy[1], xy[2], xy[3], col, k.u * 3);
            }
        }
        // dots
        for (int r = 0; r <= R; r++) for (int q = 0; q <= C; q++) drawDot(c, k, id, left + q * cell, top + r * cell, cell);
        k.screenGlare(c, left - pad, top - pad, left + bw + pad, top + bh + pad);
        k.reset();
    }

    private float[] ends(int id, float left, float top, float cell) {
        if (id < HN) { int r = id / C, q = id % C; float x = left + q * cell, y = top + r * cell; return new float[]{x, y, x + cell, y}; }
        int t = id - HN, r = t / (C + 1), q = t % (C + 1);
        float x = left + q * cell, y = top + r * cell;
        return new float[]{x, y, x, y + cell};
    }

    private void drawBox(Canvas c, BoardLook k, String id, float x, float y, float cell, int o) {
        float u = k.u, in = cell * 0.1f;
        int col = pen(o);
        k.reset();
        switch (id) {
            case "classic":   // dithered fill in the player's colour, like a 95 paint bucket
                k.crisp.setShader(k.dither(o == 1 ? BoardLook.W_NAVY : 0xFF800000, BoardLook.W_WHITE));
                c.drawRect(x + k.px * 2, y + k.px * 2, x + cell - k.px, y + cell - k.px, k.crisp);
                k.crisp.setShader(null);
                k.crisp.setColor(BoardLook.W_WHITE);
                float s = cell * 0.34f;
                c.drawRect(x + cell / 2 - s / 2, y + cell / 2 - s / 2, x + cell / 2 + s / 2, y + cell / 2 + s / 2, k.crisp);
                k.text95(c, String.valueOf(o), x + cell / 2, y + cell / 2, cell * 0.26f, o == 1 ? BoardLook.W_NAVY : 0xFF800000, true);
                return;
            case "retro":
                k.chunky(c, x + in, y + in, x + cell - in, y + cell - in - u * 3, cell * 0.16f, col);
                k.tx.setTypeface(BoardLook.COND);
                k.tx.setTextSize(cell * 0.4f);
                k.tx.setColor(BoardLook.R_CREAM);
                k.text(c, o == 1 ? "1" : "2", x + cell / 2, y + cell / 2 - u * 1.5f);
                return;
            case "art":
                k.p.setColor(BoardLook.a(col, 0x22));
                c.drawRect(x + in * 0.6f, y + in * 0.6f, x + cell - in * 0.6f, y + cell - in * 0.6f, k.p);
                k.tx.setTypeface(BoardLook.SERIF_ITALIC);
                k.tx.setTextSize(cell * 0.46f);
                k.tx.setColor(col);
                k.text(c, o == 1 ? "I" : "II", x + cell / 2, y + cell / 2);
                return;
            case "space": {
                k.p.setColor(BoardLook.a(col, 0x26));
                c.drawRect(x + in, y + in, x + cell - in, y + cell - in, k.p);
                k.ln.setStrokeWidth(Math.max(1f, u));
                k.ln.setColor(BoardLook.a(col, 0x55));
                c.save();
                c.clipRect(x + in, y + in, x + cell - in, y + cell - in);
                for (float d = -cell; d < cell; d += u * 7) c.drawLine(x + d, y + cell, x + d + cell, y, k.ln);
                c.restore();
                k.tx.setTypeface(BoardLook.MONO_BOLD);
                k.tx.setTextSize(cell * 0.26f);
                k.tx.setColor(col);
                k.tx.setShadowLayer(u * 5, 0, 0, BoardLook.a(col, 0xAA));
                k.text(c, o == 1 ? "P1" : "P2", x + cell / 2, y + cell / 2);
                return;
            }
            case "cozy":
                disc(c, x + cell / 2, y + cell / 2, cell * 0.3f, o, 255);
                return;
            case "minimal":
                k.p.setColor(o == 1 ? BoardLook.a(skin().ink, 0x14) : BoardLook.a(skin().p2, 0x1F));
                c.drawRect(x, y, x + cell, y + cell, k.p);
                k.tx.setTypeface(BoardLook.SERIF_ITALIC);
                k.tx.setTextSize(cell * 0.36f);
                k.tx.setColor(col);
                k.text(c, String.valueOf(o), x + cell / 2, y + cell / 2);
                return;
            case "light":
                k.r.set(x + in, y + in, x + cell - in, y + cell - in);
                k.p.setColor(BoardLook.a(col, 0x2E));
                c.drawRoundRect(k.r, u * 10, u * 10, k.p);
                k.tx.setTypeface(BoardLook.MEDIUM);
                k.tx.setTextSize(cell * 0.36f);
                k.tx.setColor(col);
                k.text(c, String.valueOf(o), x + cell / 2, y + cell / 2);
                return;
            default:
                k.r.set(x + in, y + in, x + cell - in, y + cell - in);
                k.p.setShader(new android.graphics.RadialGradient(x + cell / 2, y + cell / 2, cell * 0.6f,
                        BoardLook.a(col, 0x66), BoardLook.a(col, 0x1A), android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(k.r, u * 12, u * 12, k.p);
                k.p.setShader(null);
                k.tx.setTypeface(BoardLook.SANS_LIGHT);
                k.tx.setTextSize(cell * 0.4f);
                k.tx.setColor(0xF2FFFFFF);
                k.text(c, String.valueOf(o), x + cell / 2, y + cell / 2);
        }
    }

    private void cursorBox(Canvas c, BoardLook k, String id, float x, float y, float cell, float p) {
        float u = k.u, in = cell * 0.14f;
        float l = x + in, t = y + in, r = x + cell - in, b = y + cell - in;
        k.reset();
        switch (id) {
            case "classic":
                k.focus95(c, l, t, r, b, BoardLook.W_DARK);
                return;
            case "space":
                k.brackets(c, l, t, r, b, BoardLook.a(BoardLook.S_AMBER, (int) (170 + 85 * p)), cell * 0.16f);
                return;
            case "minimal":
                k.ln.setStrokeWidth(Math.max(1f, u * 1.2f));
                k.ln.setColor(BoardLook.a(skin().ink, (int) (110 + 120 * p)));
                c.drawLine(l + cell * 0.12f, b, r - cell * 0.12f, b, k.ln);
                return;
            case "art": {
                float cx = x + cell / 2, cy = y + cell / 2;
                k.p.setShader(new android.graphics.RadialGradient(cx, cy, cell * 0.55f,
                        BoardLook.a(0xFFE2B85A, (int) (80 + 60 * p)), 0x00E2B85A, android.graphics.Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, cell * 0.55f, k.p);
                k.p.setShader(null);
                return;
            }
            default: {
                int col = id.equals("light") ? 0xFF2F6DB8 : id.equals("retro") ? 0xFFFFB347 : id.equals("cozy") ? 0xFFFFB04A : 0xFFFFFFFF;
                k.r.set(l, t, r, b);
                k.p.setColor(BoardLook.a(col, (int) (35 + 35 * p)));
                c.drawRoundRect(k.r, u * 10, u * 10, k.p);
                k.ln.setStrokeWidth(u * 2.5f);
                k.ln.setColor(BoardLook.a(col, (int) (140 + 110 * p)));
                if (!id.equals("light")) k.ln.setShadowLayer(u * 8, 0, 0, BoardLook.a(col, 0x99));
                c.drawRoundRect(k.r, u * 10, u * 10, k.ln);
                k.ln.clearShadowLayer();
            }
        }
    }

    private void drawEdge(Canvas c, BoardLook k, String id, float[] e, int who, boolean last, int seed) {
        float u = k.u;
        int col = pen(who);
        k.reset();
        switch (id) {
            case "classic": {
                float t = k.px * (last ? 3 : 2);
                k.crisp.setColor(who == 1 ? BoardLook.W_NAVY : 0xFF800000);
                c.drawRect(Math.min(e[0], e[2]) - t / 2, Math.min(e[1], e[3]) - t / 2, Math.max(e[0], e[2]) + t / 2, Math.max(e[1], e[3]) + t / 2, k.crisp);
                return;
            }
            case "art":
                k.p.setColor(col);
                c.drawPath(k.taper(e[0], e[1], e[2], e[3], u * (last ? 6 : 4.5f)), k.p);
                return;
            case "space": {
                Path q = new Path(); q.moveTo(e[0], e[1]); q.lineTo(e[2], e[3]);
                k.holo(c, q, col, u * (last ? 3 : 2.2f));
                return;
            }
            case "minimal":
                k.ln.setStrokeWidth(u * (last ? 2.2f : 1.5f));
                k.ln.setStrokeCap(android.graphics.Paint.Cap.BUTT);
                k.ln.setColor(col);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                return;
            case "retro": case "cozy": {
                // a painted wooden stick between the pegs
                float wdt = u * (id.equals("retro") ? 9 : 7);
                k.ln.setStrokeWidth(wdt);
                k.ln.setColor(0x66000000);
                c.drawLine(e[0] + u, e[1] + u * 3, e[2] + u, e[3] + u * 3, k.ln);
                boolean hz = e[1] == e[3];
                k.ln.setShader(new android.graphics.LinearGradient(e[0] - (hz ? 0 : wdt / 2), e[1] - (hz ? wdt / 2 : 0),
                        e[0] + (hz ? 0 : wdt / 2), e[1] + (hz ? wdt / 2 : 0), Game.lighten(col), Game.shade(col), android.graphics.Shader.TileMode.CLAMP));
                if (last && id.equals("cozy")) k.ln.setShadowLayer(u * 10, 0, 0, 0x99FFA040);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                k.ln.setShader(null);
                k.ln.clearShadowLayer();
                return;
            }
            case "light":
                k.ln.setStrokeWidth(u * (last ? 7 : 6));
                k.ln.setColor(col);
                k.ln.setShadowLayer(u * 5, 0, u * 2, BoardLook.a(col, 0x55));
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                k.ln.clearShadowLayer();
                return;
            default:
                k.ln.setStrokeWidth(u * (last ? 7 : 5.5f));
                k.ln.setColor(col);
                k.ln.setShadowLayer(u * (last ? 14 : 9), 0, 0, BoardLook.a(col, 0xCC));
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                k.ln.clearShadowLayer();
        }
    }

    private void drawDot(Canvas c, BoardLook k, String id, float x, float y, float cell) {
        float u = k.u;
        switch (id) {
            case "classic":
                k.crisp.setColor(BoardLook.W_DARK);
                c.drawRect(x - k.px * 2, y - k.px * 2, x + k.px * 2, y + k.px * 2, k.crisp);
                return;
            case "retro": case "cozy":
                k.brass(c, x, y, u * (id.equals("retro") ? 7 : 6));
                return;
            case "art":
                k.p.setColor(BoardLook.A_INK);
                c.drawCircle(x, y, u * 3.6f, k.p);
                return;
            case "space":
                k.led(c, x, y, u * 2.6f, 0xFFB8F5C8);
                return;
            case "minimal":
                k.p.setColor(skin().ink);
                c.drawCircle(x, y, u * 2.4f, k.p);
                return;
            case "light":
                k.p.setColor(0xFF3A3A40);
                k.p.setShadowLayer(u * 3, 0, u, 0x33000000);
                c.drawCircle(x, y, u * 4.5f, k.p);
                k.p.clearShadowLayer();
                return;
            default:
                k.p.setShader(new android.graphics.RadialGradient(x, y, u * 12, 0x66FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
                c.drawCircle(x, y, u * 12, k.p);
                k.p.setShader(null);
                k.p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, u * 4.5f, k.p);
        }
    }

    // --- CS: dots are '+', lines are '-' and '|', the cursor box is inverse video

    private void drawTerminal(Canvas c, BoardLook k, float left, float top, float cell, boolean mine, float pulse) {
        GameSkin s = skin();
        int per = 6;   // characters per box side
        float cw = cell / per;
        k.tx.setTypeface(BoardLook.MONO_BOLD);
        k.tx.setTextSize(cw / 0.6f);
        if (mine) {
            k.p.setColor(BoardLook.a(s.accent, (int) (150 + 90 * pulse)));
            float x = left + bc * cell, y = top + br * cell;
            c.drawRect(x + cw * 0.6f, y + cw * 0.6f, x + cell - cw * 0.6f, y + cell - cw * 0.6f, k.p);
        }
        for (int r = 0; r < R; r++) for (int q = 0; q < C; q++) {
            int o = box[r * C + q];
            if (o == 0) continue;
            boolean inv = mine && r == br && q == bc;
            int col = inv ? s.onAccent : o == 1 ? s.p1 : s.p2;
            if (inv) k.glow(false, 0); else k.glow(true, col);
            k.tx.setColor(col);
            float x = left + q * cell, y = top + r * cell;
            k.tx.setTextSize(cell * 0.42f);
            k.text(c, o == 1 ? "1" : "2", x + cell / 2, y + cell / 2);
            k.tx.setTextSize(cw / 0.6f);
            int dim = BoardLook.a(col, inv ? 0xCC : 0x55);
            k.tx.setColor(dim);
            k.tx.clearShadowLayer();
            for (int i = 1; i < per; i += 2) for (int j = 1; j < per; j += 2) {   // a '.' stipple across the box
                if (i == 3 && j == 3) continue;
                if (Math.abs(i - 3) <= 1 && Math.abs(j - 3) <= 1) continue;
                k.text(c, ":", x + i * cw + cw / 2, y + j * cw + cw / 2);
            }
        }
        for (int e = 0; e < EDGES; e++) {
            float[] xy = ends(e, left, top, cell);
            boolean hz = xy[1] == xy[3];
            String ch = hz ? "-" : "|";
            int col;
            if (edge[e] != 0) col = edge[e] == 1 ? s.p1 : s.p2;
            else if (mine && choosing && isSideOfCursor(e)) col = BoardLook.a(s.ink, (int) (80 + 150 * pulse));
            else continue;
            k.glow(true, col);
            if (e == lastEdge) k.tx.setColor(0xFFFFFFFF);
            for (int i = 1; i < per; i++) {
                float gx = hz ? xy[0] + i * cw : xy[0], gy = hz ? xy[1] : xy[1] + i * cw;
                k.text(c, ch, gx, gy);
            }
        }
        k.glow(true, s.inkDim);
        for (int r = 0; r <= R; r++) for (int q = 0; q <= C; q++) k.text(c, "+", left + q * cell, top + r * cell);
        k.glow(false, 0);
        k.reset();
        k.scanlines(c, left - cell * 0.3f - k.u * 14, top - cell * 0.3f - k.u * 30, left + cell * C + cell * 0.3f + k.u * 14, top + cell * R + cell * 0.3f + k.u * 14);
    }

    private boolean isSideOfCursor(int id) {
        for (int s : sides(br, bc)) if (s == id) return true;
        return false;
    }
}
