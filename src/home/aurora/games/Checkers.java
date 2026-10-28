package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * American checkers: men move and capture forward, kings both ways, captures are
 * compulsory and multi-jumps continue (you pick each hop). Reaching the far row
 * crowns a king and ends the move. Player 1 starts at the bottom.
 */
public class Checkers extends Game {

    private static final int N = 8;
    /** board: 0 empty, 1/2 man of player 1/2, 3/4 king of player 1/2. */
    private int[] b = new int[N * N];
    private int cr = 5, cc = 0;
    private int sel = -1;
    private final List<Integer> steps = new ArrayList<>();
    private int lastFrom = -1, lastTo = -1;
    /** Moves in a row, counting both sides, where only a king moved and nothing was taken. */
    private int quiet;

    static class Move {
        final int[] path, caps;
        Move(int[] path, int[] caps) { this.path = path; this.caps = caps; }
    }

    private final BoardArt art = new BoardArt(this);

    public Checkers(Context c) { super(c); }

    @Override public String title() { return "Checkers"; }
    @Override public boolean classic() { return true; }

    /** The men's colors in looks where they aren't white and black. */
    @Override public String sideName(int p) {
        switch (skin().id) {
            case "classic": return p == 1 ? "Red" : "Black";
            case "retro": return p == 1 ? "Cream" : "Orange";
            case "space": return p == 1 ? "Green" : "Amber";
            default: return super.sideName(p);
        }
    }

    @Override public int uiColor(int p) {
        switch (skin().id) {
            case "classic": return p == 1 ? 0xFFC00000 : 0xFF000000;
            case "retro": return p == 1 ? 0xFFF6EBD0 : 0xFFE8784A;
            case "space": return color(p);
            default: return super.uiColor(p);
        }
    }

    @Override
    protected void newGame() {
        b = new int[N * N];
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            if ((r + c) % 2 == 0) continue;
            if (r < 3) b[r * N + c] = 2;
            else if (r > 4) b[r * N + c] = 1;
        }
        sel = -1;
        steps.clear();
        lastFrom = lastTo = -1;
        quiet = 0;
        int me = vsCpu ? 1 : turn;   // start the cursor on the human's side, not the computer's
        cr = me == 1 ? 5 : 2;
        cc = me == 1 ? 0 : 1;
        info();
    }

    // ------------------------------------------------------------------ rules

    static int owner(int v) { return v == 1 || v == 3 ? 1 : v == 2 || v == 4 ? 2 : 0; }
    static boolean king(int v) { return v == 3 || v == 4; }

    static List<Move> moves(int[] b, int p) {
        List<Move> caps = new ArrayList<>(), simple = new ArrayList<>();
        for (int sq = 0; sq < N * N; sq++) {
            if (owner(b[sq]) != p) continue;
            jumps(b, sq, b[sq], new int[]{sq}, new int[0], caps);
            if (!caps.isEmpty()) continue;
            for (int[] d : dirs(b[sq])) {
                int r = sq / N + d[0], c = sq % N + d[1];
                if (r >= 0 && r < N && c >= 0 && c < N && b[r * N + c] == 0)
                    simple.add(new Move(new int[]{sq, r * N + c}, new int[0]));
            }
        }
        return caps.isEmpty() ? simple : caps;
    }

    private static int[][] dirs(int v) {
        if (king(v)) return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        return owner(v) == 1 ? new int[][]{{-1, -1}, {-1, 1}} : new int[][]{{1, -1}, {1, 1}};
    }

    /** Depth-first search for maximal jump sequences. Captured pieces become -1 (blocking, not re-jumpable). */
    private static void jumps(int[] b, int sq, int piece, int[] path, int[] caps, List<Move> out) {
        boolean extended = false;
        int p = owner(piece);
        for (int[] d : dirs(piece)) {
            int r = sq / N, c = sq % N;
            int mr = r + d[0], mc = c + d[1], lr = r + 2 * d[0], lc = c + 2 * d[1];
            if (lr < 0 || lr >= N || lc < 0 || lc >= N) continue;
            int mid = mr * N + mc, land = lr * N + lc;
            if (owner(b[mid]) != 3 - p || b[land] != 0) continue;
            int[] nb = b.clone();
            nb[sq] = 0;
            nb[mid] = -1;
            nb[land] = piece;
            int[] np = append(path, land), nc = append(caps, mid);
            boolean crowns = !king(piece) && (lr == 0 || lr == N - 1);
            if (crowns) out.add(new Move(np, nc));
            else jumps(nb, land, piece, np, nc, out);
            extended = true;
        }
        if (!extended && caps.length > 0) out.add(new Move(path, caps));
    }

    private static int[] append(int[] a, int v) {
        int[] o = java.util.Arrays.copyOf(a, a.length + 1);
        o[a.length] = v;
        return o;
    }

    static void apply(int[] b, Move m) {
        int piece = b[m.path[0]];
        b[m.path[0]] = 0;
        for (int c : m.caps) b[c] = 0;
        int to = m.path[m.path.length - 1];
        int row = to / N;
        if (piece == 1 && row == 0) piece = 3;
        if (piece == 2 && row == N - 1) piece = 4;
        b[to] = piece;
    }

    private void commit(Move m) {
        boolean kingQuiet = m.caps.length == 0 && king(b[m.path[0]]);
        quiet = kingQuiet ? quiet + 1 : 0;
        apply(b, m);
        lastFrom = m.path[0];
        lastTo = m.path[m.path.length - 1];
        sel = -1;
        steps.clear();
        info();
        endTurn();
    }

    private int count(int p) {
        int n = 0;
        for (int v : b) if (owner(v) == p) n++;
        return n;
    }

    private void info() {
        if (host == null) return;
        boolean must = !moves(b, turn).isEmpty() && moves(b, turn).get(0).caps.length > 0;
        host.onInfo("Pieces   " + count(1) + "  vs  " + count(2) + (must && !over ? "   ·   must jump" : ""));
    }

    @Override
    protected int winner() {
        int next = 3 - turn;
        if (moves(b, next).isEmpty()) return turn;
        if (quiet >= 100) return 0;   // 50 moves each (100 in all) with only kings moving and nothing taken: draw
        return -1;
    }

    @Override
    protected void endTurn() {
        super.endTurn();
        info();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean moveCursor(int dx, int dy) {
        cr = Math.max(0, Math.min(N - 1, cr + screenDy(dy)));
        cc = Math.max(0, Math.min(N - 1, cc + screenDx(dx)));
        return true;
    }

    /** Moves still consistent with the selected piece and the hops chosen so far. */
    private List<Move> candidates() {
        List<Move> out = new ArrayList<>();
        if (sel < 0) return out;
        outer:
        for (Move m : moves(b, turn)) {
            if (m.path[0] != sel || m.path.length < steps.size() + 2) continue;
            for (int i = 0; i < steps.size(); i++) if (m.path[i + 1] != steps.get(i)) continue outer;
            out.add(m);
        }
        return out;
    }

    @Override
    protected boolean playAtCursor() {
        int sq = cr * N + cc;
        if (sel >= 0) {
            for (Move m : candidates()) {
                if (m.path[steps.size() + 1] != sq) continue;
                if (m.path.length == steps.size() + 2) { commit(m); return true; }
                steps.add(sq);              // more hops to go with this piece
                return true;
            }
            if (sq == sel && steps.isEmpty()) { sel = -1; return true; }
        }
        if (steps.isEmpty() && owner(b[sq]) == turn) {
            for (Move m : moves(b, turn)) if (m.path[0] == sq) { sel = sq; return true; }
        }
        return false;
    }

    // ------------------------------------------------------------------ computer

    @Override
    protected void cpuMove() {
        final int[] snap = b.clone();
        final int depth = level == 0 ? 1 : level == 1 ? 3 : 5;
        final boolean loose = level == 0 && Math.random() < 0.3;
        final int g = gen;
        new Thread(() -> {
            Move best = null;
            if (loose) {
                List<Move> all = moves(snap, 2);
                final Move any = all.get((int) (Math.random() * all.size()));
                cpuDone(g, () -> commit(any));
                return;
            }
            int bestScore = Integer.MIN_VALUE;
            for (Move m : moves(snap, 2)) {
                int[] nb = snap.clone();
                apply(nb, m);
                int s = -negamax(nb, 1, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
                if (s > bestScore || (s == bestScore && Math.random() < 0.3)) { bestScore = s; best = m; }
            }
            final Move pick = best;
            if (pick != null) cpuDone(g, () -> commit(pick));
        }, "checkers-ai").start();
    }

    private static int negamax(int[] b, int p, int depth, int alpha, int beta) {
        List<Move> ms = moves(b, p);
        if (ms.isEmpty()) return -10_000 - depth;
        if (depth == 0) return eval(b, p);
        for (Move m : ms) {
            int[] nb = b.clone();
            apply(nb, m);
            int s = -negamax(nb, 3 - p, depth - 1, -beta, -alpha);
            if (s > alpha) alpha = s;
            if (alpha >= beta) break;
        }
        return alpha;
    }

    private static int eval(int[] b, int p) {
        int s = 0;
        for (int sq = 0; sq < N * N; sq++) {
            int v = b[sq], o = owner(v);
            if (o == 0) continue;
            int row = sq / N, adv = o == 1 ? N - 1 - row : row;
            int val = king(v) ? 160 : 100 + adv * 4;
            s += o == p ? val : -val;
        }
        return s;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected boolean turnsBoard() { return true; }

    @Override
    protected void onDraw(Canvas c) {
        beginBoard(c);
        drawBoard(c);
        c.restore();
    }

    private void drawBoard(Canvas c) {
        float side = Math.min(getWidth(), getHeight()) * 0.92f, cell = side / N;
        float left = (getWidth() - side) / 2, top = (getHeight() - side) / 2;
        art.board(c, left, top, side, N);
        for (int sq = 0; sq < N * N; sq++)
            if (sq == lastFrom || sq == lastTo) art.lastMove(c, left + (sq % N) * cell, top + (sq / N) * cell, cell, true);

        boolean mine = !over && !cpuTurn();
        List<Move> legal = mine ? moves(b, turn) : new ArrayList<>();
        boolean mustJump = !legal.isEmpty() && legal.get(0).caps.length > 0;
        if (sel >= 0) art.selected(c, left + (sel % N) * cell, top + (sel / N) * cell, cell);
        int cur = mine ? cr * N + cc : -1;
        boolean inverse = mine && art.cursorUnder(c, left + cc * cell, top + cr * cell, cell);

        for (int sq = 0; sq < N * N; sq++) {
            int v = b[sq];
            if (owner(v) == 0) continue;
            float cx = left + (sq % N + 0.5f) * cell, cy = top + (sq / N + 0.5f) * cell;
            if (mine && sel < 0 && mustJump && owner(v) == turn) {
                for (Move m : legal) if (m.path[0] == sq) {
                    art.hint(c, cx, cy, cell, true);   // this piece can (must) jump
                    break;
                }
            }
            c.save();
            upright(c, cx, cy);   // keeps the light on each piece coming from the top-left
            art.checker(c, cx, cy, cell, owner(v), king(v), inverse && sq == cur);
            c.restore();
        }

        if (sel >= 0) {
            for (int s : steps) art.hint(c, left + (s % N + 0.5f) * cell, top + (s / N + 0.5f) * cell, cell, false);
            for (Move m : candidates()) {
                int d = m.path[steps.size() + 1];
                art.hint(c, left + (d % N + 0.5f) * cell, top + (d / N + 0.5f) * cell, cell, false);
            }
        }

        if (mine) art.cursor(c, left + cc * cell, top + cr * cell, cell);   // nothing else moves: no redraw loop
    }
}
