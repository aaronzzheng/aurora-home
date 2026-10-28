package home.aurora.games;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;

/**
 * Base for the pass-the-remote board games: D-pad cursor, OK to play, turn
 * handling, an optional computer for player 2, and shared glowing drawing helpers.
 */
public abstract class Game extends View {

    public interface Host {
        /** Whose turn it is (1 or 2). */
        void onTurn(int player);
        /** Game finished: 1 or 2 won, 0 is a draw. */
        void onOver(int winner);
        /** Extra live line, e.g. piece counts. */
        void onInfo(String info);
        /** Solo games: current score/time and personal best. */
        void onScore(String score, String best);
    }

    /** The look every game draws with: the home screen's theme (set by the games screen). */
    public static GameSkin skin;

    protected Host host;
    protected boolean vsCpu;
    /** 0 easy, 1 medium, 2 hard: computer strength or puzzle size, per game. */
    protected int level = 1;
    protected int turn = 1;
    /** Whose side is drawn at the bottom; boards that turn for 2 players follow the turn. */
    protected int facing = 1;
    protected boolean over;
    private int firstPlayer = 1;
    private boolean cpuPending;
    /** Bumped by a new round, a pause or leaving the screen: a computer move worked out before that is dropped. */
    protected int gen;
    private int pauses;
    private long pausedAt;
    /** The board is fading round to the other player; arrows would go the wrong way. */
    private boolean flipping;

    protected final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    protected final RectF rf = new RectF();

    public Game(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    public abstract String title();

    /** The current look; the Aurora one if the games screen hasn't set it. */
    protected GameSkin skin() {
        if (skin == null) skin = GameSkin.forTheme(getContext(), "aurora", false);
        return skin;
    }

    /** Color of the pulsing cursor frame. */
    protected int cursorColor() { return skin().accent; }

    /** The look's frame around a board: walnut, gilt, brushed steel, a phosphor rule… */
    protected void woodFrame(Canvas c, float l, float t, float r, float b, float thick) {
        GameSkin s = skin();
        float rad = s.bevel || s.glow ? 0 : thick * 0.5f;
        rf.set(l - thick, t - thick, r + thick, b + thick);
        fill.setColor(0xFF000000);
        if (s.glow) {
            fill.setShader(null);
            fill.setColor(s.board);
            c.drawRect(rf, fill);
            stroke.setStrokeWidth(Math.max(1f, dp(1.5f)));
            stroke.setColor(s.frameHi);
            stroke.setShadowLayer(dp(6), 0, 0, alpha(s.frameHi, 0x99));
            c.drawRect(rf, stroke);
            stroke.clearShadowLayer();
            return;
        }
        fill.setShader(new LinearGradient(0, t - thick, 0, b + thick, s.frameHi, s.frame, Shader.TileMode.CLAMP));
        c.drawRoundRect(rf, rad, rad, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(Math.max(1f, thick * 0.08f));
        stroke.setColor(s.bevel ? 0xFFFFFFFF : 0x40FFFFFF);
        c.drawRoundRect(rf, rad, rad, stroke);
        stroke.setColor(s.bevel ? 0xFF808080 : 0x80000000);
        rf.set(l - 1, t - 1, r + 1, b + 1);
        c.drawRect(rf, stroke);
    }

    /** Classic black-and-white pieces (Chess, Checkers) instead of cyan and pink. */
    public boolean classic() { return false; }

    /** Color for a player's text and dot in the side panel. */
    public int uiColor(int p) {
        if (!classic()) return color(p);
        if (skin().bevel) return p == 1 ? 0xFF000000 : 0xFF404040;   // black text inside the 95 window
        return p == 1 ? skin().ink : skin().inkDim;
    }

    /** "White"/"Black" for classic games; null means "Player 1"/"Player 2". */
    public String sideName(int p) { return classic() ? (p == 1 ? "White" : "Black") : null; }

    /** Highlights (possible moves etc.); classic wooden boards use soft dark marks. */
    protected int hintColor(int p) { return classic() ? skin().accent : color(p); }

    /** Single-player game: no turns, side panel shows score and best instead of players. */
    public boolean solo() { return false; }

    /** Controls line for the side panel; null uses the default. */
    public String hint() { return null; }

    /** Solo games end here: 1 = won, 2 = lost, 0 = just over. */
    protected void finish(int result) {
        over = true;
        host.onOver(result);
        invalidate();
    }

    protected SharedPreferences prefs() {
        return getContext().getSharedPreferences("aurora", Context.MODE_PRIVATE);
    }

    public void setLevel(int l) { level = l; }

    public void start(Host h, boolean cpu) {
        host = h;
        vsCpu = cpu;
        firstPlayer = 1;
        restart();
    }

    /** New round; whoever didn't start last time goes first. */
    public void restart() {
        over = false;
        cpuPending = false;
        removeCallbacks(cpuRunnable);
        gen++;
        if (pauses > 0) pausedAt = SystemClock.uptimeMillis();   // the new round's clock starts now
        turn = alternateStart() ? firstPlayer : 1;
        firstPlayer = 3 - firstPlayer;
        animate().cancel();
        flipping = false;
        setAlpha(1f);
        facing = turnsBoard() && !vsCpu ? turn : 1;
        newGame();
        host.onTurn(turn);
        maybeCpu();
        invalidate();
    }

    protected abstract void newGame();
    /** Move the cursor; return true if it moved. */
    protected abstract boolean moveCursor(int dx, int dy);
    /** Try to play at the cursor; return true if a move was made. */
    protected abstract boolean playAtCursor();
    /** Computer move for player 2. */
    protected abstract void cpuMove();
    /** -1 still playing, 0 draw, else the winner. */
    protected abstract int winner();

    /** Call after a move is complete. */
    protected void endTurn() {
        int w = winner();
        if (w >= 0) {
            over = true;
            host.onOver(w);
        } else {
            turn = nextPlayer();
            host.onTurn(turn);
            maybeCpu();
            if (turnsBoard() && !vsCpu && facing != turn) {
                // fade out, turn the board to the new player, fade back in; no input until it has turned
                flipping = true;
                animate().alpha(0f).setDuration(170).withEndAction(() -> {
                    facing = turn;
                    flipping = false;
                    invalidate();
                    animate().alpha(1f).setDuration(230).start();
                }).start();
            }
        }
        invalidate();
    }

    /** Whether the starting player swaps each round (Chess keeps White = player 1 first). */
    protected boolean alternateStart() { return true; }

    /** Chess and Checkers: with 2 players the board turns so the player to move is at the bottom. */
    protected boolean turnsBoard() { return false; }

    /** Draw the board turned around when the top player is to move. */
    protected void beginBoard(Canvas c) {
        c.save();
        if (facing == 2) c.rotate(180, getWidth() / 2f, getHeight() / 2f);
    }

    /** Keep a piece upright on a turned board (wrap its drawing in save/restore). */
    protected void upright(Canvas c, float cx, float cy) {
        if (facing == 2) c.rotate(180, cx, cy);
    }

    /** Arrow keys follow what you see. */
    protected int screenDx(int dx) { return facing == 2 ? -dx : dx; }
    protected int screenDy(int dy) { return facing == 2 ? -dy : dy; }

    /** Who plays next; games with passes/extra turns override. */
    protected int nextPlayer() { return 3 - turn; }

    protected boolean cpuTurn() { return vsCpu && turn == 2 && !over; }

    private final Runnable cpuRunnable = () -> {
        cpuPending = false;
        if (cpuTurn() && pauses == 0) cpuMove();
    };

    protected void maybeCpu() {
        if (cpuTurn() && !cpuPending && pauses == 0) {
            cpuPending = true;
            postDelayed(cpuRunnable, 650);
        }
    }

    /**
     * For a computer move worked out on another thread: plays it back on the UI thread,
     * unless a new round, a pause or leaving the screen came in between ({@code g} is {@link #gen} when it started).
     */
    protected void cpuDone(int g, Runnable play) {
        post(() -> { if (g == gen && cpuTurn()) play.run(); });
    }

    /** A menu or another app is over the game: stop the clock and hold the computer's move. Calls nest. */
    public void pause() {
        if (pauses++ > 0) return;
        pausedAt = SystemClock.uptimeMillis();
        removeCallbacks(cpuRunnable);
        cpuPending = false;
        gen++;
        onPaused();
    }

    /** Undoes one {@link #pause()}; the last one restarts the clock and lets the computer move. */
    public void resume() {
        if (pauses == 0 || --pauses > 0) return;
        onResumed(SystemClock.uptimeMillis() - pausedAt);
        maybeCpu();
        invalidate();
    }

    /** Timed games stop their tick here. */
    protected void onPaused() { }

    /** Timed games move their start time on by {@code pausedMs} so the pause doesn't count. */
    protected void onResumed(long pausedMs) { }

    @Override
    protected void onDetachedFromWindow() {
        gen++;
        removeCallbacks(cpuRunnable);
        removeCallbacks(pulseTick);
        cpuPending = false;
        pulsing = false;
        super.onDetachedFromWindow();
    }

    /** Subclasses mid-animation return true to ignore input. */
    protected boolean busy() { return false; }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (over) {
            if (ok) { home.aurora.Sounds.select(getContext()); restart(); return true; }
            return super.onKeyDown(code, e);
        }
        if (cpuTurn() || busy() || flipping) return ok || isDpad(code) || super.onKeyDown(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return moved(moveCursor(-1, 0));
            case KeyEvent.KEYCODE_DPAD_RIGHT: return moved(moveCursor(1, 0));
            case KeyEvent.KEYCODE_DPAD_UP: return moved(moveCursor(0, -1));
            case KeyEvent.KEYCODE_DPAD_DOWN: return moved(moveCursor(0, 1));
        }
        if (ok) {
            if (playAtCursor()) {
                home.aurora.Sounds.select(getContext());
                invalidate();
            }
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private boolean moved(boolean did) {
        if (did) home.aurora.Sounds.click(getContext());
        invalidate();
        return true;
    }

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    // ------------------------------------------------------------------ drawing helpers

    protected float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    protected int color(int player) { return player == 1 ? skin().p1 : skin().p2; }

    protected static int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    private boolean pulsing;
    private final Runnable pulseTick = () -> { pulsing = false; invalidate(); };

    /** 0..1..0 every ~1.2 s, for the cursor. Redraws about 18 times a second while a cursor shows, not every frame. */
    protected float pulse() {
        if (!pulsing) { pulsing = true; postDelayed(pulseTick, 55); }
        double t = (SystemClock.uptimeMillis() % 1200) / 1200.0;
        return (float) (0.5 - 0.5 * Math.cos(t * 2 * Math.PI));
    }

    /** A player's disc: glowing on glass and screens, a solid painted counter elsewhere. */
    protected void disc(Canvas c, float cx, float cy, float r, int player, int alpha) {
        fill.setColor(0xFF000000);   // a shader is still multiplied by the paint's own alpha; reset it
        if (classic()) { classicDisc(c, cx, cy, r, player, alpha); return; }
        int col = color(player);
        GameSkin s = skin();
        if (s.glow) {
            fill.setShader(null);
            stroke.setStrokeWidth(r * 0.18f);
            stroke.setColor(alpha(col, alpha));
            stroke.setShadowLayer(r * 0.5f, 0, 0, alpha(col, alpha / 2));
            c.drawCircle(cx, cy, r * 0.8f, stroke);
            stroke.clearShadowLayer();
            fill.setColor(alpha(col, alpha / 4));
            c.drawCircle(cx, cy, r * 0.55f, fill);
            return;
        }
        if (!s.id.equals("aurora") && !s.id.equals("space")) {
            // a painted counter with a soft shadow and a sheen
            fill.setShader(new RadialGradient(cx, cy + r * 0.12f, r * 1.25f,
                    new int[]{alpha(0xFF000000, (int) (alpha * 0.45f)), 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy + r * 0.12f, r * 1.25f, fill);
            fill.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.45f,
                    new int[]{alpha(lighten(col), alpha), alpha(col, alpha), alpha(darken(col), alpha)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, fill);
            fill.setShader(null);
            stroke.setStrokeWidth(Math.max(1f, r * 0.06f));
            stroke.setColor(alpha(darken(col), alpha));
            c.drawCircle(cx, cy, r * 0.74f, stroke);
            return;
        }
        fill.setShader(new RadialGradient(cx, cy, r * 1.6f,
                new int[]{alpha(col, (int) (alpha * 0.45f)), alpha(col, 0)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.6f, fill);
        fill.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 1.3f,
                new int[]{alpha(0xFFFFFFFF, alpha), alpha(col, alpha), alpha(darken(col), alpha)},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);
    }

    /** The look's light or dark checker with a soft shadow and a beveled edge. */
    private void classicDisc(Canvas c, float cx, float cy, float r, int player, int alpha) {
        GameSkin s = skin();
        int col = player == 1 ? s.pieceLight : s.pieceDark;
        if (s.glow) {
            stroke.setStrokeWidth(r * 0.14f);
            stroke.setColor(alpha(col, alpha));
            stroke.setShadowLayer(r * 0.45f, 0, 0, alpha(col, alpha / 2));
            c.drawCircle(cx, cy, r * 0.82f, stroke);
            c.drawCircle(cx, cy, r * 0.5f, stroke);
            stroke.clearShadowLayer();
            if (player == 1) { fill.setShader(null); fill.setColor(alpha(col, alpha / 3)); c.drawCircle(cx, cy, r * 0.5f, fill); }
            return;
        }
        fill.setShader(new RadialGradient(cx, cy + r * 0.12f, r * 1.25f,
                new int[]{alpha(0xFF000000, (int) (alpha * 0.55f)), 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy + r * 0.12f, r * 1.25f, fill);
        fill.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.45f,
                new int[]{alpha(lighten(col), alpha), alpha(col, alpha), alpha(shade(col), alpha)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(Math.max(1f, r * 0.06f));
        stroke.setColor(alpha(shade(col), alpha));
        c.drawCircle(cx, cy, r * 0.72f, stroke);   // the ridge on a real checker
        stroke.setColor(alpha(s.pieceEdge, alpha));
        c.drawCircle(cx, cy, r, stroke);
    }

    protected static int darken(int c) {
        return 0xFF000000 | ((((c >> 16) & 0xFF) / 3) << 16) | ((((c >> 8) & 0xFF) / 3) << 8) | ((c & 0xFF) / 3);
    }

    /** A gentler darkening, for the shaded side of a piece. */
    protected static int shade(int c) {
        return 0xFF000000 | (((c >> 16) & 0xFF) * 7 / 10 << 16) | (((c >> 8) & 0xFF) * 7 / 10 << 8) | ((c & 0xFF) * 7 / 10);
    }

    protected static int lighten(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | ((r + (255 - r) * 2 / 5) << 16) | ((g + (255 - g) * 2 / 5) << 8) | (b + (255 - b) * 2 / 5);
    }

    /** The look's panel behind a board: frosted glass, cream enamel, a green rule… */
    protected void panel(Canvas c, float l, float t, float r, float b, float radius) {
        GameSkin s = skin();
        float rad = s.bevel || s.glow ? 0 : radius;
        rf.set(l, t, r, b);
        fill.setShader(null);
        fill.setColor(s.board);
        c.drawRoundRect(rf, rad, rad, fill);
        stroke.setStrokeWidth(dp(s.glow ? 1.5f : 1));
        stroke.setColor(s.boardEdge);
        if (s.glow) stroke.setShadowLayer(dp(6), 0, 0, alpha(s.boardEdge, 0x99));
        c.drawRoundRect(rf, rad, rad, stroke);
        stroke.clearShadowLayer();
    }

    /** Pulsing cursor frame around a rect. */
    protected void cursor(Canvas c, float l, float t, float r, float b, float radius) {
        float p = pulse();
        rf.set(l, t, r, b);
        stroke.setStrokeWidth(dp(2.5f));
        stroke.setColor(alpha(cursorColor(), (int) (150 + 105 * p)));
        c.drawRoundRect(rf, radius, radius, stroke);
    }

    /** Yellow highlighter swipe, for the paper games' cursor. */
    protected void highlighter(Canvas c, float l, float t, float r, float b) {
        float p = pulse();
        rf.set(l, t, r, b);
        fill.setShader(null);
        fill.setColor(alpha(skin().accent, (int) (60 + 50 * p)));
        c.drawRoundRect(rf, dp(6), dp(6), fill);
    }

    // ------------------------------------------------------------------ hand-drawn strokes

    /** A slightly wobbly line, the same every frame for the same seed. */
    protected static Path handLine(float x1, float y1, float x2, float y2, float wobble, long seed) {
        java.util.Random r = new java.util.Random(seed);
        Path p = new Path();
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.hypot(dx, dy), nx = -dy / len, ny = dx / len;
        int segs = Math.max(2, (int) (len / 40));
        p.moveTo(x1 + (r.nextFloat() - 0.5f) * wobble, y1 + (r.nextFloat() - 0.5f) * wobble);
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs, off = (r.nextFloat() - 0.5f) * wobble;
            float mx = x1 + dx * (t - 0.5f / segs) + nx * off, my = y1 + dy * (t - 0.5f / segs) + ny * off;
            p.quadTo(mx, my, x1 + dx * t + nx * off * 0.4f, y1 + dy * t + ny * off * 0.4f);
        }
        return p;
    }

    /** A hand-drawn loop that doesn't quite close, like a real pen circle. */
    protected static Path handCircle(float cx, float cy, float rx, float ry, long seed) {
        java.util.Random r = new java.util.Random(seed);
        Path p = new Path();
        float start = r.nextFloat() * 6.28f, sweep = 6.28f + 0.35f + r.nextFloat() * 0.3f;
        int n = 28;
        for (int i = 0; i <= n; i++) {
            float a = start + sweep * i / n, k = 1f + (r.nextFloat() - 0.5f) * 0.06f + 0.05f * i / n;
            float x = cx + (float) Math.cos(a) * rx * k, y = cy + (float) Math.sin(a) * ry * k;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        return p;
    }
}
