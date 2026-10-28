package home.aurora.games;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Klondike solitaire, draw one (easy) or draw three (hard). OK picks up a card (and everything on it), OK
 * again on a destination drops it. OK twice on the same card sends it to the
 * best spot (a foundation if it can). OK on the deck draws. Back cancels.
 * Finishes itself once every card is face up.
 */
public class Solitaire extends Game {

    private static final int RED = 0xFFD01818, BLACK = 0xFF000000;
    private static final String RANKS = "A23456789TJQK";
    private static final int STOCK = 0, WASTE = 1, FOUND = 3;   // top-row columns (2 is a gap)

    private final List<Integer> stock = new ArrayList<>(), waste = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private final List<Integer>[] found = new List[4], tab = new List[7];
    private final int[] down = new int[7];          // face-down cards at the bottom of each column
    private final Random rnd = new Random();
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] suits;

    private int row = 1, col = 0, ti = 0;           // cursor: row 0 top piles, row 1 tableau (ti = card index)
    private int heldRow = -1, heldCol, heldIdx;     // what's picked up
    private int moves;
    private long startedAt;
    private boolean auto;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            report();
            if (!over) postDelayed(this, 1000);
        }
    };

    public Solitaire(Context c) {
        super(c);
        for (int i = 0; i < 4; i++) found[i] = new ArrayList<>();
        for (int i = 0; i < 7; i++) tab[i] = new ArrayList<>();
        txt.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        suits = txt.hasGlyph("♠") ? new String[]{"♠", "♥", "♦", "♣"} : new String[]{"S", "H", "D", "C"};
    }

    @Override public String title() { return "Solitaire"; }
    @Override public boolean solo() { return true; }
    @Override protected boolean alternateStart() { return false; }
    @Override public String hint() { return "OK picks up, OK drops  ·  OK twice auto-moves  ·  Back cancels"; }

    static int suit(int card) { return card / 13; }
    static int rank(int card) { return card % 13; }
    static boolean red(int card) { return suit(card) == 1 || suit(card) == 2; }

    @Override
    protected void newGame() {
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < 52; i++) deck.add(i);
        Collections.shuffle(deck, rnd);
        stock.clear();
        waste.clear();
        for (List<Integer> f : found) f.clear();
        for (int c = 0; c < 7; c++) {
            tab[c].clear();
            for (int k = 0; k <= c; k++) tab[c].add(deck.remove(deck.size() - 1));
            down[c] = c;
        }
        stock.addAll(deck);
        row = 1; col = 0; ti = 0;
        heldRow = -1;
        moves = 0;
        auto = false;
        startedAt = SystemClock.uptimeMillis();
        removeCallbacks(tick);
        post(tick);
        if (host != null) host.onInfo("Build each suit up from Ace to King");
    }

    private void report() {
        if (host == null) return;
        long s = (SystemClock.uptimeMillis() - startedAt) / 1000;
        int best = prefs().getInt(bestKey(), 0);
        host.onScore(clock(s), (best > 0 ? "Best  " + clock(best) : "Best  —") + "   ·   Moves  " + moves);
    }

    private String bestKey() { return level == 2 ? "best_solitaire_s_3" : "best_solitaire_s"; }

    private static String clock(long s) { return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60; }

    // ------------------------------------------------------------------ rules

    private boolean fitsFoundation(int card, int f) {
        List<Integer> p = found[f];
        if (p.isEmpty()) return rank(card) == 0;
        int top = p.get(p.size() - 1);
        return suit(top) == suit(card) && rank(card) == rank(top) + 1;
    }

    private boolean fitsTableau(int card, int c) {
        List<Integer> p = tab[c];
        if (p.isEmpty()) return rank(card) == 12;
        int top = p.get(p.size() - 1);
        return red(top) != red(card) && rank(top) == rank(card) + 1;
    }

    /** The cards currently picked up (bottom first). */
    private List<Integer> held() {
        if (heldRow == 1) return new ArrayList<>(tab[heldCol].subList(heldIdx, tab[heldCol].size()));
        List<Integer> one = new ArrayList<>();
        if (heldRow == 0 && heldCol == WASTE) one.add(waste.get(waste.size() - 1));
        if (heldRow == 0 && heldCol >= FOUND) one.add(found[heldCol - FOUND].get(found[heldCol - FOUND].size() - 1));
        return one;
    }

    private void removeHeld() {
        if (heldRow == 1) {
            tab[heldCol].subList(heldIdx, tab[heldCol].size()).clear();
            List<Integer> p = tab[heldCol];
            if (down[heldCol] >= p.size()) down[heldCol] = Math.max(0, p.size() - 1);   // flip the new top card
        } else if (heldCol == WASTE) waste.remove(waste.size() - 1);
        else found[heldCol - FOUND].remove(found[heldCol - FOUND].size() - 1);
    }

    /** Try to drop what's held on (r, c). */
    private boolean dropOn(int r, int c) {
        List<Integer> cards = held();
        if (cards.isEmpty()) return false;
        int first = cards.get(0);
        if (r == 0 && c >= FOUND) {
            if (cards.size() != 1 || !fitsFoundation(first, c - FOUND)) return false;
            removeHeld();
            found[c - FOUND].add(first);
        } else if (r == 1) {
            if (heldRow == 1 && heldCol == c) return false;
            if (!fitsTableau(first, c)) return false;
            removeHeld();
            tab[c].addAll(cards);
        } else return false;
        heldRow = -1;
        moves++;
        afterMove();
        return true;
    }

    /** OK twice on the held card: foundation if possible, else the first tableau that takes it. */
    private boolean autoMove() {
        List<Integer> cards = held();
        if (cards.size() == 1) for (int f = 0; f < 4; f++) if (fitsFoundation(cards.get(0), f)) return dropOn(0, FOUND + f);
        for (int c = 0; c < 7; c++) if (!(heldRow == 1 && heldCol == c) && fitsTableau(cards.get(0), c)) return dropOn(1, c);
        return false;
    }

    private void draw() {
        if (stock.isEmpty()) {
            for (int i = waste.size() - 1; i >= 0; i--) stock.add(waste.get(i));
            waste.clear();
        } else {
            int n = level == 2 ? 3 : 1;
            for (int i = 0; i < n && !stock.isEmpty(); i++) waste.add(stock.remove(stock.size() - 1));
        }
        moves++;
        afterMove();
    }

    private void afterMove() {
        if (row == 1) ti = Math.max(0, Math.min(ti, tab[col].size() - 1));
        report();
        int done = 0;
        for (List<Integer> f : found) done += f.size();
        if (done == 52) { win(); return; }
        if (!auto && stock.isEmpty() && waste.isEmpty()) {
            boolean allUp = true;
            for (int d : down) if (d > 0) allUp = false;
            if (allUp) { auto = true; host.onInfo("Finishing…"); postDelayed(autoStep, 150); }
        }
    }

    private final Runnable autoStep = new Runnable() {
        @Override public void run() {
            for (int c = 0; c < 7; c++) {
                if (tab[c].isEmpty()) continue;
                int card = tab[c].get(tab[c].size() - 1);
                for (int f = 0; f < 4; f++) if (fitsFoundation(card, f)) {
                    tab[c].remove(tab[c].size() - 1);
                    found[f].add(card);
                    moves++;
                    invalidate();
                    afterMove();
                    if (!over) postDelayed(this, 110);
                    return;
                }
            }
        }
    };

    private void win() {
        removeCallbacks(tick);
        int secs = (int) ((SystemClock.uptimeMillis() - startedAt) / 1000), best = prefs().getInt(bestKey(), 0);
        if (best == 0 || secs < best) prefs().edit().putInt(bestKey(), secs).apply();
        report();
        host.onInfo(best == 0 || secs < best ? "Solved! New best time" : "Solved!");
        finish(1);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && heldRow >= 0) {
            heldRow = -1;
            invalidate();
            return true;
        }
        if (auto && !over) return true;
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        removeCallbacks(autoStep);
        super.onDetachedFromWindow();
    }

    @Override protected void onPaused() { removeCallbacks(tick); }

    @Override
    protected void onResumed(long pausedMs) {
        startedAt += pausedMs;   // time in a menu doesn't count
        removeCallbacks(tick);
        if (!over) post(tick);
    }

    private int firstUp(int c) { return Math.min(down[c], Math.max(0, tab[c].size() - 1)); }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        if (row == 0) {
            if (dx != 0) {
                col += dx;
                if (col == 2) col += dx;
                col = Math.max(0, Math.min(6, col));
            }
            if (dy > 0) { row = 1; ti = Math.max(0, tab[col].size() - 1); }
        } else {
            if (dx != 0) {
                col = Math.max(0, Math.min(6, col + dx));
                ti = Math.max(0, tab[col].size() - 1);
            }
            if (dy < 0) {
                if (ti > firstUp(col)) ti--;
                else { row = 0; if (col == 2) col = WASTE; }
            }
            if (dy > 0 && ti < tab[col].size() - 1) ti++;
        }
        return true;
    }

    @Override
    protected boolean playAtCursor() {
        if (heldRow >= 0) {
            boolean same = row == heldRow && col == heldCol && (row == 0 || ti == heldIdx);
            if (same) { if (!autoMove()) heldRow = -1; return true; }
            if (!dropOn(row, col)) host.onInfo("Can't go there");
            return true;
        }
        if (row == 0) {
            if (col == STOCK) { draw(); return true; }
            if (col == WASTE && !waste.isEmpty()) { heldRow = 0; heldCol = WASTE; return true; }
            if (col >= FOUND && !found[col - FOUND].isEmpty()) { heldRow = 0; heldCol = col; return true; }
            return false;
        }
        if (tab[col].isEmpty() || ti < down[col]) return false;
        heldRow = 1; heldCol = col; heldIdx = ti;
        return true;
    }

    @Override protected void cpuMove() { }
    @Override protected int winner() { return -1; }

    // ------------------------------------------------------------------ drawing
    //
    // The table, the piles and the cards are drawn per look: Classic is Windows 95 Solitaire
    // (green felt, crisp white cards, our own 95-style pixel back, the held card inverted),
    // the others redraw the table, faces, backs and cursor in their own materials.
    // Ranks and suits stay large so the cards read from the sofa.

    private static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface SANS_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface SANS_MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface SERIF_BOLD = Typeface.create("serif", Typeface.BOLD);
    private static final Typeface MONO_BOLD = Typeface.create("monospace", Typeface.BOLD);
    private static final String SUIT_LETTERS = "SHDC";

    /** Suit shapes in a unit box centered on 0,0: spade, heart, diamond, club. */
    private static final Path[] SUIT = new Path[4];
    static {
        Path sp = new Path();
        sp.moveTo(0, -0.5f);
        sp.cubicTo(-0.2f, -0.28f, -0.5f, -0.1f, -0.5f, 0.12f);
        sp.cubicTo(-0.5f, 0.32f, -0.34f, 0.4f, -0.22f, 0.4f);
        sp.cubicTo(-0.12f, 0.4f, -0.05f, 0.35f, -0.03f, 0.28f);
        sp.lineTo(-0.15f, 0.5f);
        sp.lineTo(0.15f, 0.5f);
        sp.lineTo(0.03f, 0.28f);
        sp.cubicTo(0.05f, 0.35f, 0.12f, 0.4f, 0.22f, 0.4f);
        sp.cubicTo(0.34f, 0.4f, 0.5f, 0.32f, 0.5f, 0.12f);
        sp.cubicTo(0.5f, -0.1f, 0.2f, -0.28f, 0, -0.5f);
        sp.close();
        SUIT[0] = sp;
        Path h = new Path();
        h.moveTo(0, 0.48f);
        h.cubicTo(-0.2f, 0.25f, -0.5f, 0.05f, -0.5f, -0.18f);
        h.cubicTo(-0.5f, -0.4f, -0.34f, -0.48f, -0.24f, -0.48f);
        h.cubicTo(-0.1f, -0.48f, -0.02f, -0.38f, 0, -0.28f);
        h.cubicTo(0.02f, -0.38f, 0.1f, -0.48f, 0.24f, -0.48f);
        h.cubicTo(0.34f, -0.48f, 0.5f, -0.4f, 0.5f, -0.18f);
        h.cubicTo(0.5f, 0.05f, 0.2f, 0.25f, 0, 0.48f);
        h.close();
        SUIT[1] = h;
        Path d = new Path();
        d.moveTo(0, -0.5f);
        d.quadTo(0.18f, -0.2f, 0.4f, 0);
        d.quadTo(0.18f, 0.2f, 0, 0.5f);
        d.quadTo(-0.18f, 0.2f, -0.4f, 0);
        d.quadTo(-0.18f, -0.2f, 0, -0.5f);
        d.close();
        SUIT[2] = d;
        Path cl = new Path();
        cl.addCircle(0, -0.25f, 0.215f, Path.Direction.CW);
        cl.addCircle(-0.255f, 0.07f, 0.215f, Path.Direction.CW);
        cl.addCircle(0.255f, 0.07f, 0.215f, Path.Direction.CW);
        cl.addCircle(0, 0.02f, 0.12f, Path.Direction.CW);
        Path stem = new Path();
        stem.moveTo(-0.035f, 0.05f);
        stem.lineTo(-0.16f, 0.5f);
        stem.lineTo(0.16f, 0.5f);
        stem.lineTo(0.035f, 0.05f);
        stem.close();
        cl.addPath(stem);
        SUIT[3] = cl;
    }

    /** Pip positions for 2..10, in a unit box (x 0..1 left to right, y 0..1 top to bottom). */
    private static final float[][] PIPS = {
            {0.5f, 0, 0.5f, 1},
            {0.5f, 0, 0.5f, 0.5f, 0.5f, 1},
            {0, 0, 1, 0, 0, 1, 1, 1},
            {0, 0, 1, 0, 0.5f, 0.5f, 0, 1, 1, 1},
            {0, 0, 1, 0, 0, 0.5f, 1, 0.5f, 0, 1, 1, 1},
            {0, 0, 1, 0, 0.5f, 0.25f, 0, 0.5f, 1, 0.5f, 0, 1, 1, 1},
            {0, 0, 1, 0, 0.5f, 0.25f, 0, 0.5f, 1, 0.5f, 0.5f, 0.75f, 0, 1, 1, 1},
            {0, 0, 1, 0, 0, 1 / 3f, 1, 1 / 3f, 0.5f, 0.5f, 0, 2 / 3f, 1, 2 / 3f, 0, 1, 1, 1},
            {0, 0, 1, 0, 0.5f, 1 / 6f, 0, 1 / 3f, 1, 1 / 3f, 0, 2 / 3f, 1, 2 / 3f, 0.5f, 5 / 6f, 0, 1, 1, 1},
    };

    private final Path tmp = new Path();
    private Bitmap backTile;
    private BitmapShader backShader;
    private float backScale;

    private float cw, ch, left, top, tabTop;

    @Override
    protected void onDraw(Canvas c) {
        GameSkin s = skin();
        boolean win95 = s.bevel;
        fill.setShader(null);
        fill.setAntiAlias(!win95);
        stroke.setAntiAlias(!win95);
        stroke.setStrokeCap(win95 || s.glow ? Paint.Cap.SQUARE : Paint.Cap.ROUND);
        float w = getWidth(), h = getHeight();
        float inPad = s.id.equals("minimal") ? dp(4) : w * 0.022f;
        table(c, s, 0, 0, w, h, inPad);

        float gap = w * 0.018f;
        cw = Math.min((w - inPad * 2 - gap * 6) / 7, (h - inPad * 2) / 4.6f / 1.4f);
        if (win95) cw = (float) Math.floor(cw);
        ch = win95 ? Math.round(cw * 1.4f) : cw * 1.4f;
        left = Math.round((w - (cw * 7 + gap * 6)) / 2);
        top = Math.round(inPad);
        tabTop = Math.round(top + ch + gap * 1.6f);
        float colStep = cw + gap;

        // top row
        slot(c, s, left, top, stock.isEmpty() ? 2 : 0);
        if (!stock.isEmpty()) back(c, s, left, top);
        slot(c, s, left + colStep, top, 0);
        // draw three fans the last three waste cards into the gap; only the top one plays
        int fan = level == 2 ? Math.min(3, waste.size()) : Math.min(1, waste.size());
        for (int i = 0; i < fan; i++) {
            int card = waste.get(waste.size() - fan + i);
            boolean topCard = i == fan - 1;
            face(c, s, left + colStep + i * cw * 0.32f, top, card, topCard && isHeld(0, WASTE));
        }
        for (int f = 0; f < 4; f++) {
            float x = left + colStep * (FOUND + f);
            slot(c, s, x, top, 1);
            if (!found[f].isEmpty()) face(c, s, x, top, found[f].get(found[f].size() - 1), isHeld(0, FOUND + f));
        }

        // tableau
        float avail = h - tabTop - inPad;
        for (int k = 0; k < 7; k++) {
            float x = left + colStep * k;
            List<Integer> p = tab[k];
            slot(c, s, x, tabTop, 0);
            for (int i = 0; i < p.size(); i++) {
                float y = Math.round(cardY(k, i, avail));
                if (i < down[k]) back(c, s, x, y);
                else face(c, s, x, y, p.get(i), heldRow == 1 && heldCol == k && i >= heldIdx);
            }
        }

        // cursor
        if (!over) {
            float x = left + colStep * col, y = row == 0 ? top : cardY(col, Math.min(ti, Math.max(0, tab[col].size() - 1)), avail);
            float bottom = row == 0 || tab[col].isEmpty() ? y + ch : cardY(col, tab[col].size() - 1, avail) + ch;
            if (row == 1 && ti < tab[col].size() - 1) bottom = Math.min(bottom, y + ch);
            cardCursor(c, s, x - dp(5), y - dp(5), x + cw + dp(5), bottom + dp(5));
        }
        fill.setAntiAlias(true);
        stroke.setAntiAlias(true);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setPathEffect(null);
        if (auto) postInvalidateOnAnimation();
    }

    private boolean isHeld(int r, int c) { return heldRow == r && heldCol == c; }

    /** y of card i in column k, squeezing the fan to fit the height. */
    private float cardY(int k, int i, float avail) {
        int n = tab[k].size(), d = Math.min(down[k], n);
        float downStep = ch * 0.12f, upStep = ch * 0.27f;
        int ups = Math.max(0, n - d - 1);
        if (ups > 0) upStep = Math.min(upStep, (avail - ch - d * downStep) / ups);
        return tabTop + Math.min(i, d) * downStep + Math.max(0, i - d) * upStep;
    }

    @Override protected int cursorColor() { return skin().accent; }

    // ------------------------------------------------------------------ table

    private void table(Canvas c, GameSkin s, float l, float t, float r, float b, float pad) {
        float rim = pad * 0.6f;
        switch (s.id) {
            case "classic":
                fill.setColor(0xFF008000);
                c.drawRect(l, t, r, b, fill);
                break;
            case "retro": {
                float rad = pad * 1.4f;
                fill.setShader(new LinearGradient(0, t, 0, b, 0xFF7A4826, 0xFF3E2210, Shader.TileMode.CLAMP));
                rr(c, l, t, r, b, rad, 0);
                fill.setShader(null);
                grain(c, l, t, r, b, 0x16FFE0B0, 31);
                rr(c, l + rim, t + rim, r - rim, b - rim, rad * 0.6f, 0xFF4E6A30);
                fill.setShader(new RadialGradient((l + r) / 2, (t + b) * 0.4f, (r - l) * 0.7f, 0x18FFF0C0, 0x40000000, Shader.TileMode.CLAMP));
                rr(c, l + rim, t + rim, r - rim, b - rim, rad * 0.6f, 0);
                fill.setShader(null);
                stroke.setStrokeWidth(Math.max(2, rim * 0.12f));
                stroke.setColor(0xFFE0A526);
                rf.set(l + rim * 1.35f, t + rim * 1.35f, r - rim * 1.35f, b - rim * 1.35f);
                c.drawRoundRect(rf, rad * 0.5f, rad * 0.5f, stroke);
                break;
            }
            case "art": {
                fill.setShader(new LinearGradient(l, t, l + (r - l) * 0.3f, b, new int[]{0xFFF2D48A, 0xFFB8893A, 0xFFE8C06A, 0xFF8A6420},
                        new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.MIRROR));
                c.drawRect(l, t, r, b, fill);
                fill.setShader(null);
                stroke.setStrokeWidth(Math.max(1, rim * 0.08f));
                stroke.setColor(0xFF5A3E14);
                c.drawRect(l + 1, t + 1, r - 1, b - 1, stroke);
                fill.setColor(0xFF243328);
                c.drawRect(l + rim, t + rim, r - rim, b - rim, fill);
                fill.setShader(new RadialGradient((l + r) / 2, t + (b - t) * 0.25f, (r - l) * 0.65f, 0x30FFF4D8, 0x00FFF4D8, Shader.TileMode.CLAMP));
                c.drawRect(l + rim, t + rim, r - rim, b - rim, fill);
                fill.setShader(null);
                stroke.setColor(0xFF5A3E14);
                c.drawRect(l + rim, t + rim, r - rim, b - rim, stroke);
                break;
            }
            case "space": {
                float rad = pad * 0.5f;
                fill.setShader(new LinearGradient(0, t, 0, b, new int[]{0xFF8A9096, 0xFF4E5359, 0xFF34383D},
                        new float[]{0f, 0.2f, 1f}, Shader.TileMode.CLAMP));
                rr(c, l, t, r, b, rad, 0);
                fill.setShader(null);
                float sr = rim * 0.22f;
                screw(c, l + rim * 0.5f, t + rim * 0.5f, sr);
                screw(c, r - rim * 0.5f, t + rim * 0.5f, sr);
                screw(c, l + rim * 0.5f, b - rim * 0.5f, sr);
                screw(c, r - rim * 0.5f, b - rim * 0.5f, sr);
                rr(c, l + rim - 2, t + rim - 2, r - rim + 2, b - rim + 2, rad * 0.6f, 0xFF1A1D20);
                rr(c, l + rim, t + rim, r - rim, b - rim, rad * 0.5f, 0xFF0C0F12);
                stroke.setStrokeWidth(1);
                stroke.setColor(0x14FFC56A);
                float step = (r - l) / 28f;
                for (float x = l + rim + step; x < r - rim; x += step) c.drawLine(x, t + rim, x, b - rim, stroke);
                for (float y = t + rim + step; y < b - rim; y += step) c.drawLine(l + rim, y, r - rim, y, stroke);
                fill.setShader(new LinearGradient(0, t + rim, 0, (t + b) / 2, 0x10FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawRect(l + rim, t + rim, r - rim, (t + b) / 2, fill);
                fill.setShader(null);
                break;
            }
            case "cozy": {
                float rad = pad * 1.2f;
                fill.setShader(new LinearGradient(l, t, l, b, 0xFFA8703C, 0xFF5E3A1C, Shader.TileMode.CLAMP));
                rr(c, l, t, r, b, rad, 0);
                fill.setShader(null);
                grain(c, l, t, r, b, 0x1A2A1406, 37);
                rr(c, l + rim, t + rim, r - rim, b - rim, rad * 0.6f, 0xFF2E4A30);
                // firelight from the left, falling off into the room
                fill.setShader(new RadialGradient(l + (r - l) * 0.1f, t + (b - t) * 0.3f, (r - l) * 0.9f,
                        new int[]{0x38FFB04A, 0x10FF9A40, 0x40000000}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                rr(c, l + rim, t + rim, r - rim, b - rim, rad * 0.6f, 0);
                fill.setShader(null);
                stroke.setStrokeWidth(Math.max(1, rim * 0.08f));
                stroke.setColor(0x80C8A050);
                rf.set(l + rim, t + rim, r - rim, b - rim);
                c.drawRoundRect(rf, rad * 0.6f, rad * 0.6f, stroke);
                break;
            }
            case "minimal":
                break;
            case "cs": {
                fill.setColor(s.board);
                c.drawRect(l + rim * 0.5f, t + rim * 0.5f, r - rim * 0.5f, b - rim * 0.5f, fill);
                stroke.setStrokeWidth(Math.max(1, dp(1.5f)));
                stroke.setColor(s.frameHi);
                stroke.setShadowLayer(dp(6), 0, 0, alpha(s.frameHi, 0x99));
                c.drawRect(l + rim * 0.5f, t + rim * 0.5f, r - rim * 0.5f, b - rim * 0.5f, stroke);
                stroke.clearShadowLayer();
                String title = " ~/solitaire --draw " + (level == 2 ? 3 : 1) + " ";
                txt.setTypeface(MONO_BOLD);
                txt.setTextSize(rim * 0.9f);
                txt.setTextAlign(Paint.Align.LEFT);
                float tw = txt.measureText(title);
                fill.setColor(s.board);
                c.drawRect(l + pad * 2, t, l + pad * 2 + tw, t + rim, fill);
                txt.setColor(s.ink);
                txt.setShadowLayer(dp(4), 0, 0, alpha(s.accent, 0x99));
                c.drawText(title, l + pad * 2, t + rim * 0.5f - (txt.descent() + txt.ascent()) / 2, txt);
                txt.clearShadowLayer();
                break;
            }
            case "light": {
                float rad = pad * 1.2f;
                fill.setColor(0xFF000000);
                fill.setShadowLayer(pad * 0.8f, 0, pad * 0.25f, 0x22000000);
                rr(c, l + rim, t + rim, r - rim, b - rim, rad, 0xFFEAE6DF);
                fill.clearShadowLayer();
                break;
            }
            default: {
                float rad = pad * 1.2f;
                rr(c, l + rim, t + rim, r - rim, b - rim, rad, 0x2EFFFFFF);
                stroke.setStrokeWidth(dp(1));
                stroke.setColor(0x4DFFFFFF);
                rf.set(l + rim, t + rim, r - rim, b - rim);
                c.drawRoundRect(rf, rad, rad, stroke);
            }
        }
    }

    private void rr(Canvas c, float l, float t, float r, float b, float rad, int color) {
        fill.setColor(fill.getShader() == null ? color : 0xFF000000);
        rf.set(l, t, r, b);
        if (rad <= 0) c.drawRect(rf, fill); else c.drawRoundRect(rf, rad, rad, fill);
    }

    private void grain(Canvas c, float l, float t, float r, float b, int color, long seed) {
        Random g = new Random(seed);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(1, (b - t) * 0.003f));
        c.save();
        c.clipRect(l, t, r, b);
        for (int k = 0; k < 22; k++) {
            float y = t + (b - t) * g.nextFloat(), a = (b - t) * 0.008f * (1 + g.nextFloat());
            tmp.reset();
            tmp.moveTo(l, y);
            for (int j = 1; j <= 6; j++) tmp.quadTo(l + (r - l) * (j - 0.5f) / 6, y + (g.nextFloat() - 0.5f) * a * 4, l + (r - l) * j / 6, y);
            c.drawPath(tmp, stroke);
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

    // ------------------------------------------------------------------ piles

    /** Empty pile; kind 1 is a foundation, 2 the stock waiting to be turned over. */
    private void slot(Canvas c, GameSkin s, float x, float y, int kind) {
        float cx = x + cw / 2, cy = y + ch / 2;
        if (s.bevel) {
            stroke.setStrokeWidth(2);
            stroke.setColor(0xFF004000);
            c.drawRect(x + 1, y + 1, x + cw - 1, y + ch - 1, stroke);
            if (kind == 2) {
                stroke.setAntiAlias(true);
                stroke.setStrokeWidth(Math.max(4, cw * 0.07f));
                stroke.setColor(0xFF00C000);
                c.drawCircle(cx, cy, cw * 0.24f, stroke);
                stroke.setAntiAlias(false);
            }
            return;
        }
        int line;
        Typeface tf = SANS_MED;
        float rad = cardRadius(s);
        switch (s.id) {
            case "retro": line = 0x99F2E6C8; tf = SERIF_BOLD; break;
            case "art": line = 0xCCB8893A; tf = SERIF_BOLD; break;
            case "space": line = 0x99FFC56A; tf = MONO_BOLD; break;
            case "cozy": line = 0x80F6E6CC; tf = SERIF_BOLD; break;
            case "minimal": line = s.boardEdge; tf = SANS; break;
            case "cs": line = s.inkDim; tf = MONO_BOLD; break;
            case "light": line = 0x40000000; break;
            default: line = 0x80FFFFFF;
        }
        rf.set(x, y, x + cw, y + ch);
        if (!s.glow && !s.id.equals("minimal")) {
            fill.setColor(0x1F000000);
            c.drawRoundRect(rf, rad, rad, fill);
        }
        stroke.setStrokeWidth(s.id.equals("minimal") ? 1 : dp(1.5f));
        stroke.setColor(line);
        if (s.glow || s.id.equals("space")) stroke.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(5)}, 0));
        c.drawRoundRect(rf, rad, rad, stroke);
        stroke.setPathEffect(null);
        if (kind == 1) {
            txt.setTypeface(tf);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTextSize(ch * 0.28f);
            txt.setColor(line);
            c.drawText("A", cx, cy - (txt.descent() + txt.ascent()) / 2, txt);
        } else if (kind == 2) {
            // turn the pile over: a circular arrow
            float r = cw * 0.22f;
            stroke.setStrokeWidth(cw * 0.05f);
            rf.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(rf, -60, 290, false, stroke);
            double a = Math.toRadians(-60);
            float ax = cx + (float) Math.cos(a) * r, ay = cy + (float) Math.sin(a) * r;
            tmp.reset();
            tmp.moveTo(ax + r * 0.35f, ay - r * 0.05f);
            tmp.lineTo(ax - r * 0.1f, ay - r * 0.35f);
            tmp.lineTo(ax - r * 0.05f, ay + r * 0.25f);
            tmp.close();
            fill.setColor(line);
            c.drawPath(tmp, fill);
        }
    }

    private float cardRadius(GameSkin s) {
        switch (s.id) {
            case "classic": return Math.max(2, Math.round(cw * 0.04f));
            case "minimal": case "cs": return 0;
            case "art": return cw * 0.04f;
            case "space": return cw * 0.05f;
            case "retro": return cw * 0.1f;
            default: return cw * 0.08f;
        }
    }

    // ------------------------------------------------------------------ backs

    private void back(Canvas c, GameSkin s, float x, float y) {
        float rad = cardRadius(s), r = x + cw, b = y + ch;
        switch (s.id) {
            case "classic": {
                // white card, a navy field with a teal lattice and gold studs, drawn in whole pixels
                fill.setColor(0xFFFFFFFF);
                rf.set(x, y, r, b);
                c.drawRoundRect(rf, rad, rad, fill);
                stroke.setStrokeWidth(1);
                stroke.setColor(0xFF000000);
                rf.set(x + 0.5f, y + 0.5f, r - 0.5f, b - 0.5f);
                c.drawRoundRect(rf, rad, rad, stroke);
                float in = Math.max(4, Math.round(cw * 0.06f));
                fill.setColor(0xFF800000);
                c.drawRect(x + in, y + in, r - in, b - in, fill);
                c.save();
                c.translate(x + in + 2, y + in + 2);
                fill.setShader(win95Back());
                fill.setFilterBitmap(false);
                c.drawRect(0, 0, r - x - in * 2 - 4, b - y - in * 2 - 4, fill);
                fill.setFilterBitmap(true);
                fill.setShader(null);
                c.restore();
                return;
            }
            case "cs": {
                fill.setColor(0xFF03100A);
                c.drawRect(x, y, r, b, fill);
                c.save();
                c.clipRect(x, y, r, b);
                stroke.setStrokeWidth(Math.max(1, cw * 0.012f));
                stroke.setColor(0xFF0E4A20);
                float st = cw * 0.11f;
                for (float d = -ch; d < cw; d += st) {
                    c.drawLine(x + d, y, x + d + ch, b, stroke);
                    c.drawLine(x + d + ch, y, x + d, b, stroke);
                }
                c.restore();
                stroke.setStrokeWidth(Math.max(1, dp(1.5f)));
                stroke.setColor(s.inkDim);
                c.drawRect(x, y, r, b, stroke);
                return;
            }
            case "minimal": {
                fill.setColor(s.ink);
                c.drawRect(x, y, r, b, fill);
                stroke.setStrokeWidth(1);
                stroke.setColor(alpha(s.board, 0x99));
                float in = cw * 0.06f;
                c.drawRect(x + in, y + in, r - in, b - in, stroke);
                stroke.setColor(s.board);
                return;
            }
            default:
        }
        shadowUnder(c, s, x, y, rad);
        int edge, field;
        switch (s.id) {
            case "retro": edge = 0xFFF8F0DC; field = 0xFFC4552A; break;
            case "art": edge = 0xFFF8F2E6; field = 0xFF6E1E22; break;
            case "space": edge = 0xFF8A9096; field = 0xFF23282D; break;
            case "cozy": edge = 0xFFF8EEDC; field = 0xFF8A2A1E; break;
            case "light": edge = 0xFFFFFFFF; field = 0xFF2F6DB8; break;
            default: edge = 0xFFF4F6FA; field = 0xFF2E3C58;
        }
        rf.set(x, y, r, b);
        fill.setColor(edge);
        c.drawRoundRect(rf, rad, rad, fill);
        float in = cw * 0.06f, il = x + in, it = y + in, ir = r - in, ib = b - in, irad = Math.max(0, rad - in * 0.6f);
        rf.set(il, it, ir, ib);
        fill.setColor(field);
        c.drawRoundRect(rf, irad, irad, fill);
        c.save();
        c.clipRect(il, it, ir, ib);
        float cx = (il + ir) / 2, cy = (it + ib) / 2, fw = ir - il, fh = ib - it;
        switch (s.id) {
            case "retro": {
                // 70s stacked stripes in a racetrack
                int[] cols = {0xFF5A3418, 0xFFE0A526, 0xFFF6EBD0, 0xFFC4552A, 0xFF6B7A2A, 0xFFE0A526};
                for (int k = 0; k < cols.length; k++) {
                    float hw = fw * (0.44f - k * 0.07f), hh = fh * (0.44f - k * 0.055f);
                    if (hw <= 0 || hh <= 0) break;
                    rf.set(cx - hw, cy - hh, cx + hw, cy + hh);
                    fill.setColor(cols[k]);
                    c.drawRoundRect(rf, hw, hw, fill);
                }
                break;
            }
            case "art": {
                stroke.setStrokeWidth(Math.max(1, cw * 0.01f));
                stroke.setColor(0x80E2C27A);
                float st = cw * 0.14f;
                for (float d = -fh; d < fw; d += st) {
                    c.drawLine(il + d, it, il + d + fh, ib, stroke);
                    c.drawLine(il + d + fh, it, il + d, ib, stroke);
                }
                stroke.setStrokeWidth(Math.max(1, cw * 0.015f));
                stroke.setColor(0xFFE2C27A);
                float k = cw * 0.04f;
                c.drawRect(il + k, it + k, ir - k, ib - k, stroke);
                // a gilt rosette medallion
                fill.setColor(0xFF6E1E22);
                c.drawCircle(cx, cy, fw * 0.26f, fill);
                c.drawCircle(cx, cy, fw * 0.26f, stroke);
                fill.setColor(0xFFE2C27A);
                for (int p = 0; p < 8; p++) {
                    double a = p * Math.PI / 4;
                    c.drawCircle(cx + (float) Math.cos(a) * fw * 0.14f, cy + (float) Math.sin(a) * fw * 0.14f, fw * 0.055f, fill);
                }
                fill.setColor(0xFFF2D48A);
                c.drawCircle(cx, cy, fw * 0.06f, fill);
                break;
            }
            case "space": {
                stroke.setStrokeWidth(1);
                stroke.setColor(0x40FFC56A);
                float st = fw / 6;
                for (float gx = il + st; gx < ir; gx += st) c.drawLine(gx, it, gx, ib, stroke);
                for (float gy = it + st; gy < ib; gy += st) c.drawLine(il, gy, ir, gy, stroke);
                // mission patch: a ring, an orbit and a craft
                stroke.setStrokeWidth(Math.max(1.5f, cw * 0.018f));
                stroke.setColor(0xFFFFC56A);
                c.drawCircle(cx, cy, fw * 0.3f, stroke);
                rf.set(cx - fw * 0.36f, cy - fw * 0.12f, cx + fw * 0.36f, cy + fw * 0.12f);
                c.save();
                c.rotate(-24, cx, cy);
                c.drawOval(rf, stroke);
                c.restore();
                fill.setColor(0xFFFFC56A);
                c.drawCircle(cx, cy, fw * 0.09f, fill);
                fill.setColor(0xFF6FE08A);
                c.drawCircle(cx + fw * 0.3f, cy - fw * 0.14f, fw * 0.035f, fill);
                break;
            }
            case "cozy": {
                fill.setColor(0x33F8EEDC);
                float st = cw * 0.16f;
                for (float gy = it; gy < ib + st; gy += st * 0.75f) {
                    boolean odd = Math.round((gy - it) / (st * 0.75f)) % 2 == 1;
                    for (float gx = il + (odd ? st / 2 : 0); gx < ir + st; gx += st) {
                        tmp.reset();
                        tmp.moveTo(gx, gy - st * 0.22f);
                        tmp.lineTo(gx + st * 0.16f, gy);
                        tmp.lineTo(gx, gy + st * 0.22f);
                        tmp.lineTo(gx - st * 0.16f, gy);
                        tmp.close();
                        c.drawPath(tmp, fill);
                    }
                }
                stroke.setStrokeWidth(Math.max(1, cw * 0.012f));
                stroke.setColor(0xCCE0B860);
                float k = cw * 0.035f;
                rf.set(il + k, it + k, ir - k, ib - k);
                c.drawRoundRect(rf, irad, irad, stroke);
                break;
            }
            case "light": {
                fill.setShader(new LinearGradient(il, it, ir, ib, 0xFF4A86CC, 0xFF2A5FA4, Shader.TileMode.CLAMP));
                c.drawRect(il, it, ir, ib, fill);
                fill.setShader(null);
                stroke.setStrokeWidth(Math.max(1, cw * 0.008f));
                stroke.setColor(0x33FFFFFF);
                float st = cw * 0.07f;
                for (float d = -fh; d < fw; d += st) c.drawLine(il + d, it, il + d + fh, ib, stroke);
                stroke.setStrokeWidth(Math.max(1, cw * 0.012f));
                stroke.setColor(0xB3FFFFFF);
                float k = cw * 0.035f;
                rf.set(il + k, it + k, ir - k, ib - k);
                c.drawRoundRect(rf, irad, irad, stroke);
                break;
            }
            default: {
                // aurora: slate glass with two soft ribbons of light
                fill.setShader(new LinearGradient(il, it, il, ib, 0xFF3A4A6A, 0xFF1E2840, Shader.TileMode.CLAMP));
                c.drawRect(il, it, ir, ib, fill);
                fill.setShader(null);
                stroke.setStrokeWidth(fw * 0.09f);
                stroke.setColor(0x558FC7FF);
                tmp.reset();
                tmp.moveTo(il - fw * 0.1f, it + fh * 0.62f);
                tmp.cubicTo(il + fw * 0.3f, it + fh * 0.3f, il + fw * 0.6f, it + fh * 0.75f, ir + fw * 0.1f, it + fh * 0.35f);
                c.drawPath(tmp, stroke);
                stroke.setStrokeWidth(fw * 0.05f);
                stroke.setColor(0x4DFFB27A);
                tmp.reset();
                tmp.moveTo(il - fw * 0.1f, it + fh * 0.45f);
                tmp.cubicTo(il + fw * 0.35f, it + fh * 0.2f, il + fw * 0.55f, it + fh * 0.6f, ir + fw * 0.1f, it + fh * 0.22f);
                c.drawPath(tmp, stroke);
            }
        }
        c.restore();
    }

    /** Win95 back: an 8 x 8 pixel tile, scaled up in whole pixels so it stays crisp. */
    private BitmapShader win95Back() {
        float scale = Math.max(2, Math.round(cw / 30f));
        if (backShader == null || scale != backScale) {
            if (backTile == null) {
                backTile = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    int col = 0xFF000080;
                    if ((x + y) % 8 == 0 || (x - y + 8) % 8 == 0) col = 0xFF008080;
                    if ((x == 4 && y == 0) || (x == 0 && y == 4)) col = 0xFFFFFF00;
                    if ((x == 4 && (y == 1 || y == 7)) || (y == 4 && (x == 1 || x == 7))) col = 0xFF0000A8;
                    backTile.setPixel(x, y, col);
                }
            }
            backShader = new BitmapShader(backTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            backShader.setLocalMatrix(m);
            backScale = scale;
        }
        return backShader;
    }

    private void shadowUnder(Canvas c, GameSkin s, float x, float y, float rad) {
        if (s.bevel || s.glow || s.id.equals("minimal")) return;
        float o = Math.max(1, cw * 0.018f);
        rf.set(x - o * 0.3f, y + o * 0.2f, x + cw + o * 0.3f, y + ch + o * 1.6f);
        fill.setColor(s.id.equals("light") ? 0x1F000000 : 0x40000000);
        c.drawRoundRect(rf, rad + o, rad + o, fill);
    }

    // ------------------------------------------------------------------ faces

    private static int inv(int c) { return (c & 0xFF000000) | (~c & 0xFFFFFF); }

    private void face(Canvas c, GameSkin s, float x, float y, int card, boolean held) {
        if (s.glow) { termFace(c, s, x, y, card, held); return; }
        boolean win95 = s.bevel;
        float rad = cardRadius(s);
        int faceCol, edge, red, black, tint;
        Typeface tf;
        switch (s.id) {
            case "classic": faceCol = 0xFFFFFFFF; edge = 0xFF000000; red = 0xFFFF0000; black = 0xFF000000; tint = 0xFFFFF4B0; tf = SANS_BOLD; break;
            case "retro": faceCol = 0xFFF8F0DC; edge = 0xFFB9A57E; red = 0xFFB4361E; black = 0xFF2B1A10; tint = 0xFFF2D9A0; tf = SERIF_BOLD; break;
            case "art": faceCol = 0xFFF8F2E6; edge = 0xFFB8893A; red = 0xFF9A2A22; black = 0xFF1C1612; tint = 0xFFEFE2C4; tf = SERIF_BOLD; break;
            case "space": faceCol = 0xFFE4E7EA; edge = 0xFF8A9096; red = 0xFFC8481E; black = 0xFF1A1D20; tint = 0xFFD2D8DE; tf = MONO_BOLD; break;
            case "cozy": faceCol = 0xFFF8EEDC; edge = 0xFFC8B08A; red = 0xFFB4361E; black = 0xFF2A1A10; tint = 0xFFF0DDB8; tf = SERIF_BOLD; break;
            case "minimal": faceCol = s.cardFace; edge = s.ink; red = 0xFFB4361E; black = s.ink; tint = faceCol; tf = SANS; break;
            case "light": faceCol = 0xFFFFFFFF; edge = 0x1F000000; red = 0xFFD8402F; black = 0xFF1C1C1E; tint = 0xFFF2EEE6; tf = SANS_MED; break;
            default: faceCol = 0xFFFFFFFF; edge = 0x1F000000; red = 0xFFD8402F; black = 0xFF1C1C1E; tint = 0xFFF0F2F6; tf = SANS_MED; break;
        }
        if (held && win95) {
            // Windows 95 shows the picked-up card in inverted colors
            faceCol = inv(faceCol); edge = inv(edge); red = inv(red); black = inv(black); tint = inv(tint);
        }
        if (held && !win95) {
            y -= ch * 0.04f;
            int col = s.id.equals("aurora") ? 0xFFFFFFFF : s.accent;
            rf.set(x - dp(4), y - dp(4), x + cw + dp(4), y + ch + dp(4));
            fill.setColor(alpha(col, 0xE6));
            if (!s.id.equals("minimal")) fill.setShadowLayer(dp(10), 0, 0, alpha(col, 0xAA));
            c.drawRoundRect(rf, rad + dp(4), rad + dp(4), fill);
            fill.clearShadowLayer();
        } else shadowUnder(c, s, x, y, rad);

        rf.set(x, y, x + cw, y + ch);
        fill.setColor(faceCol);
        c.drawRoundRect(rf, rad, rad, fill);
        stroke.setColor(edge);
        if (win95) {
            stroke.setStrokeWidth(1);
            rf.set(x + 0.5f, y + 0.5f, x + cw - 0.5f, y + ch - 0.5f);
            c.drawRoundRect(rf, rad, rad, stroke);
        } else {
            stroke.setStrokeWidth(s.id.equals("art") ? Math.max(2, cw * 0.02f) : s.id.equals("minimal") ? 1 : dp(1));
            c.drawRoundRect(rf, rad, rad, stroke);
        }
        if (s.id.equals("art")) {
            // the gilt edge: a second fine gold rule inside
            stroke.setStrokeWidth(Math.max(1, cw * 0.01f));
            stroke.setColor(0xFFC9A24A);
            float k = cw * 0.04f;
            rf.set(x + k, y + k, x + cw - k, y + ch - k);
            c.drawRoundRect(rf, rad * 0.5f, rad * 0.5f, stroke);
        }

        fill.setAntiAlias(true);
        int col = red(card) ? red : black;
        int rk = rank(card), su = suit(card);
        // the index: rank top-left, suit top-right, both large
        txt.setTypeface(tf);
        txt.setColor(col);
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTextSize(cw * (s.id.equals("space") ? 0.3f : 0.33f));
        String r = rk == 9 ? "10" : String.valueOf(RANKS.charAt(rk));
        txt.setLetterSpacing(rk == 9 ? -0.06f : 0f);
        c.drawText(r, x + cw * 0.08f, y + cw * 0.33f, txt);
        txt.setLetterSpacing(0f);
        pip(c, su, x + cw * 0.79f, y + cw * 0.2f, cw * 0.25f, col, false);

        if (s.id.equals("minimal")) {
            pip(c, su, x + cw / 2, y + ch * 0.62f, cw * 0.38f, col, false);
        } else if (rk == 0) {
            pip(c, su, x + cw / 2, y + ch * 0.62f, cw * 0.44f, col, false);
            if (su == 0 && !win95) {
                fill.setColor(faceCol);
                c.drawCircle(x + cw / 2, y + ch * 0.6f, cw * 0.05f, fill);
            }
        } else if (rk <= 9) {
            float[] pp = PIPS[rk - 1];
            float bl = x + cw * 0.3f, bw = cw * 0.4f, bt = y + cw * 0.56f, bh = ch - cw * 0.17f - (cw * 0.56f);
            for (int k = 0; k < pp.length; k += 2) {
                float py = pp[k + 1];
                pip(c, su, bl + pp[k] * bw, bt + py * bh, cw * (rk >= 8 ? 0.165f : 0.19f), col, py > 0.5f);
            }
        } else {
            courtCard(c, s, x, y, rk, su, col, tint, faceCol, tf);
        }
        if (win95) fill.setAntiAlias(false);
    }

    private void pip(Canvas c, int suit, float cx, float cy, float size, int col, boolean flip) {
        c.save();
        c.translate(cx, cy);
        if (flip) c.rotate(180);
        c.scale(size, size);
        fill.setColor(col);
        c.drawPath(SUIT[suit], fill);
        c.restore();
    }

    /** J, Q, K: a framed panel with a crown (K), a coronet (Q) or a cap (J) over a big letter. */
    private void courtCard(Canvas c, GameSkin s, float x, float y, int rk, int su, int col, int tint, int faceCol, Typeface tf) {
        float l = x + cw * 0.17f, t = y + cw * 0.46f, r = x + cw * 0.83f, b = y + ch - cw * 0.09f;
        fill.setColor(tint);
        c.drawRect(l, t, r, b, fill);
        stroke.setStrokeWidth(Math.max(2, cw * 0.018f));
        stroke.setColor(col);
        c.drawRect(l, t, r, b, stroke);
        float cx = (l + r) / 2, w = r - l, hh = b - t;
        // headwear in gold, outlined in the suit color
        int gold = s.bevel ? 0xFFFFFF00 : 0xFFE0A526;
        float hy = t + hh * 0.3f, hw = w * 0.32f;
        tmp.reset();
        if (rk == 12) {          // king's crown: three points
            tmp.moveTo(cx - hw, hy);
            tmp.lineTo(cx - hw, hy - hh * 0.16f);
            tmp.lineTo(cx - hw * 0.5f, hy - hh * 0.07f);
            tmp.lineTo(cx, hy - hh * 0.2f);
            tmp.lineTo(cx + hw * 0.5f, hy - hh * 0.07f);
            tmp.lineTo(cx + hw, hy - hh * 0.16f);
            tmp.lineTo(cx + hw, hy);
            tmp.close();
        } else if (rk == 11) {   // queen's coronet: a rounded band with a peak
            tmp.moveTo(cx - hw, hy);
            tmp.quadTo(cx - hw * 0.9f, hy - hh * 0.12f, cx - hw * 0.45f, hy - hh * 0.1f);
            tmp.lineTo(cx, hy - hh * 0.18f);
            tmp.lineTo(cx + hw * 0.45f, hy - hh * 0.1f);
            tmp.quadTo(cx + hw * 0.9f, hy - hh * 0.12f, cx + hw, hy);
            tmp.close();
        } else {                 // jack's cap: a round dome with a brim, and a feather
            tmp.moveTo(cx - hw * 0.85f, hy);
            tmp.cubicTo(cx - hw * 0.8f, hy - hh * 0.24f, cx + hw * 0.8f, hy - hh * 0.24f, cx + hw * 0.85f, hy);
            tmp.close();
            stroke.setStrokeWidth(Math.max(2, cw * 0.02f));
            c.drawLine(cx + hw * 0.3f, hy - hh * 0.13f, cx + hw * 1.05f, hy - hh * 0.27f, stroke);
        }
        fill.setColor(gold);
        c.drawPath(tmp, fill);
        stroke.setStrokeWidth(Math.max(1.5f, cw * 0.012f));
        c.drawPath(tmp, stroke);
        txt.setTypeface(s.id.equals("space") ? MONO_BOLD : SERIF_BOLD);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(w * 0.62f);
        txt.setColor(col);
        c.drawText(String.valueOf(RANKS.charAt(rk)), cx, t + hh * 0.66f - (txt.descent() + txt.ascent()) / 2, txt);
        pip(c, su, l + w * 0.15f, b - hh * 0.1f, w * 0.17f, col, false);
        pip(c, su, r - w * 0.15f, b - hh * 0.1f, w * 0.17f, col, false);
    }

    /** Computer Science: a text box with the rank and suit letter; red suits in inverse video. */
    private void termFace(Canvas c, GameSkin s, float x, float y, int card, boolean held) {
        int bg = held ? s.accent : 0xFF041A0C, fg = held ? s.onAccent : s.accent;
        fill.setColor(bg);
        if (held) fill.setShadowLayer(dp(8), 0, 0, alpha(s.accent, 0x99));
        c.drawRect(x, y, x + cw, y + ch, fill);
        fill.clearShadowLayer();
        stroke.setStrokeWidth(Math.max(1, dp(1.5f)));
        stroke.setColor(held ? s.accent : s.inkDim);
        c.drawRect(x, y, x + cw, y + ch, stroke);
        int rk = rank(card);
        String r = rk == 9 ? "10" : String.valueOf(RANKS.charAt(rk));
        String su = String.valueOf(SUIT_LETTERS.charAt(suit(card)));
        boolean redSuit = red(card);
        txt.setTypeface(MONO_BOLD);
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTextSize(cw * 0.32f);
        float base = y + cw * 0.32f;
        float tw = txt.measureText(r + su) + cw * 0.06f;
        if (redSuit) {
            // red cards: the whole tag in inverse video so they can't be mistaken for black ones
            fill.setColor(held ? s.onAccent : s.ink);
            c.drawRect(x + cw * 0.05f, y + cw * 0.05f, x + cw * 0.05f + tw, y + cw * 0.4f, fill);
            txt.setColor(held ? s.accent : 0xFF02140A);
        } else {
            txt.setColor(fg);
            if (!held) txt.setShadowLayer(dp(4), 0, 0, alpha(s.accent, 0x99));
        }
        c.drawText(r + su, x + cw * 0.08f, base, txt);
        txt.clearShadowLayer();
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTextSize(cw * 0.5f);
        txt.setColor(held ? alpha(s.onAccent, 0x99) : alpha(redSuit ? s.ink : s.accent, 0x80));
        String big = redSuit ? "<" + su + ">" : "[" + su + "]";
        c.drawText(big, x + cw / 2, y + ch * 0.66f - (txt.descent() + txt.ascent()) / 2, txt);
    }

    // ------------------------------------------------------------------ cursor

    private void cardCursor(Canvas c, GameSkin s, float l, float t, float r, float b) {
        float p = pulse();
        switch (s.id) {
            case "classic": {
                // a black frame with a white dotted focus rectangle over it, square, whole pixels
                l = Math.round(l); t = Math.round(t); r = Math.round(r); b = Math.round(b);
                stroke.setStrokeWidth(4);
                stroke.setColor(alpha(0xFF000000, (int) (180 + 75 * p)));
                c.drawRect(l, t, r, b, stroke);
                stroke.setColor(0xFFFFFFFF);
                stroke.setPathEffect(new DashPathEffect(new float[]{4, 4}, 0));
                c.drawRect(l, t, r, b, stroke);
                stroke.setPathEffect(null);
                return;
            }
            case "space": {
                stroke.setStrokeWidth(dp(3));
                stroke.setColor(alpha(s.accent, (int) (170 + 85 * p)));
                stroke.setShadowLayer(dp(6), 0, 0, alpha(s.accent, 0x99));
                float k = cw * 0.25f;
                tmp.reset();
                tmp.moveTo(l, t + k); tmp.lineTo(l, t); tmp.lineTo(l + k, t);
                tmp.moveTo(r - k, t); tmp.lineTo(r, t); tmp.lineTo(r, t + k);
                tmp.moveTo(r, b - k); tmp.lineTo(r, b); tmp.lineTo(r - k, b);
                tmp.moveTo(l + k, b); tmp.lineTo(l, b); tmp.lineTo(l, b - k);
                c.drawPath(tmp, stroke);
                stroke.clearShadowLayer();
                return;
            }
            default: {
                int col = s.id.equals("aurora") ? 0xFFFFFFFF : s.accent;
                float rad = s.glow || s.id.equals("minimal") ? 0 : cardRadius(s) + dp(5);
                stroke.setStrokeWidth(dp(3.5f));
                stroke.setColor(alpha(col, (int) (170 + 85 * p)));
                if (!s.id.equals("minimal")) stroke.setShadowLayer(dp(8), 0, 0, alpha(col, 0x88));
                rf.set(l, t, r, b);
                c.drawRoundRect(rf, rad, rad, stroke);
                stroke.clearShadowLayer();
            }
        }
    }
}
