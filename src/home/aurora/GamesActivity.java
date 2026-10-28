package home.aurora;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import home.aurora.games.Checkers;
import home.aurora.games.Chess;
import home.aurora.games.ConnectFour;
import home.aurora.games.DotsAndBoxes;
import home.aurora.games.Game;
import home.aurora.games.GameSkin;
import home.aurora.games.Hangman;
import home.aurora.games.Minesweeper;
import home.aurora.games.Solitaire;
import home.aurora.games.TicTacToe;
import home.aurora.games.Twenty48;

/** Pass-the-remote games: a hub of cards, then a board with a scoreboard beside it. */
public class GamesActivity extends Activity implements Game.Host {

    private static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private static final String[] NAMES = {"Tic-Tac-Toe", "Connect 4", "Checkers", "Chess", "Dots & Boxes",
            "Hangman", "2048", "Minesweeper", "Solitaire"};
    private static final String[] BLURBS = {"Three in a row", "Four in a line", "Jump and king", "Checkmate", "Close the boxes",
            "Guess the word", "Slide and merge", "Clear the mines", "Klondike classic"};
    private static final int HANGMAN = 5;
    private static final String[] LEVELS = {"Easy", "Medium", "Hard"};

    /** Short, plain rules and controls for each game, in NAMES order. */
    private static final String[] RULES = {
            "Get three of your marks in a row: across, down, or diagonal.\n\n"
                    + "CONTROLS\nArrows: move\nOK: place your mark",
            "Take turns dropping pieces into the columns. First to get four in a row wins: across, down, or diagonal.\n\n"
                    + "CONTROLS\nLeft / Right: pick a column\nOK: drop your piece",
            "Pieces move one square diagonally forward. Jump over an opponent's piece to capture it. "
                    + "If you can jump, you have to. Reach the far side to become a king, which can also move backward. "
                    + "Win by taking all their pieces or leaving them no moves.\n\n"
                    + "CONTROLS\nArrows: move\nOK: pick up a piece, then OK on a highlighted square\n"
                    + "Double jump: pick each landing square in turn",
            "Normal chess. Put the other king in checkmate to win. White goes first. "
                    + "A pawn that reaches the far side becomes a queen, rook, bishop or knight (you choose). "
                    + "It's a draw on stalemate, the same position three times, or 50 moves each with no capture or pawn move.\n\n"
                    + "CONTROLS\nArrows: move\nOK: pick a piece (dots show where it can go)\nOK on a dot: move there\n"
                    + "Promoting: Left / Right picks the piece, OK confirms",
            "Take turns drawing one line between two dots. Draw the last side of a box to win it and go again. "
                    + "Most boxes at the end wins.\n\n"
                    + "CONTROLS\nArrows: pick a box\nOK, then an arrow: draw that side of the box\nBack: cancel",
            "Guess the hidden word one letter at a time. Each wrong letter adds to the drawing. "
                    + "Finish the word before the drawing is done.\n\n"
                    + "With 2 players, one person picks the word while the other looks away, then hands over the remote.\n\n"
                    + "CONTROLS\nArrows: pick a letter\nOK: guess it",
            "All tiles slide together. Two tiles with the same number join into one. Try to make 2048 (256 on the 3 × 3 grid). "
                    + "The game ends when the board is full and nothing can join.\n\n"
                    + "CONTROLS\nArrows: slide the tiles",
            "Open every square that isn't a mine. A number tells you how many mines touch that square. "
                    + "Flag the squares you think are mines.\n\n"
                    + "CONTROLS\nArrows: move\nOK: open a square\nHold OK: place or remove a flag\n"
                    + "OK on a number: opens the squares around it, once all its mines are flagged",
            "Move every card to the four piles at the top, one suit per pile, from Ace up to King. "
                    + "In the columns, stack cards going down in number, switching between red and black. "
                    + "Only a King can go in an empty column.\n\n"
                    + "CONTROLS\nArrows: move\nOK: pick up a card, then OK where it should go\n"
                    + "OK twice on a card: move it automatically\nOK on the deck: draw\nBack: put the card back"};
    /** Played alone: start straight away, no mode question. */
    private static boolean soloGame(int i) { return i >= 6; }

    private WallpaperView wall;
    private View shade;
    private GameSkin skin;     // the home screen's look, worn by the hub and every game
    private String sounds;     // and its sound set
    private int sideInk;       // text on the look's surface (black inside a Windows 95 window)
    private FrameLayout stage;
    private final View[] cards = new View[NAMES.length];
    private int lastCard;

    private Game game;
    private int gameIndex;
    private boolean cpu;
    private final int[] score = new int[3];
    private TextView turnText, infoText, hintText, score1, score2, bigScore, bestText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        HomeTheme home = HomeTheme.current(this);
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        // preview builds can pretend it's another hour, like the home screen does
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            hour = Prefs.of(this).getInt("debug_hour", hour);
        skin = GameSkin.forTheme(this, home.id, hour < 7 || hour >= 19);
        Game.skin = skin;
        sideInk = skin.bevel ? 0xFF000000 : skin.ink;
        sounds = home.sounds;
        FrameLayout root = new FrameLayout(this);
        wall = new WallpaperView(this);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        shade = new View(this);
        shade.setBackgroundColor(0x8C000000);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        showHub();
        // Preview builds only: open a game straight away (for checking each look)
        int debugGame = getIntent().getIntExtra("debug_game", -1);
        if (debugGame >= 0 && (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            play(debugGame, getIntent().getBooleanExtra("debug_cpu", true), getIntent().getIntExtra("debug_level", 1));
    }

    @Override protected void onResume() {
        super.onResume();
        Sounds.quietFor(500);
        Sounds.setTheme(this, sounds);
        if (skin.surface == null) wall.start();
        if (game != null) game.resume();
    }

    @Override protected void onPause() { wall.stop(); if (game != null) game.pause(); super.onPause(); }

    /** Let go of the look (it can hold a full-screen bitmap), unless a newer games screen has already set its own. */
    @Override protected void onDestroy() {
        if (Game.skin == skin) Game.skin = null;
        super.onDestroy();
    }

    /** Held arrows move at a steady pace instead of racing across the board. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        return Keys.tooFast(e) || Keys.settingsButton(this, e) || super.dispatchKeyEvent(e);
    }

    /** After any menu closes, make sure the remote goes to the game, not nowhere. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && game != null && !game.hasFocus()) game.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (game != null) showPauseMenu(); else super.onBackPressed();
    }

    // ------------------------------------------------------------------ hub

    private void showHub() {
        game = null;
        sideInk = skin.bevel ? 0xFF000000 : skin.ink;
        applyTheme(false);
        stage.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        TextView title = text(40, ink(0xFFFFFFFF), skin.titleFont);
        title.setText("Games");
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView sub = caps(9, ink(0xB3FFFFFF), "Pass the remote  ·  play the computer  ·  or go solo");
        sub.setPadding(0, dp(2), 0, dp(18));
        col.addView(sub, new LinearLayout.LayoutParams(-2, -2));

        for (int start = 0; start < NAMES.length; start += 5) {
            LinearLayout row = new LinearLayout(this);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(0, dp(7), 0, dp(7));
            for (int i = start; i < Math.min(NAMES.length, start + 5); i++) row.addView(cards[i] = card(i));
            col.addView(row, new LinearLayout.LayoutParams(-2, -2));
        }

        TextView hint = caps(9, ink(0x80FFFFFF), "OK to play  ·  Back for home");
        hint.setPadding(0, dp(18), 0, 0);
        col.addView(hint, new LinearLayout.LayoutParams(-2, -2));

        if (skin.bevel) {
            // Windows 95: the games live in a window on the desktop
            title.setVisibility(View.GONE);
            col.setPadding(dp(24), dp(14), dp(24), dp(18));
            stage.addView(win95("Games", col), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        } else stage.addView(col, new FrameLayout.LayoutParams(-1, -1));
        cards[lastCard].requestFocus();
    }

    private View card(int i) {
        // Aurora's cards are frosted glass over the wallpaper; every other look gets its own panel
        GlassView glass = skin.surface == null ? new GlassView(this, wall, dp(22), 0x33080A12) : null;
        FrameLayout g = glass != null ? glass : new FrameLayout(this);
        if (glass == null) g.setBackground(cardFace(false));
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER_HORIZONTAL);
        in.setPadding(dp(10), dp(12), dp(10), dp(10));
        in.addView(new Preview(this, i, skin), new LinearLayout.LayoutParams(dp(96), dp(62)));
        TextView name = text(15, cardInk(false), skin.textFont);
        name.setText(NAMES[i]);
        name.setPadding(0, dp(8), 0, dp(1));
        in.addView(name);
        TextView blurb = caps(8, (cardInk(false) & 0xFFFFFF) | 0x99000000, BLURBS[i]);
        in.addView(blurb);
        g.addView(in);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(140), dp(148));
        lp.setMargins(dp(8), 0, dp(8), 0);
        g.setLayoutParams(lp);
        g.setFocusable(true);
        g.setOutlineSpotShadowColor(skin.surface == null ? 0xFFFFFFFF : 0xFF000000);
        g.setOnFocusChangeListener((v, has) -> {
            if (has) Sounds.click(this);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? dp(18) : 0).setDuration(170).start();
            if (glass != null) glass.setTint(has ? 0x4DFFFFFF : 0x33080A12);
            else {
                g.setBackground(cardFace(has));
                name.setTextColor(cardInk(has));
                blurb.setTextColor((cardInk(has) & 0xFFFFFF) | 0x99000000);
            }
        });
        g.setOnClickListener(v -> { Sounds.select(this); chooseMode(i); });
        return g;
    }

    private void chooseMode(int i) {
        lastCard = i;
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        if (i < HANGMAN) {
            labels.add("2 players  ·  pass the remote");
            actions.add(() -> play(i, false, 1));
        }
        if (i <= HANGMAN) {
            for (int l = 0; l < 3; l++) {
                final int lv = l;
                labels.add((i == HANGMAN ? "Computer picks the word  ·  " : "Play the computer  ·  ") + LEVELS[l]);
                actions.add(() -> play(i, true, lv));
            }
        }
        if (i == HANGMAN) {
            labels.add("2 players  ·  one picks, one guesses");
            actions.add(() -> play(i, false, 1));
        }
        if (i == 6) addLevels(labels, actions, i, new String[]{"Easy  ·  5 × 5 grid", "Medium  ·  4 × 4 grid", "Hard  ·  3 × 3 grid"}, new int[]{0, 1, 2});
        if (i == 7) addLevels(labels, actions, i, new String[]{"Easy  ·  10 mines", "Medium  ·  22 mines", "Hard  ·  45 mines"}, new int[]{0, 1, 2});
        if (i == 8) addLevels(labels, actions, i, new String[]{"Easy  ·  draw 1 card", "Hard  ·  draw 3 cards"}, new int[]{0, 2});
        labels.add("How to play");
        actions.add(() -> showRules(i, () -> chooseMode(i)));
        GlassMenu.list(this, NAMES[i], labels.toArray(new String[0]), which -> actions.get(which).run());
    }

    private void addLevels(java.util.List<String> labels, java.util.List<Runnable> actions, int game, String[] names, int[] levels) {
        for (int k = 0; k < names.length; k++) {
            final int lv = levels[k];
            labels.add(names[k]);
            actions.add(() -> play(game, false, lv));
        }
    }

    /** Rules and controls; {@code then} runs when it's closed. */
    private void showRules(int i, Runnable then) {
        GlassMenu.message(this, "How to play  ·  " + NAMES[i], RULES[i], then);
    }

    /** Back during a game: a small pause menu instead of leaving straight away. */
    private void showPauseMenu() {
        final Game g = game;
        g.pause();   // the clock and the computer wait while a menu is up
        GlassMenu.list(this, NAMES[gameIndex], new String[]{"Resume", "How to play", "New game", "Back to games"}, which -> {
            if (which == 1) {
                g.pause();
                showRules(gameIndex, () -> { g.resume(); if (game != null) game.requestFocus(); });
            }
            else if (which == 2) g.restart();
            else if (which == 3) showHub();
        }).setOnDismissListener(d -> g.resume());
    }

    // ------------------------------------------------------------------ play

    private void play(int which, boolean vsCpu, int level) {
        cpu = vsCpu;
        gameIndex = which;
        score[1] = score[2] = 0;
        switch (which) {
            case 0: game = new TicTacToe(this); break;
            case 1: game = new ConnectFour(this); break;
            case 2: game = new Checkers(this); break;
            case 3: game = new Chess(this); break;
            case 4: game = new DotsAndBoxes(this); break;
            case 5: game = new Hangman(this); break;
            case 6: game = new Twenty48(this); break;
            case 7: game = new Minesweeper(this); break;
            default: game = new Solitaire(this); break;
        }
        boolean solo = game.solo();
        game.setLevel(level);
        String levelName = solo && which == 8 ? (level == 2 ? "Draw 3" : "Draw 1")
                : solo ? LEVELS[level] : vsCpu ? "vs computer  ·  " + LEVELS[level] : "2 players";
        stage.removeAllViews();
        applyTheme(true);
        // the scoreboard sits on the look's own panel (Aurora floats it on the wallpaper; 95 uses the window)
        boolean card = !skin.bevel && skin.surface != null;
        sideInk = skin.bevel ? 0xFF000000 : card ? cardInk(false) : skin.ink;

        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(56), dp(36), dp(40), dp(36));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.addView(caps(9, ink(0x80FFFFFF), levelName));
        TextView title = text(34, ink(0xFFFFFFFF), skin.titleFont);
        title.setText(game.title());
        title.setPadding(0, 0, 0, dp(22));
        // one line always: long names ("Minesweeper") shrink to fit the panel instead of wrapping
        title.setMaxLines(1);
        title.setAutoSizeTextTypeUniformWithConfiguration(16, 34, 1, TypedValue.COMPLEX_UNIT_SP);
        if (!skin.bevel) side.addView(title, new LinearLayout.LayoutParams(-1, dp(64)));   // in Windows 95 the window's title bar names the game
        score1 = score2 = bigScore = bestText = null;
        if (solo) {
            bigScore = text(52, ink(0xFFFFFFFF), skin.titleFont);
            bigScore.setIncludeFontPadding(false);
            side.addView(bigScore);
            bestText = caps(9, ink(0x99FFFFFF), "");
            bestText.setPadding(0, dp(6), 0, 0);
            side.addView(bestText);
        } else {
            score1 = new TextView(this);
            score2 = new TextView(this);
            side.addView(playerRow(1, name(1), score1));
            side.addView(playerRow(2, name(2), score2));
        }
        turnText = text(22, ink(0xFFFFFFFF), skin.textFont);
        turnText.setPadding(0, dp(28), 0, dp(6));
        side.addView(turnText);
        infoText = caps(9, ink(0xB3FFFFFF), "");
        side.addView(infoText);
        View spacer = new View(this);
        side.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));
        hintText = caps(9, ink(0x80FFFFFF), "");
        side.addView(hintText);
        if (card) {
            side.setBackground(cardFace(false));
            side.setPadding(dp(20), dp(18), dp(18), dp(16));
            side.setElevation(dp(6));
        }
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(dp((solo ? 200 : 250) + (card ? 24 : 0)), -1);
        if (card) sideLp.rightMargin = dp(22);
        row.addView(side, sideLp);

        row.addView(game, new LinearLayout.LayoutParams(0, -1, 1));
        if (skin.bevel) {
            row.setPadding(dp(24), dp(14), dp(20), dp(16));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            lp.setMargins(dp(22), dp(16), dp(22), dp(16));
            stage.addView(win95(game.title(), row), lp);
        } else stage.addView(row, new FrameLayout.LayoutParams(-1, -1));
        updateScores();
        game.start(this, vsCpu);
        game.requestFocus();
        game.post(game::requestFocus);   // again once the mode menu has finished closing
    }

    private View playerRow(int p, String name, TextView scoreView) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(game.classic() ? (p == 1 ? skin.pieceLight : skin.pieceDark) : game.uiColor(p));
        if (game.classic()) d.setStroke(dp(1.5f), skin.pieceEdge);
        dot.setBackground(d);
        dot.setElevation(dp(4));
        dot.setOutlineSpotShadowColor(game.uiColor(p));
        r.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        TextView n = text(15, ink(0xF2FFFFFF), skin.textFont);
        n.setText(name);
        n.setPadding(dp(12), 0, 0, 0);
        r.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
        scoreView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        scoreView.setTypeface(skin.titleFont);
        scoreView.setTextColor(ink(0xFFFFFFFF));
        r.addView(scoreView);
        return r;
    }

    private String name(int p) {
        String side = game == null ? null : game.sideName(p);
        if (cpu) return (p == 1 ? "You" : "Computer") + (side != null ? " · " + side : "");
        return side != null ? side : "Player " + p;
    }

    private void updateScores() {
        if (score1 == null) return;
        score1.setText(String.valueOf(score[1]));
        score2.setText(String.valueOf(score[2]));
    }

    @Override
    public void onTurn(int p) {
        String hint = game.hint();
        if (game.solo()) {
            turnText.setText("");
        } else {
            turnText.setTextColor(game.uiColor(p));
            if (cpu) turnText.setText(p == 1 ? "Your turn" : "Computer is thinking…");
            else turnText.setText(name(p) + "'s turn");
        }
        hintText.setText(hint != null ? hint : "Arrows move  ·  OK plays  ·  Back for menu");
    }

    @Override
    public void onOver(int winner) {
        if (game.solo()) {
            turnText.setTextColor(winner == 1 ? skin.accent : ink(0xFFFFFFFF));
            turnText.setText(winner == 1 ? "You did it!" : "Game over");
            hintText.setText("OK for a new game  ·  Back for menu");
            return;
        }
        if (winner > 0) score[winner]++;
        updateScores();
        if (winner == 0) {
            turnText.setTextColor(ink(0xFFFFFFFF));
            turnText.setText("It's a draw");
        } else {
            turnText.setTextColor(game.uiColor(winner));
            turnText.setText(cpu ? (winner == 1 ? "You win!" : "Computer wins") : name(winner) + " wins!");
        }
        hintText.setText("OK to play again  ·  Back for menu");
    }

    @Override
    public void onInfo(String info) { if (infoText != null) infoText.setText(info); }

    @Override
    public void onScore(String scoreLine, String best) {
        if (bigScore != null) bigScore.setText(scoreLine);
        if (bestText != null) bestText.setText(best);
    }

    // ------------------------------------------------------------------ helpers

    /** The hub and the games stand on the look's surface; Aurora's is the wallpaper. */
    private void applyTheme(boolean inGame) {
        boolean wallpaper = skin.surface == null;
        wall.setVisibility(wallpaper ? View.VISIBLE : View.GONE);
        shade.setVisibility(wallpaper ? View.VISIBLE : View.GONE);
        stage.setBackground(skin.surface);
        if (wallpaper) wall.start(); else wall.stop();
    }

    /** White text becomes the look's ink, keeping its transparency. */
    private int ink(int white) {
        int a = (white >>> 24) * (sideInk >>> 24) / 255;
        return (a << 24) | (sideInk & 0xFFFFFF);
    }

    /** A Windows 95 window: navy title bar with its three buttons, a menu bar, a silver client area. */
    private View win95(String title, View body) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setBackground(new HomeTheme.Bevel(this, 0xFFC0C0C0, false));
        w.setPadding(dp(3), dp(3), dp(3), dp(3));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(0xFF000080);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(2), dp(2), dp(2));
        TextView t = text(12, 0xFFFFFFFF, Typeface.create("sans-serif", Typeface.BOLD));
        t.setText(title);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        String[] glyphs = {"_", "□", "×"};
        for (int i = 0; i < 3; i++) {
            TextView b = text(10, 0xFF000000, Typeface.create("sans-serif", Typeface.BOLD));
            b.setText(glyphs[i]);
            b.setGravity(Gravity.CENTER);
            b.setIncludeFontPadding(false);
            b.setBackground(new HomeTheme.Bevel(this, 0xFFC0C0C0, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(16));
            lp.leftMargin = dp(i == 2 ? 3 : 1);
            bar.addView(b, lp);
        }
        w.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        TextView menu = text(12, 0xFF000000, Typeface.create("sans-serif", Typeface.NORMAL));
        menu.setText(android.text.Html.fromHtml("<u>G</u>ame&nbsp;&nbsp;&nbsp;&nbsp;<u>O</u>ptions&nbsp;&nbsp;&nbsp;&nbsp;<u>H</u>elp", 0));
        menu.setPadding(dp(8), dp(3), dp(8), dp(4));
        w.addView(menu, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout client = new FrameLayout(this);
        client.setClipChildren(false);
        client.addView(body, new FrameLayout.LayoutParams(-1, -1));
        w.addView(client, new LinearLayout.LayoutParams(-1, 0, 1));
        return w;
    }

    /** A hub card in the look's own material. */
    private android.graphics.drawable.Drawable cardFace(boolean focused) {
        if (skin.bevel) return new HomeTheme.Bevel(this, focused ? 0xFFFFFFFF : 0xFFC0C0C0, false);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(skin.glow ? 0 : dp(6 + 60 * skin.round));
        d.setColor(focused && !skin.glow ? skin.panelFocus : skin.panel);
        d.setStroke(dp(focused ? 2.5f : 1f), focused ? skin.panelFocus : skin.panelEdge);
        return d;
    }

    /** Text on a hub card. */
    private int cardInk(boolean focused) {
        if (skin.bevel) return 0xFF000000;
        if (focused && !skin.glow) return skin.onAccent;
        int bg = skin.panel;
        int lum = ((bg >> 16) & 0xFF) * 3 + ((bg >> 8) & 0xFF) * 6 + (bg & 0xFF);
        return (bg >>> 24) > 0x99 && lum > 1600 ? 0xFF1C1C1E : skin.ink;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    private TextView caps(float sp, int color, String s) {
        TextView t = text(sp, color, MEDIUM);
        t.setLetterSpacing(0.26f);
        t.setAllCaps(true);
        t.setText(s);
        return t;
    }

    /** Little glowing motif for each hub card. */
    private static class Preview extends View {
        private final int kind;
        private final GameSkin s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Preview(Context c, int kind, GameSkin s) { super(c); this.kind = kind; this.s = s; }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), u = h / 6;
            p.setStrokeCap(Paint.Cap.ROUND);
            switch (kind) {
                case 0: // X and O
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(u * 0.55f);
                    p.setColor(s.p1);
                    float x = w * 0.32f, y = h / 2;
                    c.drawLine(x - u * 1.3f, y - u * 1.3f, x + u * 1.3f, y + u * 1.3f, p);
                    c.drawLine(x + u * 1.3f, y - u * 1.3f, x - u * 1.3f, y + u * 1.3f, p);
                    p.setColor(s.p2);
                    c.drawCircle(w * 0.7f, y, u * 1.4f, p);
                    break;
                case 1: { // little blue board, red and yellow pieces
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(s.cell);
                    float bx = w / 2 - u * 3.3f, byy = h / 2 - u * 2.3f;
                    c.drawRoundRect(bx, byy, bx + u * 6.6f, byy + u * 4.6f, u * 0.6f, u * 0.6f, p);
                    int[] cols = {0, s.p1, 0, s.p2, s.p1, s.p2, s.p1, s.p2, s.p2};
                    for (int i = 0; i < 9; i++) {
                        p.setColor(cols[i] == 0 ? s.cellOpen : cols[i]);
                        c.drawCircle(bx + u * (1.2f + (i % 3) * 2.1f), byy + u * (0.95f + (i / 3) * 1.35f), u * 0.55f, p);
                    }
                    break;
                }
                case 2: // checkers: a black man and a white king
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(s.pieceDark);
                    c.drawCircle(w / 2 - u * 1.6f, h / 2 - u * 1.1f, u * 1.1f, p);
                    p.setColor(s.pieceLight);
                    c.drawCircle(w / 2 + u * 1.6f, h / 2 + u * 1.1f, u * 1.1f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(u * 0.12f);
                    p.setColor(s.pieceEdge);
                    c.drawCircle(w / 2 - u * 1.6f, h / 2 - u * 1.1f, u * 1.1f, p);
                    p.setStrokeWidth(u * 0.3f);
                    p.setColor(s.accent);
                    c.drawCircle(w / 2 + u * 1.6f, h / 2 + u * 1.1f, u * 0.5f, p);
                    break;
                case 3: { // chess knight
                    p.setStyle(Paint.Style.FILL);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(u * 4.4f);
                    p.setColor(s.pieceLight);
                    p.setShadowLayer(u * 0.8f, 0, 0, 0x66000000);
                    String k = p.hasGlyph("♞") ? "♞\uFE0E" : "N";
                    c.drawText(k, w / 2, h / 2 - (p.descent() + p.ascent()) / 2, p);
                    p.clearShadowLayer();
                    break;
                }
                case 4: { // dots with two lines
                    float g = u * 1.7f, ox = w / 2 - g, oy = h / 2 - g;
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(u * 0.4f);
                    p.setColor(s.p1);
                    c.drawLine(ox, oy, ox + g, oy, p);
                    p.setColor(s.p2);
                    c.drawLine(ox + g, oy, ox + g, oy + g, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor((s.p1 & 0xFFFFFF) | 0x4D000000);
                    c.drawRect(ox + u * 0.3f, oy + u * 0.3f, ox + g - u * 0.3f, oy + g - u * 0.3f, p);
                    p.setColor(s.pieceLight);
                    for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) c.drawCircle(ox + k * g, oy + r * g, u * 0.32f, p);
                    break;
                }
                case 5: { // hangman blanks
                    p.setStyle(Paint.Style.FILL);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(u * 2.4f);
                    p.setColor(s.accent);
                    p.setLetterSpacing(0.3f);
                    c.drawText("_A_E", w / 2, h / 2 + u * 0.8f, p);
                    p.setLetterSpacing(0f);
                    break;
                }
                case 6: { // 2048 tile
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(s.tiles[s.tiles.length - 1]);
                    c.drawRoundRect(w / 2 - u * 2.4f, h / 2 - u * 2.4f, w / 2 + u * 2.4f, h / 2 + u * 2.4f, u * 0.7f, u * 0.7f, p);
                    p.setColor(s.tileInkLight);
                    p.setFakeBoldText(true);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(u * 1.5f);
                    c.drawText("2048", w / 2, h / 2 + u * 0.55f, p);
                    break;
                }
                case 7: { // mine on a grey Windows tile
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(s.cell);
                    c.drawRect(w / 2 - u * 2.8f, h / 2 - u * 2.8f, w / 2 + u * 2.8f, h / 2 + u * 2.8f, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(u * 0.35f);
                    p.setColor(s.glow ? s.accent : 0xFF000000);
                    for (int k = 0; k < 4; k++) {
                        double a = k * Math.PI / 4;
                        float dx = (float) Math.cos(a) * u * 2.2f, dy = (float) Math.sin(a) * u * 2.2f;
                        c.drawLine(w / 2 - dx, h / 2 - dy, w / 2 + dx, h / 2 + dy, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(w / 2, h / 2, u * 1.4f, p);
                    break;
                }
                default: { // two cards
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(s.cardBack);
                    c.drawRoundRect(w / 2 - u * 2.6f, h / 2 - u * 2.3f, w / 2 + u * 0.4f, h / 2 + u * 2f, u * 0.5f, u * 0.5f, p);
                    p.setColor(s.cardFace);
                    c.drawRoundRect(w / 2 - u * 0.6f, h / 2 - u * 2f, w / 2 + u * 2.4f, h / 2 + u * 2.3f, u * 0.5f, u * 0.5f, p);
                    p.setColor(s.cardRed);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(u * 2.2f);
                    c.drawText(p.hasGlyph("♥") ? "♥" : "A", w / 2 + u * 0.9f, h / 2 + u * 0.9f, p);
                }
            }
        }
    }
}
