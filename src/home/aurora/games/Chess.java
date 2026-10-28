package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chess with castling, en passant, promotion to any piece, check, checkmate, and draws by
 * stalemate, repetition, the 50-move rule and too little material. Player 1 is White (bottom) and always moves first.
 */
public class Chess extends Game {

    static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;
    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};

    /** Position: piece = color * 8 + type (color 1 White, 2 Black); row 0 is Black's back rank. */
    static final class Pos {
        int[] b = new int[64];
        int side = 1, castle = 15, ep = -1;   // castle bits: 1 WK, 2 WQ, 4 BK, 8 BQ
        int half;                              // moves since the last capture or pawn move, for the 50-move rule

        Pos copy() {
            Pos p = new Pos();
            p.b = b.clone();
            p.side = side;
            p.castle = castle;
            p.ep = ep;
            p.half = half;
            return p;
        }

        /** Same key = same position for repetition: pieces, side to move, castling, and an en passant that could be taken. */
        long key() {
            long h = side * 31L + castle;
            for (int v : b) h = h * 1_000_003L + v;
            if (ep >= 0) {
                int pawn = side * 8 + P, r = ep / 8 + (side == 1 ? 1 : -1), c = ep % 8;
                if ((c > 0 && b[r * 8 + c - 1] == pawn) || (c < 7 && b[r * 8 + c + 1] == pawn)) h = h * 131 + ep + 1;
            }
            return h;
        }
    }

    static final class Move {
        final int from, to, promo;
        Move(int from, int to, int promo) { this.from = from; this.to = to; this.promo = promo; }
    }

    private Pos pos;
    private int cr = 6, cc = 4, sel = -1, lastFrom = -1, lastTo = -1;
    /** The selected piece's legal moves, worked out once when it's picked up, not every frame. */
    private List<Move> selMoves = new ArrayList<>();
    /** Positions so far this game, for threefold repetition. */
    private final List<Long> seen = new ArrayList<>();
    /** A pawn has reached the last row: choosing what it becomes (index into PROMOS), or null. */
    private List<Move> promoting;
    private int promoPick;
    private static final int[] PROMOS = {Q, R, B, N};
    private final BoardArt art = new BoardArt(this);

    public Chess(Context c) {
        super(c);
    }

    @Override public String title() { return "Chess"; }
    @Override public boolean classic() { return true; }
    @Override protected boolean alternateStart() { return false; }

    @Override
    protected void newGame() {
        pos = new Pos();
        int[] back = {R, N, B, Q, K, B, N, R};
        for (int c = 0; c < 8; c++) {
            pos.b[c] = 16 + back[c];
            pos.b[8 + c] = 16 + P;
            pos.b[48 + c] = 8 + P;
            pos.b[56 + c] = 8 + back[c];
        }
        sel = lastFrom = lastTo = -1;
        selMoves = new ArrayList<>();
        promoting = null;
        seen.clear();
        seen.add(pos.key());
        cr = 6; cc = 4;
        if (host != null) host.onInfo("White moves first");
    }

    static int owner(int v) { return v >> 3; }
    static int type(int v) { return v & 7; }

    // ------------------------------------------------------------------ move generation

    private static final int[][] KNIGHT = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    private static final int[][] DIAG = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] ORTHO = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] ALL = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    static List<Move> legal(Pos p) {
        List<Move> out = new ArrayList<>();
        for (Move m : pseudo(p)) {
            Pos n = make(p, m);
            if (!inCheck(n, p.side)) out.add(m);
        }
        return out;
    }

    private static List<Move> pseudo(Pos p) {
        List<Move> out = new ArrayList<>();
        int me = p.side, them = 3 - me;
        for (int sq = 0; sq < 64; sq++) {
            int v = p.b[sq];
            if (owner(v) != me) continue;
            int r = sq / 8, c = sq % 8, t = type(v);
            switch (t) {
                case P: {
                    int dir = me == 1 ? -1 : 1, start = me == 1 ? 6 : 1, last = me == 1 ? 0 : 7;
                    int f = (r + dir) * 8 + c;
                    if (on(r + dir, c) && p.b[f] == 0) {
                        pawnTo(sq, f, r + dir == last, out);
                        int f2 = (r + 2 * dir) * 8 + c;
                        if (r == start && p.b[f2] == 0) out.add(new Move(sq, f2, 0));
                    }
                    for (int dc = -1; dc <= 1; dc += 2) {
                        if (!on(r + dir, c + dc)) continue;
                        int x = (r + dir) * 8 + c + dc;
                        if (owner(p.b[x]) == them || x == p.ep) pawnTo(sq, x, r + dir == last, out);
                    }
                    break;
                }
                case N: steps(p, sq, KNIGHT, false, out); break;
                case B: steps(p, sq, DIAG, true, out); break;
                case R: steps(p, sq, ORTHO, true, out); break;
                case Q: steps(p, sq, ALL, true, out); break;
                case K: {
                    steps(p, sq, ALL, false, out);
                    int home = me == 1 ? 60 : 4;
                    if (sq == home && !attacked(p, home, them)) {
                        int kBit = me == 1 ? 1 : 4, qBit = me == 1 ? 2 : 8;
                        if ((p.castle & kBit) != 0 && p.b[home + 1] == 0 && p.b[home + 2] == 0
                                && type(p.b[home + 3]) == R && !attacked(p, home + 1, them))
                            out.add(new Move(home, home + 2, 0));
                        if ((p.castle & qBit) != 0 && p.b[home - 1] == 0 && p.b[home - 2] == 0 && p.b[home - 3] == 0
                                && type(p.b[home - 4]) == R && !attacked(p, home - 1, them))
                            out.add(new Move(home, home - 2, 0));
                    }
                    break;
                }
            }
        }
        return out;
    }

    /** A pawn move; on the last row, one move per piece it could become. */
    private static void pawnTo(int from, int to, boolean promotes, List<Move> out) {
        if (!promotes) { out.add(new Move(from, to, 0)); return; }
        for (int t : PROMOS) out.add(new Move(from, to, t));
    }

    private static void steps(Pos p, int sq, int[][] dirs, boolean slide, List<Move> out) {
        int me = owner(p.b[sq]);
        for (int[] d : dirs) {
            int r = sq / 8 + d[0], c = sq % 8 + d[1];
            while (on(r, c)) {
                int x = r * 8 + c, o = owner(p.b[x]);
                if (o == me) break;
                out.add(new Move(sq, x, 0));
                if (o != 0 || !slide) break;
                r += d[0];
                c += d[1];
            }
        }
    }

    private static boolean on(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    static boolean attacked(Pos p, int sq, int by) {
        int r = sq / 8, c = sq % 8;
        int pr = by == 1 ? r + 1 : r - 1;   // white pawns attack upward, from the row below
        for (int dc = -1; dc <= 1; dc += 2)
            if (on(pr, c + dc) && p.b[pr * 8 + c + dc] == by * 8 + P) return true;
        for (int[] d : KNIGHT)
            if (on(r + d[0], c + d[1]) && p.b[(r + d[0]) * 8 + c + d[1]] == by * 8 + N) return true;
        for (int[] d : ALL)
            if (on(r + d[0], c + d[1]) && p.b[(r + d[0]) * 8 + c + d[1]] == by * 8 + K) return true;
        for (int[] d : ALL) {
            boolean diag = d[0] != 0 && d[1] != 0;
            int rr = r + d[0], cc = c + d[1];
            while (on(rr, cc)) {
                int v = p.b[rr * 8 + cc];
                if (v != 0) {
                    if (owner(v) == by && (type(v) == Q || type(v) == (diag ? B : R))) return true;
                    break;
                }
                rr += d[0];
                cc += d[1];
            }
        }
        return false;
    }

    static boolean inCheck(Pos p, int side) {
        for (int sq = 0; sq < 64; sq++) if (p.b[sq] == side * 8 + K) return attacked(p, sq, 3 - side);
        return true;
    }

    static Pos make(Pos p, Move m) {
        Pos n = p.copy();
        int v = n.b[m.from], t = type(v), me = owner(v);
        n.b[m.to] = m.promo != 0 ? me * 8 + m.promo : v;
        n.b[m.from] = 0;
        if (t == P && m.to == p.ep) n.b[m.to + (me == 1 ? 8 : -8)] = 0;          // en passant
        if (t == K && Math.abs(m.to - m.from) == 2) {                              // castling: move the rook
            boolean kingSide = m.to > m.from;
            int rookFrom = kingSide ? m.from + 3 : m.from - 4, rookTo = kingSide ? m.from + 1 : m.from - 1;
            n.b[rookTo] = n.b[rookFrom];
            n.b[rookFrom] = 0;
        }
        n.ep = t == P && Math.abs(m.to - m.from) == 16 ? (m.from + m.to) / 2 : -1;
        n.half = t == P || p.b[m.to] != 0 ? 0 : p.half + 1;
        if (t == K) n.castle &= me == 1 ? ~3 : ~12;
        for (int sq : new int[]{m.from, m.to}) {
            if (sq == 63) n.castle &= ~1;
            if (sq == 56) n.castle &= ~2;
            if (sq == 7) n.castle &= ~4;
            if (sq == 0) n.castle &= ~8;
        }
        n.side = 3 - p.side;
        return n;
    }

    /** Nobody can mate: a lone minor piece, or only bishops and all on the same colour of square. */
    private static boolean insufficient(Pos p) {
        int knights = 0, bishops = 0, light = 0;
        for (int sq = 0; sq < 64; sq++) {
            int t = type(p.b[sq]);
            if (t == P || t == R || t == Q) return false;
            if (t == N) knights++;
            if (t == B) { bishops++; if ((sq / 8 + sq % 8) % 2 == 0) light++; }
        }
        if (knights + bishops <= 1) return true;
        return knights == 0 && (light == 0 || light == bishops);
    }

    // ------------------------------------------------------------------ game flow

    private void commit(Move m) {
        pos = make(pos, m);
        if (pos.half == 0) seen.clear();   // a capture or pawn move: nothing before it can come round again
        seen.add(pos.key());
        lastFrom = m.from;
        lastTo = m.to;
        select(-1);
        promoting = null;
        endTurn();
        if (!over) host.onInfo(inCheck(pos, pos.side) ? "Check!" : "");
    }

    @Override
    protected int winner() {
        if (legal(pos).isEmpty()) {
            if (inCheck(pos, pos.side)) { host.onInfo("Checkmate"); return turn; }
            host.onInfo("Stalemate");
            return 0;
        }
        if (insufficient(pos)) { host.onInfo("Not enough pieces left to checkmate"); return 0; }
        if (pos.half >= 100) { host.onInfo("50 moves each with no capture or pawn move"); return 0; }
        long k = pos.key();
        int times = 0;
        for (long s : seen) if (s == k) times++;
        if (times >= 3) { host.onInfo("Same position three times"); return 0; }
        return -1;
    }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        if (promoting != null) {   // the picker is drawn upright: arrows go as you see them
            int was = promoPick;
            promoPick = Math.max(0, Math.min(PROMOS.length - 1, promoPick + dx));
            return promoPick != was;
        }
        cr = Math.max(0, Math.min(7, cr + screenDy(dy)));
        cc = Math.max(0, Math.min(7, cc + screenDx(dx)));
        return true;
    }

    /** Pick up a piece (or put it down with -1) and work out where it can go. */
    private void select(int sq) {
        sel = sq;
        selMoves = new ArrayList<>();
        if (sq < 0) return;
        for (Move m : legal(pos)) if (m.from == sq) selMoves.add(m);
    }

    @Override
    protected boolean playAtCursor() {
        if (promoting != null) {
            for (Move m : promoting) if (m.promo == PROMOS[promoPick]) { commit(m); return true; }
            return false;
        }
        int sq = cr * 8 + cc;
        List<Move> here = new ArrayList<>();
        for (Move m : selMoves) if (m.to == sq) here.add(m);
        if (here.size() == 1) { commit(here.get(0)); return true; }
        if (here.size() > 1) {   // a pawn on the last row: ask what it becomes
            promoting = here;
            promoPick = 0;
            host.onInfo("Promote to  ·  Left / Right picks  ·  OK confirms  ·  Back cancels");
            return true;
        }
        if (sq == sel) { select(-1); return true; }
        if (owner(pos.b[sq]) == turn) {
            select(sq);
            if (!selMoves.isEmpty()) return true;
            select(-1);
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && promoting != null && !over) {
            promoting = null;
            host.onInfo(inCheck(pos, pos.side) ? "Check!" : "");
            invalidate();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    // ------------------------------------------------------------------ computer

    @Override
    protected void cpuMove() {
        final Pos snap = pos.copy();
        final int depth = level == 0 ? 1 : level == 1 ? 2 : 3;
        final boolean loose = level == 0 && Math.random() < 0.3;
        final int g = gen;
        new Thread(() -> {
            List<Move> ms = ordered(snap, legal(snap));
            if (loose) {
                final Move any = ms.get((int) (Math.random() * ms.size()));
                cpuDone(g, () -> commit(any));
                return;
            }
            Move best = null;
            int bestScore = Integer.MIN_VALUE, alpha = -1_000_000;
            for (Move m : ms) {
                int s = -search(make(snap, m), depth - 1, -1_000_000, -alpha);
                if (s > bestScore || (s == bestScore && Math.random() < 0.25)) { bestScore = s; best = m; }
                if (s > alpha) alpha = s;
            }
            final Move pick = best;
            if (pick != null) cpuDone(g, () -> commit(pick));
        }, "chess-ai").start();
    }

    /** Alpha-beta over pseudo-legal moves; a move that leaves the king in check is skipped when it comes up. */
    private static int search(Pos p, int depth, int alpha, int beta) {
        if (depth == 0) {
            if (inCheck(p, p.side) && !hasLegal(p)) return -100_000;
            return quiesce(p, alpha, beta, 4);
        }
        boolean any = false;
        for (Move m : ordered(p, pseudo(p))) {
            Pos n = make(p, m);
            if (inCheck(n, p.side)) continue;
            any = true;
            int s = -search(n, depth - 1, -beta, -alpha);
            if (s > alpha) alpha = s;
            if (alpha >= beta) break;
        }
        if (!any) return inCheck(p, p.side) ? -100_000 - depth : 0;
        return alpha;
    }

    /** At the leaves, play out captures (a few deep) so a hanging piece isn't missed. */
    private static int quiesce(Pos p, int alpha, int beta, int left) {
        int stand = eval(p);
        if (stand >= beta || left == 0) return stand;
        if (stand > alpha) alpha = stand;
        for (Move m : ordered(p, pseudo(p))) {
            boolean noisy = p.b[m.to] != 0 || m.promo == Q || (m.to == p.ep && type(p.b[m.from]) == P);
            if (!noisy || (m.promo != 0 && m.promo != Q)) continue;   // quiet moves and underpromotions wait
            Pos n = make(p, m);
            if (inCheck(n, p.side)) continue;
            int s = -quiesce(n, -beta, -alpha, left - 1);
            if (s > alpha) alpha = s;
            if (alpha >= beta) break;
        }
        return alpha;
    }

    private static boolean hasLegal(Pos p) {
        for (Move m : pseudo(p)) if (!inCheck(make(p, m), p.side)) return true;
        return false;
    }

    /** Captures and queening first, most valuable victim first: prunes much more. */
    private static List<Move> ordered(Pos p, List<Move> ms) {
        List<Move> out = new ArrayList<>(ms);
        Collections.sort(out, (a, b) -> gain(p, b) - gain(p, a));
        return out;
    }

    private static int gain(Pos p, Move m) { return VALUE[type(p.b[m.to])] + VALUE[m.promo]; }

    private static int eval(Pos p) {
        int s = 0;
        for (int sq = 0; sq < 64; sq++) {
            int v = p.b[sq];
            if (v == 0) continue;
            int t = type(v), r = sq / 8, c = sq % 8, val = VALUE[t];
            int center = 3 - Math.max(Math.abs(2 * r - 7), Math.abs(2 * c - 7)) / 2;   // 0 edge .. 3 middle
            if (t == N || t == B) val += center * 8;
            if (t == P) val += (owner(v) == 1 ? 6 - r : r - 1) * 6 + (c >= 2 && c <= 5 ? 6 : 0);
            if (t == Q) val += center * 2;
            s += owner(v) == p.side ? val : -val;
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
        if (promoting != null && !over) drawPromotion(c);
    }

    /** Four pieces to choose from, upright over the middle of the board. */
    private void drawPromotion(Canvas c) {
        float cell = Math.min(getWidth(), getHeight()) * 0.92f / 8 * 1.15f, gap = cell * 0.2f;
        float w = PROMOS.length * cell + (PROMOS.length + 1) * gap, h = cell + 2 * gap;
        float l = (getWidth() - w) / 2, t = (getHeight() - h) / 2;
        panel(c, l, t, l + w, t + h, gap);
        for (int i = 0; i < PROMOS.length; i++) {
            float x = l + gap + i * (cell + gap), y = t + gap;
            boolean inverse = i == promoPick && art.cursorUnder(c, x, y, cell);
            art.chessman(c, x + cell / 2, y + cell / 2, cell, PROMOS[i], turn, inverse);
            if (i == promoPick) art.cursor(c, x, y, cell);
        }
    }

    private void drawBoard(Canvas c) {
        float side = Math.min(getWidth(), getHeight()) * 0.92f, cell = side / 8;
        float left = (getWidth() - side) / 2, top = (getHeight() - side) / 2;
        art.board(c, left, top, side, 8);
        boolean mine = !over && !cpuTurn() && promoting == null;   // the promotion picker has its own cursor
        int checkedKing = -1;
        if (inCheck(pos, pos.side)) for (int sq = 0; sq < 64; sq++) if (pos.b[sq] == pos.side * 8 + K) checkedKing = sq;

        for (int sq = 0; sq < 64; sq++) {
            float x = left + (sq % 8) * cell, y = top + (sq / 8) * cell;
            if (sq == lastFrom || sq == lastTo) art.lastMove(c, x, y, cell, (sq / 8 + sq % 8) % 2 == 1);
            if (sq == checkedKing) art.check(c, x, y, cell);
            if (sq == sel) art.selected(c, x, y, cell);
        }
        int cur = mine ? cr * 8 + cc : -1;
        boolean inverse = mine && art.cursorUnder(c, left + cc * cell, top + cr * cell, cell);

        for (int sq = 0; sq < 64; sq++) {
            int v = pos.b[sq];
            if (v == 0) continue;
            float cx = left + (sq % 8 + 0.5f) * cell, cy = top + (sq / 8 + 0.5f) * cell;
            c.save();
            upright(c, cx, cy);
            art.chessman(c, cx, cy, cell, type(v), owner(v), inverse && sq == cur);
            c.restore();
        }

        if (sel >= 0) for (Move m : selMoves) {
            float cx = left + (m.to % 8 + 0.5f) * cell, cy = top + (m.to / 8 + 0.5f) * cell;
            art.hint(c, cx, cy, cell, pos.b[m.to] != 0);
        }

        if (mine) art.cursor(c, left + cc * cell, top + cr * cell, cell);   // nothing else moves: no redraw loop
    }
}
