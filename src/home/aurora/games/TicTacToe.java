package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 3x3. The computer plays minimax but makes random moves, more often on easier levels. */
public class TicTacToe extends Game {

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
    private final int[] b = new int[9];
    private final Random rnd = new Random();
    private int cur = 4;
    private int[] winLine;

    public TicTacToe(Context c) { super(c); }

    @Override public String title() { return "Tic-Tac-Toe"; }

    @Override
    protected void newGame() {
        for (int i = 0; i < 9; i++) b[i] = 0;
        cur = 4;
        winLine = null;
    }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        int x = Math.max(0, Math.min(2, cur % 3 + dx)), y = Math.max(0, Math.min(2, cur / 3 + dy));
        cur = y * 3 + x;
        return true;
    }

    @Override
    protected boolean playAtCursor() {
        if (b[cur] != 0) return false;
        b[cur] = turn;
        endTurn();
        return true;
    }

    @Override
    protected void cpuMove() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (b[i] == 0) free.add(i);
        int pick;
        int randomChance = level == 0 ? 55 : level == 1 ? 20 : 0;
        if (rnd.nextInt(100) < randomChance) {
            pick = free.get(rnd.nextInt(free.size()));
        } else {
            int best = Integer.MIN_VALUE;
            pick = free.get(0);
            for (int i : free) {
                b[i] = 2;
                int s = minimax(1, 0);
                b[i] = 0;
                if (s > best) { best = s; pick = i; }
            }
        }
        b[pick] = 2;
        cur = pick;
        endTurn();
    }

    private int minimax(int player, int depth) {
        int w = check();
        if (w == 2) return 10 - depth;
        if (w == 1) return depth - 10;
        if (w == 0) return 0;
        int best = player == 2 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (b[i] != 0) continue;
            b[i] = player;
            int s = minimax(3 - player, depth + 1);
            b[i] = 0;
            best = player == 2 ? Math.max(best, s) : Math.min(best, s);
        }
        return best;
    }

    private int check() {
        for (int[] l : LINES) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        for (int v : b) if (v == 0) return -1;
        return 0;
    }

    @Override
    protected int winner() {
        int w = check();
        if (w > 0) for (int[] l : LINES) if (b[l[0]] == w && b[l[1]] == w && b[l[2]] == w) winLine = l;
        return w;
    }

    // ------------------------------------------------------------------ drawing: one board per look

    private BoardLook look;

    private BoardLook look() {
        GameSkin s = skin();
        if (look == null || look.s != s) look = new BoardLook(this, s);
        return look;
    }

    private static final String[] PX_X = {
            "##.......##", "###.....###", ".###...###.", "..###.###..", "...#####...", "....###....",
            "...#####...", "..###.###..", ".###...###.", "###.....###", "##.......##"};
    private static final String[] PX_O = {
            "...#####...", ".#########.", ".###...###.", "###.....###", "##.......##", "##.......##",
            "##.......##", "###.....###", ".###...###.", ".#########.", "...#####..."};

    /** The colour of X (1) and O (2) on the board. */
    private int mark(int p) {
        switch (skin().id) {
            case "retro": return p == 1 ? BoardLook.R_ORANGE : BoardLook.R_AVOCADO;
            case "art": return p == 1 ? BoardLook.A_INK : BoardLook.A_OXBLOOD;
            case "space": return p == 1 ? BoardLook.S_GREEN : BoardLook.S_AMBER;
            case "cozy": return p == 1 ? BoardLook.C_IVORY : BoardLook.C_BRASS;
            default: return color(p);
        }
    }

    /** Side-panel colours: readable on the look's surface. */
    @Override public int uiColor(int p) {
        switch (skin().id) {
            case "art": return p == 1 ? 0xFFF3E7CF : 0xFFD9806E;
            case "retro": return p == 1 ? 0xFFF08A4A : 0xFFB8C46A;
            default: return mark(p);
        }
    }

    @Override public String sideName(int p) { return vsCpu ? null : (p == 1 ? "Player X" : "Player O"); }

    @Override
    protected void onDraw(Canvas c) {
        BoardLook k = look();
        String id = k.id();
        float[] m = k.margins();
        float w = getWidth(), h = getHeight();
        float side = Math.min(w - 2 * m[1], h - m[0] - m[2]) * 0.9f;
        float left = (w - side) / 2, top = (h - side + m[0] - m[2]) / 2, cell = side / 3;
        if (id.equals("classic")) {   // whole 95 pixels
            cell = (float) Math.floor(cell / k.px) * k.px;
            side = cell * 3;
            left = Math.round(left / k.px) * k.px;
            top = Math.round(top / k.px) * k.px;
        }
        k.board(c, left, top, left + side, top + side, title());
        boolean showCursor = !over && !cpuTurn();
        float pulse = showCursor ? pulse() : 0;

        switch (id) {
            case "classic": drawClassic(c, k, left, top, cell, showCursor); break;
            case "cs": drawTerminal(c, k, left, top, side, cell, showCursor, pulse); break;
            default: drawSmooth(c, k, id, left, top, side, cell, showCursor, pulse); break;
        }
        k.screenGlare(c, left, top, left + side, top + side);
    }

    // --- Windows 95: nine buttons that sink when played

    private void drawClassic(Canvas c, BoardLook k, float left, float top, float cell, boolean cursor) {
        float px = k.px;
        for (int i = 0; i < 9; i++) {
            float x = left + cell * (i % 3), y = top + cell * (i / 3);
            boolean win = winLine != null && (winLine[0] == i || winLine[1] == i || winLine[2] == i);
            if (b[i] == 0) {
                k.button95(c, x, y, x + cell, y + cell, false, BoardLook.W_FACE);
            } else {
                k.crisp.setColor(win ? BoardLook.W_NAVY : BoardLook.W_WHITE);
                c.drawRect(x, y, x + cell, y + cell, k.crisp);
                k.edge(c, x, y, x + cell, y + cell, BoardLook.W_SHADOW, BoardLook.W_SHADOW);
                float cp = Math.max(px, (float) Math.floor(cell * 0.56f / 11f / px) * px);
                int col = win ? BoardLook.W_WHITE : (b[i] == 1 ? BoardLook.W_NAVY : 0xFF800000);
                k.pixels(c, b[i] == 1 ? PX_X : PX_O, x + cell / 2, y + cell / 2, cp, col);
            }
            if (cursor && i == cur) {
                float in = px * 5;
                k.focus95(c, x + in, y + in, x + cell - in, y + cell - in, BoardLook.W_DARK);
                k.edge(c, x, y, x + cell, y + cell, BoardLook.W_DARK, BoardLook.W_DARK);
            }
        }
    }

    // --- CS: an ASCII board, the cursor in inverse video

    private void drawTerminal(Canvas c, BoardLook k, float left, float top, float side, float cell, boolean cursor, float pulse) {
        GameSkin s = skin();
        int cols = 25, rows = 13;
        float cw = side / cols, rh = side / rows;
        k.tx.setTypeface(BoardLook.MONO);
        k.tx.setTextSize(Math.min(cw / 0.6f, rh * 0.95f));
        // inverse-video cursor under the text
        if (cursor) {
            float x = left + (cur % 3) * 8 * cw + cw, y = top + (cur / 3) * 4 * rh + rh;
            k.p.setColor(BoardLook.a(s.accent, (int) (170 + 85 * pulse)));
            c.drawRect(x + cw * 0.15f, y + rh * 0.1f, x + 7 * cw - cw * 0.15f, y + 3 * rh - rh * 0.1f, k.p);
        }
        k.glow(true, s.inkDim);
        for (int r = 0; r < rows; r++) {
            String line = r % 4 == 0 ? "+-------+-------+-------+" : "|       |       |       |";
            k.glyphs(c, line, left, top + rh * (r + 0.5f), cw);
        }
        k.tx.setTypeface(BoardLook.MONO_BOLD);
        k.tx.setTextSize(rh * 2.3f);
        for (int i = 0; i < 9; i++) {
            if (b[i] == 0) continue;
            float cx = left + (i % 3) * 8 * cw + 4.5f * cw, cy = top + (i / 3) * 4 * rh + 2.5f * rh;
            boolean win = winLine != null && (winLine[0] == i || winLine[1] == i || winLine[2] == i);
            boolean inv = cursor && i == cur;
            int col = inv ? s.onAccent : win ? 0xFFFFFFFF : (b[i] == 1 ? s.p1 : s.p2);
            if (inv) k.glow(false, 0); else k.glow(true, col);
            k.tx.setColor(col);
            k.text(c, b[i] == 1 ? "X" : "O", cx, cy);
        }
        k.glow(false, 0);
        if (winLine != null) {
            k.tx.setTypeface(BoardLook.MONO);
            k.tx.setTextSize(Math.min(cw / 0.6f, rh * 0.95f));
            k.glow(true, s.ink);
            k.tx.setTextAlign(android.graphics.Paint.Align.LEFT);
            c.drawText("> " + (b[winLine[0]] == 1 ? "X" : "O") + " wins_", left, top + side + rh * 1.2f, k.tx);
            k.glow(false, 0);
        }
        k.reset();
        k.scanlines(c, left - k.u * 14, top - k.u * 30, left + side + k.u * 14, top + side + k.u * 14);
    }

    // --- every other look: drawn grid and marks in the look's material

    private void drawSmooth(Canvas c, BoardLook k, String id, float left, float top, float side, float cell, boolean cursor, float pulse) {
        float u = k.u;
        // the cursor goes under the grid and marks
        if (cursor) {
            float x = left + cell * (cur % 3), y = top + cell * (cur / 3), in = cell * 0.08f;
            cursorCell(c, k, id, x + in, y + in, x + cell - in, y + cell - in, pulse);
        }
        // the grid
        for (int i = 1; i < 3; i++) {
            float e = cell * 0.06f;
            gridLine(c, k, id, left + cell * i, top + e, left + cell * i, top + side - e, i);
            gridLine(c, k, id, left + e, top + cell * i, left + side - e, top + cell * i, i + 2);
        }
        if (id.equals("space")) {   // flight-software cell labels
            k.tx.setTypeface(BoardLook.MONO);
            k.tx.setTextSize(u * 9);
            k.tx.setColor(0x667CF29A);
            k.tx.setTextAlign(android.graphics.Paint.Align.LEFT);
            for (int i = 0; i < 9; i++)
                c.drawText((char) ('A' + i % 3) + "" + (i / 3 + 1), left + cell * (i % 3) + u * 8, top + cell * (i / 3) + u * 15, k.tx);
            k.tx.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
        for (int i = 0; i < 9; i++) {
            if (b[i] == 0) continue;
            float cx = left + cell * (i % 3 + 0.5f), cy = top + cell * (i / 3 + 0.5f), r = cell * 0.28f;
            drawMark(c, k, id, cx, cy, r, b[i], i);
        }
        if (winLine != null) {
            int a = winLine[0], z = winLine[2], who = b[a];
            float ax = left + cell * (a % 3 + 0.5f), ay = top + cell * (a / 3 + 0.5f);
            float zx = left + cell * (z % 3 + 0.5f), zy = top + cell * (z / 3 + 0.5f);
            float ex = (zx - ax) * 0.2f, ey = (zy - ay) * 0.2f;
            ax -= ex; ay -= ey; zx += ex; zy += ey;
            k.reset();
            switch (id) {
                case "art":
                    k.p.setColor(BoardLook.A_OXBLOOD);
                    c.drawPath(k.taper(ax, ay, zx, zy, u * 9), k.p);
                    break;
                case "space": {
                    Path pth = new Path(); pth.moveTo(ax, ay); pth.lineTo(zx, zy);
                    k.holo(c, pth, BoardLook.S_AMBER, u * 3);
                    break;
                }
                case "minimal":
                    k.ln.setStrokeWidth(u * 1.5f);
                    k.ln.setColor(skin().p2);
                    c.drawLine(ax, ay, zx, zy, k.ln);
                    break;
                case "retro": case "cozy": {
                    int col = id.equals("retro") ? BoardLook.R_MUSTARD : 0xFFFFC870;
                    k.ln.setStrokeWidth(u * 10);
                    k.ln.setColor(0x66000000);
                    c.drawLine(ax, ay + u * 3, zx, zy + u * 3, k.ln);
                    k.ln.setColor(col);
                    if (id.equals("cozy")) k.ln.setShadowLayer(u * 14, 0, 0, 0xAAFFA040);
                    c.drawLine(ax, ay, zx, zy, k.ln);
                    k.ln.clearShadowLayer();
                    k.ln.setStrokeWidth(u * 3);
                    k.ln.setColor(0x66FFFFFF);
                    c.drawLine(ax, ay - u * 2, zx, zy - u * 2, k.ln);
                    break;
                }
                default: {
                    int col = mark(who);
                    k.ln.setStrokeWidth(u * 7);
                    if (id.equals("aurora")) k.ln.setShadowLayer(u * 12, 0, 0, BoardLook.a(col, 0xCC));
                    else k.ln.setShadowLayer(u * 6, 0, u * 2, 0x33000000);
                    k.ln.setColor(id.equals("aurora") ? 0xFFFFFFFF : col);
                    c.drawLine(ax, ay, zx, zy, k.ln);
                    k.ln.clearShadowLayer();
                }
            }
        }
        k.reset();
    }

    private void cursorCell(Canvas c, BoardLook k, String id, float l, float t, float r, float b, float p) {
        float u = k.u;
        k.reset();
        switch (id) {
            case "space":
                k.brackets(c, l, t, r, b, BoardLook.a(BoardLook.S_AMBER, (int) (170 + 85 * p)), (r - l) * 0.22f);
                return;
            case "minimal": {
                k.ln.setStrokeCap(android.graphics.Paint.Cap.SQUARE);
                k.ln.setStrokeWidth(Math.max(1f, u * 1.2f));
                k.ln.setColor(BoardLook.a(skin().ink, (int) (120 + 135 * p)));
                float len = (r - l) * 0.12f;
                Path q = new Path();
                q.moveTo(l, t + len); q.lineTo(l, t); q.lineTo(l + len, t);
                q.moveTo(r - len, t); q.lineTo(r, t); q.lineTo(r, t + len);
                q.moveTo(r, b - len); q.lineTo(r, b); q.lineTo(r - len, b);
                q.moveTo(l + len, b); q.lineTo(l, b); q.lineTo(l, b - len);
                c.drawPath(q, k.ln);
                return;
            }
            case "art": {
                float cx = (l + r) / 2, cy = (t + b) / 2;
                k.p.setShader(new android.graphics.RadialGradient(cx, cy, (r - l) * 0.7f,
                        BoardLook.a(0xFFE2B85A, (int) (70 + 50 * p)), 0x00E2B85A, android.graphics.Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, (r - l) * 0.7f, k.p);
                k.p.setShader(null);
                k.ln.setStrokeWidth(u * 1.5f);
                k.ln.setColor(BoardLook.a(0xFF9A7028, (int) (140 + 100 * p)));
                k.r.set(l, t, r, b);
                c.drawRect(k.r, k.ln);
                return;
            }
            case "retro": case "cozy": {
                int col = id.equals("retro") ? 0xFFFFB347 : 0xFFFFB04A;
                k.r.set(l, t, r, b);
                k.p.setColor(BoardLook.a(col, (int) (50 + 40 * p)));
                c.drawRoundRect(k.r, u * 12, u * 12, k.p);
                k.ln.setStrokeWidth(u * 4);
                k.ln.setColor(BoardLook.a(col, (int) (170 + 85 * p)));
                k.ln.setShadowLayer(u * 10, 0, 0, BoardLook.a(col, 0xAA));
                c.drawRoundRect(k.r, u * 12, u * 12, k.ln);
                k.ln.clearShadowLayer();
                return;
            }
            case "light": {
                k.r.set(l, t, r, b);
                k.p.setColor(BoardLook.a(0xFF2F6DB8, (int) (22 + 22 * p)));
                c.drawRoundRect(k.r, u * 16, u * 16, k.p);
                k.ln.setStrokeWidth(u * 2);
                k.ln.setColor(BoardLook.a(0xFF2F6DB8, (int) (110 + 90 * p)));
                c.drawRoundRect(k.r, u * 16, u * 16, k.ln);
                return;
            }
            default: {
                k.r.set(l, t, r, b);
                k.p.setColor(BoardLook.a(0xFFFFFFFF, (int) (30 + 30 * p)));
                c.drawRoundRect(k.r, u * 18, u * 18, k.p);
                k.ln.setStrokeWidth(u * 2);
                k.ln.setColor(BoardLook.a(0xFFFFFFFF, (int) (140 + 100 * p)));
                k.ln.setShadowLayer(u * 10, 0, 0, 0x99FFFFFF);
                c.drawRoundRect(k.r, u * 18, u * 18, k.ln);
                k.ln.clearShadowLayer();
            }
        }
    }

    private void gridLine(Canvas c, BoardLook k, String id, float x1, float y1, float x2, float y2, int seed) {
        float u = k.u;
        k.reset();
        switch (id) {
            case "art":
                k.p.setColor(0xE63A2A1E);
                c.drawPath(k.taper(x1, y1, x2, y2, u * 4.5f), k.p);
                break;
            case "space": {
                Path q = new Path(); q.moveTo(x1, y1); q.lineTo(x2, y2);
                k.holo(c, q, 0xB37CF29A, u * 1.6f);
                break;
            }
            case "minimal":
                k.ln.setStrokeWidth(Math.max(1f, u * 0.8f));
                k.ln.setColor(skin().ink);
                c.drawLine(x1, y1, x2, y2, k.ln);
                break;
            case "retro":   // chunky walnut rails set into the enamel
                k.ln.setStrokeWidth(u * 11);
                k.ln.setColor(0x33000000);
                c.drawLine(x1, y1 + u * 2, x2, y2 + u * 2, k.ln);
                k.ln.setColor(0xFF7A4422);
                c.drawLine(x1, y1, x2, y2, k.ln);
                k.ln.setStrokeWidth(u * 3);
                k.ln.setColor(0x40FFE0B0);
                c.drawLine(x1 - (y1 == y2 ? 0 : u * 2), y1 - (y1 == y2 ? u * 2 : 0), x2 - (y1 == y2 ? 0 : u * 2), y2 - (y1 == y2 ? u * 2 : 0), k.ln);
                break;
            case "cozy":    // brass inlay in the felt
                k.ln.setStrokeWidth(u * 5);
                k.ln.setColor(0x80000000);
                c.drawLine(x1, y1 + u * 1.5f, x2, y2 + u * 1.5f, k.ln);
                k.ln.setStrokeWidth(u * 3.5f);
                k.ln.setShader(new android.graphics.LinearGradient(x1, y1, x2 + u * 4, y2 + u * 4, 0xFFF2D08A, 0xFF9A7028, android.graphics.Shader.TileMode.MIRROR));
                c.drawLine(x1, y1, x2, y2, k.ln);
                k.ln.setShader(null);
                break;
            case "light":
                k.ln.setStrokeWidth(u * 3);
                k.ln.setColor(0x1F1C1C1E);
                c.drawLine(x1, y1, x2, y2, k.ln);
                break;
            default:
                k.ln.setStrokeWidth(u * 3);
                k.ln.setColor(0x8CFFFFFF);
                k.ln.setShadowLayer(u * 8, 0, 0, 0x66FFFFFF);
                c.drawLine(x1, y1, x2, y2, k.ln);
                k.ln.clearShadowLayer();
        }
    }

    private void drawMark(Canvas c, BoardLook k, String id, float cx, float cy, float r, int who, int seed) {
        float u = k.u;
        boolean x = who == 1;
        int col = mark(who);
        k.reset();
        switch (id) {
            case "art":
                k.p.setColor(col);
                if (x) {
                    c.drawPath(k.taper(cx - r, cy - r, cx + r, cy + r, r * 0.26f), k.p);
                    c.drawPath(k.taper(cx + r * 0.9f, cy - r, cx - r * 0.9f, cy + r, r * 0.14f), k.p);
                } else {
                    c.drawPath(k.nibRing(cx, cy, r, r * 0.26f), k.p);
                }
                return;
            case "space": {
                Path q = new Path();
                if (x) { q.moveTo(cx - r, cy - r); q.lineTo(cx + r, cy + r); q.moveTo(cx + r, cy - r); q.lineTo(cx - r, cy + r); }
                else q.addCircle(cx, cy, r, Path.Direction.CW);
                k.holo(c, q, col, u * 3);
                // a fine inner trace, like a hologram's second plane
                Path q2 = new Path();
                if (x) { q2.addRect(cx - r * 0.22f, cy - r * 0.22f, cx + r * 0.22f, cy + r * 0.22f, Path.Direction.CW); }
                else q2.addCircle(cx, cy, r * 0.62f, Path.Direction.CW);
                k.ln.setStrokeWidth(Math.max(1f, u));
                k.ln.setColor(BoardLook.a(col, 0x88));
                k.ln.setPathEffect(new android.graphics.DashPathEffect(new float[]{u * 4, u * 3}, 0));
                c.drawPath(q2, k.ln);
                k.ln.setPathEffect(null);
                return;
            }
            case "minimal":
                k.ln.setStrokeWidth(u * 1.6f);
                k.ln.setStrokeCap(android.graphics.Paint.Cap.BUTT);
                k.ln.setColor(col);
                if (x) { c.drawLine(cx - r, cy - r, cx + r, cy + r, k.ln); c.drawLine(cx + r, cy - r, cx - r, cy + r, k.ln); }
                else c.drawCircle(cx, cy, r, k.ln);
                return;
            case "retro": case "cozy": {
                // a chunky playing piece: shadow, body, then a highlight
                float w = r * (id.equals("retro") ? 0.42f : 0.36f);
                Path q = new Path();
                if (x) { q.moveTo(cx - r, cy - r); q.lineTo(cx + r, cy + r); q.moveTo(cx + r, cy - r); q.lineTo(cx - r, cy + r); }
                else q.addCircle(cx, cy, r * 0.92f, Path.Direction.CW);
                k.ln.setStrokeWidth(w);
                k.ln.setColor(0x73000000);
                c.save(); c.translate(u * 2, u * 5); c.drawPath(q, k.ln); c.restore();
                if (id.equals("cozy") && !x) {
                    k.ln.setShader(new android.graphics.LinearGradient(cx - r, cy - r, cx + r, cy + r,
                            new int[]{0xFFFFF0C0, BoardLook.C_BRASS, 0xFF7A5418}, null, android.graphics.Shader.TileMode.CLAMP));
                } else {
                    k.ln.setShader(new android.graphics.LinearGradient(cx - r, cy - r, cx + r, cy + r,
                            Game.lighten(col), Game.shade(col), android.graphics.Shader.TileMode.CLAMP));
                }
                c.drawPath(q, k.ln);
                k.ln.setShader(null);
                k.ln.setStrokeWidth(w * 0.22f);
                k.ln.setColor(0x80FFFFFF);
                c.save(); c.translate(-w * 0.18f, -w * 0.22f);
                if (x) { Path hl = new Path(); hl.moveTo(cx - r * 0.75f, cy - r * 0.75f); hl.lineTo(cx - r * 0.2f, cy - r * 0.2f);
                    hl.moveTo(cx + r * 0.75f, cy - r * 0.75f); hl.lineTo(cx + r * 0.2f, cy - r * 0.2f); c.drawPath(hl, k.ln); }
                else { k.r.set(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f); c.drawArc(k.r, 200, 70, false, k.ln); }
                c.restore();
                return;
            }
            case "light":
                k.ln.setStrokeWidth(u * 9);
                k.ln.setColor(col);
                k.ln.setShadowLayer(u * 8, 0, u * 3, BoardLook.a(col, 0x55));
                if (x) { c.drawLine(cx - r, cy - r, cx + r, cy + r, k.ln); c.drawLine(cx + r, cy - r, cx - r, cy + r, k.ln); }
                else c.drawCircle(cx, cy, r, k.ln);
                k.ln.clearShadowLayer();
                return;
            default:   // aurora: soft luminous strokes
                k.ln.setStrokeWidth(u * 8);
                k.ln.setColor(col);
                k.ln.setShadowLayer(u * 14, 0, 0, BoardLook.a(col, 0xCC));
                if (x) { c.drawLine(cx - r, cy - r, cx + r, cy + r, k.ln); c.drawLine(cx + r, cy - r, cx - r, cy + r, k.ln); }
                else c.drawCircle(cx, cy, r, k.ln);
                k.ln.clearShadowLayer();
                k.ln.setStrokeWidth(u * 2.5f);
                k.ln.setColor(0xB3FFFFFF);
                if (x) { c.drawLine(cx - r, cy - r, cx + r, cy + r, k.ln); c.drawLine(cx + r, cy - r, cx - r, cy + r, k.ln); }
                else c.drawCircle(cx, cy, r, k.ln);
        }
    }
}
