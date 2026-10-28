package home.aurora.games;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;

/**
 * Connect 4: a holed board in the home look (95 silver, 70s orange plastic, gilt ebony,
 * a lit console…) with discs that drop in behind the board's face. The computer looks 1, 3 or 5 moves ahead by level.
 */
public class ConnectFour extends Game {

    private static final int W = 7, H = 6;
    private static final long DROP_MS = 380;
    private final int[][] g = new int[H][W];   // row 0 is the top
    private int col = 3;
    private int dropCol = -1, dropRow, dropPlayer;
    private long dropStart;
    /** The disc has landed but its turn hasn't ended yet: still no input. */
    private boolean settling;
    private int[][] winCells;

    public ConnectFour(Context c) {
        super(c);
        crisp.setAntiAlias(false);
        crisp.setFilterBitmap(false);
    }

    @Override public String title() { return "Connect 4"; }

    @Override
    protected void newGame() {
        for (int[] r : g) java.util.Arrays.fill(r, 0);
        col = 3;
        dropCol = -1;
        settling = false;
        winCells = null;
    }

    @Override protected boolean busy() { return dropCol >= 0 || settling; }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        col = Math.max(0, Math.min(W - 1, col + dx));
        return true;
    }

    @Override
    protected boolean playAtCursor() { return drop(col, turn); }

    private boolean drop(int c, int player) {
        int r = landing(c);
        if (r < 0) return false;
        dropCol = c;
        dropRow = r;
        dropPlayer = player;
        dropStart = SystemClock.uptimeMillis();
        invalidate();
        return true;
    }

    private int landing(int c) { return landing(g, c); }

    private static int landing(int[][] g, int c) {
        for (int r = H - 1; r >= 0; r--) if (g[r][c] == 0) return r;
        return -1;
    }

    @Override
    protected void cpuMove() {
        if (level == 0 && Math.random() < 0.35) {
            int c;
            do { c = (int) (Math.random() * W); } while (landing(c) < 0);
            col = c;
            drop(c, 2);
            return;
        }
        final int depth = level == 0 ? 1 : level == 1 ? 3 : 5, gn = gen;
        final int[][] snap = new int[H][];
        for (int r = 0; r < H; r++) snap[r] = g[r].clone();
        new Thread(() -> {   // the deep search takes a moment: keep the screen responsive
            int best = Integer.MIN_VALUE, pick = 3;
            for (int c : new int[]{3, 2, 4, 1, 5, 0, 6}) {
                int r = landing(snap, c);
                if (r < 0) continue;
                snap[r][c] = 2;
                int s = -negamax(snap, 1, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
                snap[r][c] = 0;
                if (s > best) { best = s; pick = c; }
            }
            final int at = pick;
            cpuDone(gn, () -> { col = at; drop(at, 2); });
        }, "connect4-ai").start();
    }

    /** Score from the point of view of {@code player} to move. */
    private static int negamax(int[][] g, int player, int depth, int alpha, int beta) {
        int w = check(g);
        if (w > 0) return w == player ? 100_000 + depth : -100_000 - depth;
        if (w == 0) return 0;
        if (depth == 0) return evaluate(g, player) - evaluate(g, 3 - player);
        for (int c : new int[]{3, 2, 4, 1, 5, 0, 6}) {
            int r = landing(g, c);
            if (r < 0) continue;
            g[r][c] = player;
            int s = -negamax(g, 3 - player, depth - 1, -beta, -alpha);
            g[r][c] = 0;
            if (s > alpha) alpha = s;
            if (alpha >= beta) break;
        }
        return alpha;
    }

    private static int evaluate(int[][] g, int p) {
        int score = 0;
        for (int r = 0; r < H; r++) if (g[r][3] == p) score += 3;
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < H; r++) for (int c = 0; c < W; c++) for (int[] d : dirs) {
            int er = r + 3 * d[0], ec = c + 3 * d[1];
            if (er < 0 || er >= H || ec < 0 || ec >= W) continue;
            int mine = 0, empty = 0;
            for (int k = 0; k < 4; k++) {
                int v = g[r + k * d[0]][c + k * d[1]];
                if (v == p) mine++; else if (v == 0) empty++;
            }
            if (mine == 3 && empty == 1) score += 5;
            else if (mine == 2 && empty == 2) score += 2;
        }
        return score;
    }

    private int check() { return check(g); }

    private static int check(int[][] g) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int r = 0; r < H; r++) for (int c = 0; c < W; c++) {
            int v = g[r][c];
            if (v == 0) continue;
            for (int[] d : dirs) {
                int er = r + 3 * d[0], ec = c + 3 * d[1];
                if (er < 0 || er >= H || ec < 0 || ec >= W) continue;
                if (g[r + d[0]][c + d[1]] == v && g[r + 2 * d[0]][c + 2 * d[1]] == v && g[er][ec] == v) return v;
            }
        }
        for (int c = 0; c < W; c++) if (g[0][c] == 0) return -1;
        return 0;
    }

    @Override
    protected int winner() {
        int w = check();
        if (w > 0) {
            int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
            for (int r = 0; r < H && winCells == null; r++) for (int c = 0; c < W && winCells == null; c++) for (int[] d : dirs) {
                int er = r + 3 * d[0], ec = c + 3 * d[1];
                if (er < 0 || er >= H || ec < 0 || ec >= W) continue;
                boolean all = true;
                for (int k = 0; k < 4; k++) all &= g[r + k * d[0]][c + k * d[1]] == w;
                if (all) {
                    winCells = new int[4][];
                    for (int k = 0; k < 4; k++) winCells[k] = new int[]{r + k * d[0], c + k * d[1]};
                    break;
                }
            }
        }
        return w;
    }

    private boolean isWinCell(int r, int c) {
        if (winCells == null) return false;
        for (int[] w : winCells) if (w[0] == r && w[1] == c) return true;
        return false;
    }

    // ------------------------------------------------------------------ the look

    private final BoardArt art = new BoardArt(this);
    private final Path holes = new Path();
    private final Paint crisp = new Paint();
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap face;
    private String faceKey;
    private final Bitmap[] pixel = new Bitmap[3];
    private String pixelKey;

    private String look() { return skin().id; }

    @Override public String sideName(int p) {
        switch (look()) {
            case "classic": return p == 1 ? "Red" : "Yellow";
            case "retro": return p == 1 ? "Mustard" : "Avocado";
            case "art": return p == 1 ? "Ivory" : "Oxblood";
            case "space": return p == 1 ? "Green" : "Amber";
            case "cozy": return p == 1 ? "Maple" : "Cherry";
            case "minimal": return p == 1 ? (art.night() ? "White" : "Black") : "Red";
            case "cs": return p == 1 ? "X" : "O";
            case "light": return p == 1 ? "Blue" : "Coral";
            default: return p == 1 ? "Blue" : "Peach";
        }
    }

    @Override public int uiColor(int p) {
        GameSkin s = skin();
        switch (look()) {
            case "classic": return p == 1 ? 0xFFC00000 : 0xFF808000;
            case "retro": return p == 1 ? 0xFFF0B040 : 0xFFA8B850;
            case "art": return p == 1 ? 0xFFF4EEE2 : 0xFFD0604A;
            case "cozy": return p == 1 ? 0xFFF0D29A : 0xFFE07050;
            case "minimal": return p == 1 ? s.ink : 0xFFB4361E;
            case "cs": return p == 1 ? s.ink : s.accent;
            default: return color(p);
        }
    }

    @Override protected int hintColor(int p) { return uiColor(p); }

    /** A disc in the look: pixel art, LED, ASCII, flat ink or a glossy counter. */
    private void piece(Canvas c, float cx, float cy, float r, int player, int alpha) {
        GameSkin s = skin();
        fill.setShader(null);
        fill.setColor(0xFF000000);
        switch (look()) {
            case "classic": {
                // chunky pixels about 2.5dp square, as many as fill the hole
                int sc = Math.max(1, Math.round(dp(2.5f))), n = Math.max(8, (int) (r * 2.06f / sc));
                String k = n + "/" + sc;
                if (!k.equals(pixelKey)) {
                    pixel[1] = BoardArt.pixelChecker(n, 0xFFFF0000, 0xFFFFFFFF, 0xFF800000, false, false);
                    pixel[2] = BoardArt.pixelChecker(n, 0xFFFFFF00, 0xFFFFFFFF, 0xFF808000, false, false);
                    pixelKey = k;
                }
                Bitmap b = pixel[player];
                int w = b.getWidth() * sc;
                int x = Math.round(cx - w / 2f), y = Math.round(cy - w / 2f);
                rf.set(x, y, x + w, y + w);
                crisp.setAlpha(alpha);
                c.drawBitmap(b, null, rf, crisp);
                crisp.setAlpha(255);
                return;
            }
            case "cs": {
                txt.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                txt.setTextSize(r * 1.5f);
                int col = player == 1 ? s.ink : s.accent;
                txt.setColor(alpha(col, alpha));
                txt.setShadowLayer(r * 0.35f, 0, 0, alpha(0xFF46F07A, alpha * 3 / 4));
                c.drawText(player == 1 ? "X" : "O", cx, cy - (txt.ascent() + txt.descent()) / 2, txt);
                txt.clearShadowLayer();
                return;
            }
            case "minimal": {
                fill.setColor(alpha(player == 1 ? s.ink : 0xFFB4361E, alpha));
                c.drawCircle(cx, cy, r * 0.92f, fill);
                return;
            }
            case "space": {
                int col = color(player);
                fill.setShader(new RadialGradient(cx, cy, r * 1.5f, new int[]{alpha(col, alpha * 2 / 5), alpha(col, 0)},
                        null, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r * 1.5f, fill);
                fill.setShader(new RadialGradient(cx - r * 0.2f, cy - r * 0.25f, r * 1.1f,
                        new int[]{alpha(0xFFFFFFFF, alpha), alpha(lighten(col), alpha), alpha(col, alpha), alpha(shade(col), alpha)},
                        new float[]{0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r * 0.88f, fill);
                fill.setShader(null);
                stroke.setStrokeWidth(r * 0.06f);
                stroke.setColor(alpha(darken(col), alpha * 3 / 5));
                c.drawCircle(cx, cy, r * 0.6f, stroke);
                return;
            }
            case "retro":
                glossy(c, cx, cy, r, player == 1 ? 0xFFEAAA30 : 0xFF84963A, player == 1 ? 0xFFFFD880 : 0xFFC0D070,
                        player == 1 ? 0xFF9A6410 : 0xFF3A4410, 0xFF2A160A, alpha);
                return;
            case "art":
                glossy(c, cx, cy, r, player == 1 ? 0xFFEDE4D0 : 0xFF8A2420, player == 1 ? 0xFFFFFCF4 : 0xFFC8483C,
                        player == 1 ? 0xFFB0A080 : 0xFF3A0A08, 0, alpha);
                stroke.setStrokeWidth(r * 0.07f);
                stroke.setColor(alpha(0xFFC9A24A, alpha));
                c.drawCircle(cx, cy, r * 0.66f, stroke);
                return;
            case "cozy":
                glossy(c, cx, cy, r, player == 1 ? 0xFFDDB880 : 0xFF8A3420, player == 1 ? 0xFFF8E2B8 : 0xFFC0603C,
                        player == 1 ? 0xFF8A6438 : 0xFF3A1008, 0, alpha);
                stroke.setStrokeWidth(r * 0.025f);
                stroke.setColor(alpha(player == 1 ? 0xFF8A6438 : 0xFF2A0A04, alpha / 3));
                for (float k = 0.25f; k < 0.6f; k += 0.12f) c.drawCircle(cx + r * 0.05f, cy + r * 0.04f, r * k, stroke);
                return;
            case "light":
                glossy(c, cx, cy, r, color(player), lighten(color(player)), shade(color(player)), 0, alpha);
                return;
            default:
                disc(c, cx, cy, r, player, alpha);
        }
    }

    /** A molded counter lit from the top-left, with a raised inner ring. */
    private void glossy(Canvas c, float cx, float cy, float r, int base, int light, int dark, int outline, int alpha) {
        if (outline != 0) {
            fill.setColor(alpha(outline, alpha));
            c.drawCircle(cx, cy, r + dp(2), fill);
        }
        fill.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.5f,
                new int[]{alpha(light, alpha), alpha(base, alpha), alpha(dark, alpha)}, new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(r * 0.09f);
        stroke.setColor(alpha(dark, (int) (alpha * 0.7f)));
        c.drawCircle(cx, cy, r * 0.68f, stroke);
        stroke.setStrokeWidth(r * 0.05f);
        stroke.setColor(alpha(light, (int) (alpha * 0.7f)));
        c.drawCircle(cx - r * 0.03f, cy - r * 0.03f, r * 0.62f, stroke);
    }

    @Override
    protected void onDraw(Canvas c) {
        String look = look();
        float cell = Math.min(getWidth() / (W + 0.6f), getHeight() / (H + 1.9f)) * 0.95f;
        float bw = cell * W, bh = cell * H;
        float left = (getWidth() - bw) / 2, top = (getHeight() - bh) / 2 + cell * 0.35f;
        float r = cell * 0.39f, pad = cell * 0.22f;
        boolean see = !look.equals("cs") && !look.equals("minimal");   // a face the discs sit behind

        // the piece you're about to drop, above the chosen column
        if (!over && !busy()) piece(c, left + cell * (col + 0.5f), top - cell * 0.62f, r, turn, cpuTurn() ? 110 : 255);

        // looking through the holes: the inside of the board behind the pieces
        if (see) {
            fill.setShader(null);
            fill.setColor(interior());
            rf.set(left - pad * 0.3f, top - pad * 0.3f, left + bw + pad * 0.3f, top + bh + pad * 0.3f);
            c.drawRect(rf, fill);
        } else {
            drawFace(c, left, top, bw, bh, cell, r, pad);
        }

        // pieces sit (and fall) behind the board's face
        for (int row = 0; row < H; row++) for (int k = 0; k < W; k++)
            if (g[row][k] != 0) piece(c, left + cell * (k + 0.5f), top + cell * (row + 0.5f), r, g[row][k], 255);
        if (dropCol >= 0) {
            float t = Math.min(1f, (SystemClock.uptimeMillis() - dropStart) / (float) DROP_MS);
            float from = top - cell * 0.62f, to = top + cell * (dropRow + 0.5f);
            piece(c, left + cell * (dropCol + 0.5f), from + (to - from) * t * t, r, dropPlayer, 255);
            if (t >= 1f) {
                g[dropRow][dropCol] = dropPlayer;
                dropCol = -1;
                settling = true;       // busy until the turn has really passed, so a quick second OK can't drop again
                post(() -> {           // not from inside draw: it updates other views
                    if (!settling) return;   // a new round started meanwhile
                    settling = false;
                    endTurn();
                });
            } else {
                postInvalidateOnAnimation();
            }
        }

        if (see) drawFace(c, left, top, bw, bh, cell, r, pad);

        // four in a row: ring the winners
        if (winCells != null) for (int[] w : winCells) {
            float cx = left + cell * (w[1] + 0.5f), cy = top + cell * (w[0] + 0.5f);
            winMark(c, cx, cy, r, cell);
        }

        if (!over && !busy() && !cpuTurn()) columnCursor(c, left + cell * col, top, cell, bh, pad);
    }

    private int interior() {
        switch (look()) {
            case "classic": return 0xFFFFFFFF;
            case "retro": return 0xFF2A160A;
            case "art": return 0xFF120806;
            case "space": return 0xFF05070A;
            case "cozy": return 0xFF140A04;
            case "light": return 0xFFE8E4DC;
            default: return 0x660A1024;
        }
    }

    /** The board's face (with its holes), feet and labels, cached as a bitmap per look and size. */
    private void drawFace(Canvas c, float left, float top, float bw, float bh, float cell, float r, float pad) {
        String k = look() + art.night() + getWidth() + "x" + getHeight();
        if (face == null || !k.equals(faceKey)) {
            if (face != null) face.recycle();
            face = Bitmap.createBitmap(Math.max(1, getWidth()), Math.max(1, getHeight()), Bitmap.Config.ARGB_8888);
            renderFace(new Canvas(face), left, top, bw, bh, cell, r, pad);
            faceKey = k;
        }
        c.drawBitmap(face, 0, 0, null);
    }

    private void renderFace(Canvas c, float left, float top, float bw, float bh, float cell, float r, float pad) {
        GameSkin s = skin();
        float hr = r * 1.04f;
        RectF board = new RectF(left - pad, top - pad, left + bw + pad, top + bh + pad);
        holes.reset();
        holes.setFillType(Path.FillType.EVEN_ODD);
        switch (look()) {
            case "cs": {
                int gr = s.accent;
                txt.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                txt.setTextSize(cell * 0.3f);
                txt.setColor(0xFF22A048);
                for (int k = 0; k < W; k++)
                    c.drawText(String.valueOf(k + 1), left + cell * (k + 0.5f), top + bh + pad + cell * 0.32f, txt);
                stroke.setStrokeCap(Paint.Cap.SQUARE);
                stroke.setStrokeWidth(dp(1.5f));
                stroke.setColor(gr);
                stroke.setShadowLayer(dp(5), 0, 0, 0x9946F07A);
                for (int k = 0; k <= W; k++) c.drawLine(left + cell * k, top - pad * 0.3f, left + cell * k, top + bh, stroke);
                c.drawLine(left - pad * 0.6f, top + bh, left + bw + pad * 0.6f, top + bh, stroke);
                c.drawLine(left - pad * 0.6f, top + bh + dp(5), left + bw + pad * 0.6f, top + bh + dp(5), stroke);
                stroke.clearShadowLayer();
                stroke.setStrokeCap(Paint.Cap.ROUND);
                txt.setTextSize(cell * 0.4f);
                txt.setColor(0xFF1A6A30);
                for (int row = 0; row < H; row++) for (int k = 0; k < W; k++)
                    c.drawText("·", left + cell * (k + 0.5f), top + cell * (row + 0.5f) - (txt.ascent() + txt.descent()) / 2, txt);
                return;
            }
            case "minimal": {
                stroke.setStrokeWidth(Math.max(1, dp(0.75f)));
                stroke.setColor(alpha(s.ink, 0x40));
                for (int row = 0; row < H; row++) for (int k = 0; k < W; k++)
                    c.drawCircle(left + cell * (k + 0.5f), top + cell * (row + 0.5f), hr, stroke);
                stroke.setColor(alpha(s.ink, 0xCC));
                c.drawRect(board, stroke);
                c.drawLine(board.left - cell * 0.3f, board.bottom + dp(10), board.right + cell * 0.3f, board.bottom + dp(10), stroke);
                return;
            }
        }
        for (int row = 0; row < H; row++) for (int k = 0; k < W; k++)
            holes.addCircle(left + cell * (k + 0.5f), top + cell * (row + 0.5f), hr, Path.Direction.CW);
        switch (look()) {
            case "classic": {
                int u = Math.max(1, Math.round(dp(1)));
                int L = Math.round(board.left), T = Math.round(board.top), R = Math.round(board.right), B = Math.round(board.bottom);
                // feet
                int fw = Math.round(cell * 0.5f), fh = Math.round(cell * 0.5f);
                art.bevel(c, L, B - 2 * u, L + fw, B + fh, u, true);
                art.bevel(c, R - fw, B - 2 * u, R, B + fh, u, true);
                art.bevel(c, L, T, R, B, u, true);
                // holes cut in so they show the inside; then each a sunken radio-button well
                Paint clear = new Paint();
                clear.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                c.drawPath(holes, clear);
                Paint ring = new Paint();
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(u);
                for (int row = 0; row < H; row++) for (int k = 0; k < W; k++) {
                    float cx = Math.round(left + cell * (k + 0.5f)), cy = Math.round(top + cell * (row + 0.5f));
                    rf.set(cx - hr - u * 0.5f, cy - hr - u * 0.5f, cx + hr + u * 0.5f, cy + hr + u * 0.5f);
                    ring.setColor(0xFF808080); c.drawArc(rf, 135, 180, false, ring);
                    ring.setColor(0xFFFFFFFF); c.drawArc(rf, 315, 180, false, ring);
                    rf.inset(-u, -u);
                    ring.setColor(0xFF000000); c.drawArc(rf, 135, 180, false, ring);
                    ring.setColor(0xFFDFDFDF); c.drawArc(rf, 315, 180, false, ring);
                }
                return;
            }
            case "retro": {
                feet(c, board, cell, 0xFF5A3418, 0xFF2A160A, true);
                face(c, board, cell * 0.3f, new LinearGradient(0, board.top, 0, board.bottom,
                        new int[]{0xFFE87A40, 0xFFC4552A, 0xFFA03E18}, null, Shader.TileMode.CLAMP), 0xCC000000);
                holeBevels(c, left, top, cell, hr, 0x993A1408, 0x66FFD8A0, cell * 0.05f);
                // the chunky molded rim and a gloss band
                stroke.setStrokeWidth(cell * 0.05f);
                stroke.setColor(0xFF2A160A);
                c.drawRoundRect(board, cell * 0.3f, cell * 0.3f, stroke);
                stroke.setStrokeWidth(cell * 0.03f);
                stroke.setColor(0x80FFE0B0);
                rf.set(board.left + cell * 0.06f, board.top + cell * 0.06f, board.right - cell * 0.06f, board.bottom - cell * 0.06f);
                c.drawRoundRect(rf, cell * 0.25f, cell * 0.25f, stroke);
                // a mustard and avocado stripe along the bottom rail
                fill.setShader(null);
                fill.setColor(0xFFE0A030);
                c.drawRect(board.left + cell * 0.3f, board.bottom - pad * 0.75f, board.right - cell * 0.3f, board.bottom - pad * 0.55f, fill);
                fill.setColor(0xFF6B7A2A);
                c.drawRect(board.left + cell * 0.3f, board.bottom - pad * 0.5f, board.right - cell * 0.3f, board.bottom - pad * 0.3f, fill);
                return;
            }
            case "art": {
                feet(c, board, cell, 0xFFB8893A, 0xFF5A3A10, false);
                face(c, board, 0, new LinearGradient(board.left, board.top, board.right, board.bottom,
                        new int[]{0xFF3A2E24, 0xFF1A120C, 0xFF0C0806}, null, Shader.TileMode.CLAMP), 0xCC000000);
                // gilt rings round every hole, a gilt frame round the board
                stroke.setStrokeWidth(cell * 0.04f);
                for (int row = 0; row < H; row++) for (int k = 0; k < W; k++) {
                    float cx = left + cell * (k + 0.5f), cy = top + cell * (row + 0.5f);
                    stroke.setShader(new LinearGradient(cx - hr, cy - hr, cx + hr, cy + hr, 0xFFF2D48A, 0xFF8A6A2A, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy, hr + cell * 0.02f, stroke);
                }
                stroke.setStrokeWidth(cell * 0.09f);
                stroke.setShader(new LinearGradient(board.left, board.top, board.right, board.bottom,
                        new int[]{0xFFF6DC94, 0xFFB8893A, 0xFFE8C470, 0xFF8A6A2A}, null, Shader.TileMode.CLAMP));
                c.drawRect(board, stroke);
                stroke.setShader(null);
                stroke.setStrokeWidth(cell * 0.015f);
                stroke.setColor(0xFFF2D48A);
                rf.set(board.left + cell * 0.1f, board.top + cell * 0.1f, board.right - cell * 0.1f, board.bottom - cell * 0.1f);
                c.drawRect(rf, stroke);
                fill.setShader(new RadialGradient(board.centerX(), board.top + board.height() * 0.2f, board.width() * 0.8f,
                        0x22FFF4DC, 0x00FFF4DC, Shader.TileMode.CLAMP));
                Path lit = new Path(holes);
                lit.addRect(board, Path.Direction.CW);
                c.drawPath(lit, fill);
                fill.setShader(null);
                return;
            }
            case "space": {
                feet(c, board, cell, 0xFF4A4F55, 0xFF1A1D20, false);
                face(c, board, cell * 0.12f, new LinearGradient(0, board.top, 0, board.bottom,
                        new int[]{0xFF6A7078, 0xFF40454B, 0xFF26292D}, null, Shader.TileMode.CLAMP), 0xCC000000);
                Path metal = new Path(holes);
                metal.addRect(board, Path.Direction.CW);
                c.save();
                c.clipPath(metal);
                art.brushed(c, board);
                c.restore();
                holeBevels(c, left, top, cell, hr, 0xCC0A0C0E, 0x55FFFFFF, cell * 0.04f);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x806FE08A);
                for (int row = 0; row < H; row++) for (int k = 0; k < W; k++)
                    c.drawCircle(left + cell * (k + 0.5f), top + cell * (row + 0.5f), hr - dp(2), stroke);
                stroke.setColor(0x66FFFFFF);
                c.drawRoundRect(board, cell * 0.12f, cell * 0.12f, stroke);
                for (int i = 0; i < 4; i++)
                    art.screw(c, i % 2 == 0 ? board.left + pad * 0.5f : board.right - pad * 0.5f,
                            i < 2 ? board.top + pad * 0.5f : board.bottom - pad * 0.5f, pad * 0.22f);
                txt.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                txt.setTextSize(cell * 0.2f);
                txt.setColor(s.accent);
                txt.setShadowLayer(dp(4), 0, 0, 0x99FFC56A);
                for (int k = 0; k < W; k++)
                    c.drawText("C" + (k + 1), left + cell * (k + 0.5f), board.bottom + cell * 0.36f, txt);
                txt.clearShadowLayer();
                return;
            }
            case "cozy": {
                feet(c, board, cell, 0xFF4A2A14, 0xFF1A0C04, true);
                face(c, board, cell * 0.16f, art.wood(1.2f, 0, 0), 0xCC000000);
                fill.setShader(new LinearGradient(0, board.top, 0, board.bottom, 0x14FFE0B0, 0x59000000, Shader.TileMode.CLAMP));
                Path wood = new Path(holes);
                wood.addRoundRect(board, cell * 0.16f, cell * 0.16f, Path.Direction.CW);
                c.drawPath(wood, fill);
                fill.setShader(new RadialGradient(board.right, board.bottom, board.width() * 1.1f, 0x30FFA040, 0x00FFA040, Shader.TileMode.CLAMP));
                c.drawPath(wood, fill);
                fill.setShader(null);
                stroke.setStrokeWidth(cell * 0.045f);
                for (int row = 0; row < H; row++) for (int k = 0; k < W; k++) {
                    float cx = left + cell * (k + 0.5f), cy = top + cell * (row + 0.5f);
                    stroke.setShader(new LinearGradient(cx - hr, cy - hr, cx + hr, cy + hr,
                            new int[]{0xFFF2D48A, 0xFFB8893A, 0xFF6A4A18}, null, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy, hr + cell * 0.015f, stroke);
                }
                stroke.setShader(null);
                stroke.setStrokeWidth(cell * 0.02f);
                stroke.setColor(0x40FFE0B0);
                c.drawRoundRect(board, cell * 0.16f, cell * 0.16f, stroke);
                return;
            }
            case "light": {
                feet(c, board, cell, 0xFFFFFFFF, 0xFFD8D4CC, false);
                face(c, board, cell * 0.28f, null, 0x2E000000);
                holeBevels(c, left, top, cell, hr, 0x33000000, 0xCCFFFFFF, cell * 0.04f);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x14000000);
                c.drawRoundRect(board, cell * 0.28f, cell * 0.28f, stroke);
                return;
            }
            default: { // aurora: frosted glass
                face(c, board, cell * 0.24f, new LinearGradient(0, board.top, 0, board.bottom, 0x52FFFFFF, 0x29FFFFFF,
                        Shader.TileMode.CLAMP), 0);
                holeBevels(c, left, top, cell, hr, 0x40000000, 0x66FFFFFF, cell * 0.03f);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x80FFFFFF);
                c.drawRoundRect(board, cell * 0.24f, cell * 0.24f, stroke);
            }
        }
    }

    /** The holed face, with an optional drop shadow; a null shader paints it frosted white. */
    private void face(Canvas c, RectF board, float rad, Shader sh, int shadow) {
        Path p = new Path(holes);
        p.addRoundRect(board, rad, rad, Path.Direction.CW);
        fill.setShader(null);
        if (shadow != 0) {
            fill.setColor(sh == null ? 0xF7FFFFFF : 0xFF000000);
            fill.setShadowLayer(dp(16), 0, dp(6), shadow);
            c.drawPath(p, fill);
            fill.clearShadowLayer();
        }
        if (sh != null) {
            fill.setColor(0xFF000000);
            fill.setShader(sh);
            c.drawPath(p, fill);
            fill.setShader(null);
        }
    }

    private void holeBevels(Canvas c, float left, float top, float cell, float hr, int shade, int catchLight, float w) {
        stroke.setShader(null);
        stroke.setStrokeWidth(w);
        for (int row = 0; row < H; row++) for (int k = 0; k < W; k++) {
            float cx = left + cell * (k + 0.5f), cy = top + cell * (row + 0.5f);
            rf.set(cx - hr, cy - hr, cx + hr, cy + hr);
            stroke.setColor(shade);
            c.drawArc(rf, 180, 180, false, stroke);
            stroke.setColor(catchLight);
            c.drawArc(rf, 20, 140, false, stroke);
        }
    }

    private void feet(Canvas c, RectF board, float cell, int top, int bottom, boolean round) {
        for (int side = 0; side < 2; side++) {
            float fx = side == 0 ? board.left : board.right - cell * 0.5f;
            rf.set(fx, board.bottom - cell * 0.2f, fx + cell * 0.5f, board.bottom + cell * 0.55f);
            fill.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, top, bottom, Shader.TileMode.CLAMP));
            fill.setShadowLayer(dp(6), 0, dp(3), 0x66000000);
            c.drawRoundRect(rf, round ? cell * 0.12f : cell * 0.04f, round ? cell * 0.12f : cell * 0.04f, fill);
            fill.clearShadowLayer();
            fill.setShader(null);
        }
    }

    private void winMark(Canvas c, float cx, float cy, float r, float cell) {
        float p = pulse();
        switch (look()) {
            case "classic": {
                int u = Math.max(2, Math.round(dp(1.5f)));
                crisp.setColor(p > 0.5f ? 0xFF000080 : 0xFFFFFFFF);
                int L = Math.round(cx - r), T = Math.round(cy - r), R = Math.round(cx + r), B = Math.round(cy + r);
                c.drawRect(L, T, R, T + u, crisp); c.drawRect(L, B - u, R, B, crisp);
                c.drawRect(L, T, L + u, B, crisp); c.drawRect(R - u, T, R, B, crisp);
                return;
            }
            case "cs": {
                fill.setShader(null);
                fill.setColor(alpha(skin().accent, (int) (90 + 80 * p)));
                c.drawRect(cx - cell / 2 + dp(2), cy - cell / 2 + dp(2), cx + cell / 2 - dp(2), cy + cell / 2 - dp(2), fill);
                return;
            }
            case "minimal": {
                stroke.setStrokeWidth(dp(2));
                stroke.setColor(skin().ink);
                c.drawCircle(cx, cy, r * 1.15f, stroke);
                return;
            }
        }
        int col;
        switch (look()) {
            case "art": col = 0xFFF2D48A; break;
            case "space": col = 0xFFFFE6B0; break;
            case "cozy": col = 0xFFFFD080; break;
            case "retro": col = 0xFFFFF6E0; break;
            case "light": col = 0xFF1C1C1E; break;
            default: col = 0xFFFFFFFF;
        }
        stroke.setStrokeWidth(cell * 0.07f);
        stroke.setColor(alpha(col, (int) (170 + 85 * p)));
        if (!look().equals("light")) stroke.setShadowLayer(dp(6), 0, 0, alpha(col, 0xAA));
        c.drawCircle(cx, cy, r * 0.9f, stroke);
        stroke.clearShadowLayer();
    }

    /** The column the next disc drops down. */
    private void columnCursor(Canvas c, float x, float top, float cell, float bh, float pad) {
        float p = pulse();
        float t = top - pad - dp(4), b = top + bh + pad + dp(4);
        switch (look()) {
            case "classic": {
                // a dotted focus rectangle and a black arrow pointing down the column
                int u = Math.max(2, Math.round(dp(1.5f)));
                int L = Math.round(x + dp(3)), R = Math.round(x + cell - dp(3)), T = Math.round(top - pad + dp(3)), B = Math.round(top + bh + pad - dp(3));
                crisp.setColor(0xFF000000);
                for (int px = L; px < R; px += 2 * u) { c.drawRect(px, T, px + u, T + u, crisp); c.drawRect(px, B - u, px + u, B, crisp); }
                for (int py = T; py < B; py += 2 * u) { c.drawRect(L, py, L + u, py + u, crisp); c.drawRect(R - u, py, R, py + u, crisp); }
                return;
            }
            case "cs": {
                fill.setShader(null);
                fill.setColor(alpha(skin().accent, (int) (24 + 20 * p)));
                c.drawRect(x + dp(2), top - pad * 0.3f, x + cell - dp(2), top + bh, fill);
                fill.setColor(alpha(skin().accent, (int) (190 + 65 * p)));
                c.drawRect(x + dp(2), top + bh + dp(8), x + cell - dp(2), top + bh + pad + cell * 0.42f, fill);
                txt.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                txt.setTextSize(cell * 0.3f);
                txt.setColor(skin().onAccent);
                c.drawText(String.valueOf(col + 1), x + cell / 2, top + bh + pad + cell * 0.32f, txt);
                return;
            }
            case "minimal": {
                stroke.setStrokeWidth(dp(2));
                stroke.setColor(alpha(skin().ink, (int) (150 + 105 * p)));
                c.drawRect(x + dp(4), t, x + cell - dp(4), b, stroke);
                return;
            }
            case "space": {
                stroke.setStrokeWidth(dp(3));
                stroke.setColor(alpha(skin().accent, (int) (170 + 85 * p)));
                stroke.setShadowLayer(dp(6), 0, 0, 0xAAFFC56A);
                float k = cell * 0.3f, l = x + dp(2), r = x + cell - dp(2);
                c.drawLine(l, t, l + k, t, stroke); c.drawLine(l, t, l, t + k, stroke);
                c.drawLine(r, t, r - k, t, stroke); c.drawLine(r, t, r, t + k, stroke);
                c.drawLine(l, b, l + k, b, stroke); c.drawLine(l, b, l, b - k, stroke);
                c.drawLine(r, b, r - k, b, stroke); c.drawLine(r, b, r, b - k, stroke);
                stroke.clearShadowLayer();
                return;
            }
        }
        int col; float w = 3f;
        switch (look()) {
            case "retro": col = 0xFFFFB347; w = 5f; break;
            case "art": col = 0xFFF2D48A; break;
            case "cozy": col = 0xFFFFB04A; break;
            case "light": col = 0xFF1C1C1E; break;
            default: col = 0xFFFFFFFF;
        }
        stroke.setStrokeWidth(dp(w));
        stroke.setColor(alpha(col, (int) (150 + 105 * p)));
        if (!look().equals("light")) stroke.setShadowLayer(dp(6 + 4 * p), 0, 0, alpha(col, 0xAA));
        rf.set(x + dp(2), t, x + cell - dp(2), b);
        float rad = look().equals("art") ? 0 : dp(14);
        c.drawRoundRect(rf, rad, rad, stroke);
        stroke.clearShadowLayer();
    }
}
