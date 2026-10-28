package home.aurora.games;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Hangman. Against the computer it picks a word and you guess. With 2 players,
 * one types a secret word (shown only as dots) and the other guesses; the roles
 * swap each round. Six wrong guesses and the setter wins.
 */
public class Hangman extends Game {

    private static final String[] WORDS = (
            "ANCHOR APPLE ARCADE ASTRONAUT AVOCADO BACKPACK BALLOON BANANA BASKET BEACH BICYCLE BLANKET "
            + "BLIZZARD BRIDGE BUBBLE BUTTERFLY CACTUS CAMERA CAMPFIRE CANDLE CANYON CARNIVAL CASTLE "
            + "CHEETAH CHIMNEY CHOCOLATE CIRCUS CLOUD COCONUT COMET COMPASS COOKIE CRAYON CROCODILE "
            + "CRYSTAL CUPCAKE DIAMOND DINOSAUR DOLPHIN DONUT DRAGON DRUMMER EAGLE ECLIPSE ELEPHANT "
            + "EMERALD ENGINE FEATHER FESTIVAL FIREWORK FLAMINGO FOOTBALL FOREST FOSSIL FOUNTAIN GALAXY "
            + "GARDEN GIRAFFE GLACIER GUITAR HAMMOCK HARBOR HEDGEHOG HELICOPTER HONEY HORIZON ICEBERG "
            + "IGLOO ISLAND JACKET JELLYFISH JIGSAW JUNGLE KANGAROO KETTLE KEYBOARD KITTEN LADDER LANTERN "
            + "LEMONADE LIBRARY LIGHTHOUSE LIZARD LOBSTER MAGNET MAMMOTH MARBLE MEADOW MERMAID METEOR "
            + "MONKEY MOUNTAIN MUSEUM MUSHROOM NECKLACE NOTEBOOK OCTOPUS ORANGE ORCHESTRA OSTRICH PADDLE "
            + "PANCAKE PANDA PARACHUTE PARROT PENGUIN PEPPER PIANO PICNIC PILLOW PINEAPPLE PIRATE PIZZA "
            + "PLANET POPCORN PRETZEL PUMPKIN PUPPY PUZZLE PYRAMID RAINBOW RABBIT RACCOON RIVER ROBOT "
            + "ROCKET SANDWICH SATELLITE SAXOPHONE SCARECROW SEAHORSE SHADOW SKATEBOARD SNOWFLAKE "
            + "SPAGHETTI SPIDER SQUIRREL STADIUM STARFISH STRAWBERRY SUBMARINE SUNFLOWER SUNSET SWEATER "
            + "TELESCOPE TIGER TORNADO TORTOISE TRACTOR TREASURE TROPHY TRUMPET TUNNEL TURTLE UMBRELLA "
            + "UNICORN VAMPIRE VELVET VIOLIN VOLCANO WAFFLE WALRUS WATERFALL WATERMELON WHISTLE WINDMILL "
            + "WIZARD WOLF ZEBRA ZIPPER").split(" ");
    private static final String[] KEYS = {"ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ"};

    private final Random rnd = new Random();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Set<Character> guessed = new HashSet<>();
    private String word = "";
    private StringBuilder entry = new StringBuilder();
    private boolean entering;
    private int misses, kr, kc, maxMisses = 6;

    public Hangman(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
    }

    @Override public String title() { return "Hangman"; }
    @Override protected boolean alternateStart() { return !vsCpu; }

    @Override
    public String hint() {
        return entering ? "Pick letters  ·  ⌫ deletes  ·  DONE when ready" : "Arrows to pick a letter  ·  OK to guess";
    }

    @Override
    protected void newGame() {
        guessed.clear();
        misses = 0;
        kr = kc = 0;
        entry = new StringBuilder();
        maxMisses = vsCpu ? (level == 0 ? 8 : level == 1 ? 6 : 5) : 6;
        if (vsCpu) {
            do { word = WORDS[rnd.nextInt(WORDS.length)]; }
            while (level == 0 ? word.length() > 6 : level == 2 && word.length() < 8);
            entering = false;
            turn = 1;
            if (host != null) host.onInfo(word.length() + " letters  ·  " + maxMisses + " wrong guesses allowed");
        } else {
            word = "";
            entering = true;    // turn = the player choosing the word
            if (host != null) host.onInfo("Choose a secret word  ·  everyone else look away");
        }
    }

    /** During entry the last keyboard row also has ⌫ and DONE. */
    private int rowLength(int r) { return KEYS[r].length() + (entering && r == 3 ? 2 : 0); }

    @Override
    protected boolean moveCursor(int dx, int dy) {
        kr = Math.max(0, Math.min(3, kr + dy));
        kc = Math.max(0, Math.min(rowLength(kr) - 1, kc + dx));
        return true;
    }

    @Override
    protected boolean playAtCursor() {
        if (entering) {
            if (kr == 3 && kc == 5) { if (entry.length() > 0) entry.setLength(entry.length() - 1); }
            else if (kr == 3 && kc == 6) {
                if (entry.length() < 3) { host.onInfo("At least 3 letters"); return true; }
                word = entry.toString();
                entering = false;
                turn = 3 - turn;
                kr = kc = 0;   // don't leave the cursor on the word's last letter
                host.onTurn(turn);
                host.onInfo(word.length() + " letters  ·  pass the remote");
            } else if (entry.length() < 12) {
                entry.append(KEYS[kr].charAt(kc));
                kr = kc = 0;   // back to A, so the screen never rests on a letter of the secret word
            }
            if (entering) host.onInfo("Secret word:  " + dots(entry.length()));
            return true;
        }
        char ch = KEYS[kr].charAt(kc);
        if (!guessed.add(ch)) return false;
        if (word.indexOf(ch) < 0) misses++;
        host.onInfo(maxMisses - misses + " wrong guesses left");
        int w = winner();
        if (w >= 0) {
            over = true;
            host.onInfo("The word was " + word);
            host.onOver(w);
        }
        return true;
    }

    private static String dots(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append("● ");
        return b.toString().trim();
    }

    private boolean solved() {
        for (char ch : word.toCharArray()) if (!guessed.contains(ch)) return false;
        return true;
    }

    /** The guesser is {@code turn}; they win by solving it, the setter wins on six misses. */
    @Override
    protected int winner() {
        if (entering || word.isEmpty()) return -1;
        if (solved()) return turn;
        if (misses >= maxMisses) return 3 - turn;
        return -1;
    }

    @Override protected void cpuMove() { }

    // ------------------------------------------------------------------ drawing: one board per look

    private BoardLook look;

    private BoardLook look() {
        GameSkin s = skin();
        if (look == null || look.s != s) look = new BoardLook(this, s);
        return look;
    }

    @Override public int uiColor(int p) {
        GameSkin s = skin();
        switch (s.id) {
            case "art": return p == 1 ? 0xFFF3E7CF : 0xFFE2C27A;
            case "retro": return p == 1 ? 0xFFF6EBD0 : BoardLook.R_MUSTARD;
            case "space": return p == 1 ? BoardLook.S_GREEN : BoardLook.S_AMBER;
            case "cozy": return p == 1 ? BoardLook.C_IVORY : 0xFFFFB04A;
            default: return color(p);
        }
    }

    /** Line and letter colours on the board: ink, a "found" colour and a "missed" colour. */
    private int ink() {
        switch (skin().id) {
            case "classic": return 0xFF000000;
            case "retro": return BoardLook.R_BROWN;
            case "art": return BoardLook.A_INK;
            case "space": return BoardLook.S_GREEN;
            case "cozy": return BoardLook.C_IVORY;
            case "light": return 0xFF2A2A2E;
            case "aurora": return 0xFFFFFFFF;
            default: return skin().ink;
        }
    }

    private int found() {
        switch (skin().id) {
            case "classic": return BoardLook.W_NAVY;
            case "retro": return BoardLook.R_AVOCADO;
            case "art": return BoardLook.A_GREEN;
            case "space": return BoardLook.S_GREEN;
            case "cozy": return BoardLook.C_BRASS;
            default: return skin().p1;
        }
    }

    private int missed() {
        switch (skin().id) {
            case "classic": return 0xFF800000;
            case "retro": return BoardLook.R_ORANGE;
            case "art": return BoardLook.A_OXBLOOD;
            case "space": return BoardLook.S_RED;
            case "cozy": return 0xFFE0603A;
            case "aurora": return skin().p2;
            case "cs": return skin().ink;
            default: return skin().p2;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        BoardLook k = look();
        String id = k.id();
        float[] m = k.margins();
        float W = getWidth(), H = getHeight(), u = k.u;
        float L = m[1] + u * 8, T = m[0] + u * 6, R = W - m[1] - u * 8, B = H - m[2] - u * 6;
        if (id.equals("classic")) { L = Math.round(L / k.px) * k.px; T = Math.round(T / k.px) * k.px; R = Math.round(R / k.px) * k.px; B = Math.round(B / k.px) * k.px; }
        k.board(c, L, T, R, B, title());
        float w = R - L, h = B - T;

        // the keyboard along the bottom
        float kw = Math.min(w / 8.4f, u * 62), kh = kw * 0.78f, gap = kw * 0.16f;
        if (id.equals("classic")) { kw = Math.round(kw / k.px) * k.px; kh = Math.round(kh / k.px) * k.px; gap = Math.round(gap / k.px) * k.px; }
        float ky = B - 4 * kh - 3 * gap - u * 6;
        // the gallows on the left, the word on the right
        float picTop = T + u * 6, picBot = ky - u * 18, picH = picBot - picTop;
        float picW = Math.min(w * 0.4f, picH * 0.95f), picL = L + u * 6;
        drawGallows(c, k, id, picL, picTop, picL + picW, picBot);
        drawWord(c, k, id, picL + picW + u * 18, R - u * 6, picTop + picH * 0.5f, picH);
        drawKeys(c, k, id, L, R, ky, kw, kh, gap);
        k.screenGlare(c, L, T, R, B);
        if (id.equals("cs")) k.scanlines(c, L - u * 14, T - u * 30, R + u * 14, B + u * 14);
        k.reset();
    }

    private int parts() { return (int) Math.ceil(misses * 6.0 / maxMisses); }

    // --- the gallows and the figure

    private void drawGallows(Canvas c, BoardLook k, String id, float l, float t, float r, float b) {
        float u = k.u;
        int parts = parts();
        k.reset();
        if (id.equals("cs")) { asciiGallows(c, k, l, t, r, b, parts); return; }
        if (id.equals("classic")) {   // a white Paint canvas, sunk into the window
            k.crisp.setColor(BoardLook.W_WHITE);
            c.drawRect(l, t, r, b, k.crisp);
            k.bevel(c, l - k.px * 2, t - k.px * 2, r + k.px * 2, b + k.px * 2, false);
        } else if (id.equals("space")) {
            k.ln.setStrokeWidth(Math.max(1f, u));
            k.ln.setColor(0x447CF29A);
            c.drawRect(l, t, r, b, k.ln);
            k.tx.setTypeface(BoardLook.MONO);
            k.tx.setTextSize(u * 9);
            k.tx.setColor(0x997CF29A);
            k.tx.setTextAlign(android.graphics.Paint.Align.LEFT);
            c.drawText("HULL  " + (6 - parts) + "/6", l + u * 6, t + u * 14, k.tx);
            k.tx.setTextAlign(android.graphics.Paint.Align.CENTER);
        }
        float pw = r - l, ph = b - t;
        float gh = ph * 0.86f, gw = gh * 0.7f, gx = l + (pw - gw) / 2 + gw * 0.12f, gy = t + ph * 0.07f;
        float base = gy + gh, hx = gx + gw * 0.6f, hr = gh * 0.085f, hy = gy + gh * 0.14f + hr;
        float neck = hy + hr, hip = neck + gh * 0.3f;
        float[][] wood = {
                {gx - gw * 0.35f, base, gx + gw * 0.6f, base},
                {gx, base, gx, gy},
                {gx, gy, hx + gw * 0.08f, gy},
                {gx, gy + gh * 0.2f, gx + gw * 0.2f, gy},
                {hx, gy, hx, gy + gh * 0.14f}};
        for (int i = 0; i < wood.length; i++) beam(c, k, id, wood[i], i == 4, i);
        float[][] limbs = {
                {hx, neck, hx, hip},
                {hx, neck + gh * 0.07f, hx - gh * 0.13f, neck + gh * 0.18f},
                {hx, neck + gh * 0.07f, hx + gh * 0.13f, neck + gh * 0.18f},
                {hx, hip, hx - gh * 0.11f, hip + gh * 0.17f},
                {hx, hip, hx + gh * 0.11f, hip + gh * 0.17f}};
        if (parts > 0) head(c, k, id, hx, hy, hr, parts == 1);
        for (int i = 0; i < 5; i++) if (parts > i + 1) limb(c, k, id, limbs[i], parts == i + 2);
    }

    private void beam(Canvas c, BoardLook k, String id, float[] e, boolean rope, int seed) {
        float u = k.u;
        k.reset();
        switch (id) {
            case "classic": {
                k.crisp.setStyle(android.graphics.Paint.Style.STROKE);
                k.crisp.setStrokeWidth(k.px * (rope ? 2 : 4));
                k.crisp.setColor(rope ? 0xFF808000 : 0xFF000000);
                c.drawLine(e[0], e[1], e[2], e[3], k.crisp);
                k.crisp.setStyle(android.graphics.Paint.Style.FILL);
                return;
            }
            case "art":
                k.p.setColor(rope ? 0xCC6A4A2A : 0xFF3A2A1E);
                c.drawPath(k.taper(e[0], e[1], e[2], e[3], u * (rope ? 2.5f : 6)), k.p);
                return;
            case "space": {
                Path q = new Path(); q.moveTo(e[0], e[1]); q.lineTo(e[2], e[3]);
                k.holo(c, q, rope ? 0x997CF29A : 0xCC7CF29A, u * (rope ? 1.2f : 2));
                return;
            }
            case "minimal":
                k.ln.setStrokeWidth(u * (rope ? 0.8f : 1.4f));
                k.ln.setStrokeCap(android.graphics.Paint.Cap.SQUARE);
                k.ln.setColor(skin().ink);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                return;
            case "retro": case "cozy": {
                if (rope) {
                    k.ln.setStrokeWidth(u * 3);
                    k.ln.setColor(id.equals("retro") ? 0xFFB8893A : 0xFFD9B070);
                    c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                    return;
                }
                float wd = u * (id.equals("retro") ? 12 : 10);
                k.ln.setStrokeCap(android.graphics.Paint.Cap.SQUARE);
                k.ln.setStrokeWidth(wd);
                k.ln.setColor(0x55000000);
                c.drawLine(e[0] + u * 2, e[1] + u * 3, e[2] + u * 2, e[3] + u * 3, k.ln);
                int base = id.equals("retro") ? 0xFF7A4422 : 0xFF9A6A3A;
                k.ln.setColor(base);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                k.ln.setStrokeWidth(wd * 0.25f);
                k.ln.setColor(0x40FFE0B0);
                boolean hz = Math.abs(e[1] - e[3]) < 1;
                c.drawLine(e[0] - (hz ? 0 : wd * 0.25f), e[1] - (hz ? wd * 0.25f : 0), e[2] - (hz ? 0 : wd * 0.25f), e[3] - (hz ? wd * 0.25f : 0), k.ln);
                return;
            }
            case "light":
                k.ln.setStrokeWidth(u * (rope ? 2 : 5));
                k.ln.setColor(rope ? 0xFF8E8E93 : 0xFF3A3A40);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                return;
            default:
                k.ln.setStrokeWidth(u * (rope ? 2 : 4.5f));
                k.ln.setColor(rope ? 0xB3FFFFFF : 0xE6FFFFFF);
                k.ln.setShadowLayer(u * 8, 0, 0, 0x66FFFFFF);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                k.ln.clearShadowLayer();
        }
    }

    /** The figure's colour: a little warning red on the newest part in most looks. */
    private int figure(String id, boolean newest) {
        switch (id) {
            case "classic": return 0xFF000000;
            case "retro": return newest ? BoardLook.R_ORANGE : BoardLook.R_BROWN;
            case "art": return newest ? BoardLook.A_OXBLOOD : BoardLook.A_INK;
            case "space": return BoardLook.S_AMBER;
            case "cozy": return newest ? 0xFFFFB04A : BoardLook.C_IVORY;
            case "minimal": return newest ? skin().p2 : skin().ink;
            case "light": return newest ? skin().p2 : 0xFF2A2A2E;
            default: return newest ? skin().p2 : 0xFFFFFFFF;
        }
    }

    private void head(Canvas c, BoardLook k, String id, float x, float y, float r, boolean newest) {
        float u = k.u;
        int col = figure(id, newest);
        k.reset();
        switch (id) {
            case "classic":
                k.crisp.setStyle(android.graphics.Paint.Style.STROKE);
                k.crisp.setStrokeWidth(k.px * 2);
                k.crisp.setColor(col);
                c.drawCircle(x, y, r, k.crisp);
                k.crisp.setStyle(android.graphics.Paint.Style.FILL);
                return;
            case "art":
                k.p.setColor(col);
                c.drawPath(k.nibRing(x, y, r, u * 4), k.p);
                return;
            case "space": {
                Path q = new Path(); q.addCircle(x, y, r, Path.Direction.CW);
                k.holo(c, q, col, u * 2);
                return;
            }
            default:
                stroke(c, k, id, col, newest);
                c.drawCircle(x, y, r, k.ln);
                k.ln.clearShadowLayer();
        }
    }

    private void limb(Canvas c, BoardLook k, String id, float[] e, boolean newest) {
        float u = k.u;
        int col = figure(id, newest);
        k.reset();
        switch (id) {
            case "classic":
                k.crisp.setStyle(android.graphics.Paint.Style.STROKE);
                k.crisp.setStrokeWidth(k.px * 2);
                k.crisp.setColor(col);
                c.drawLine(e[0], e[1], e[2], e[3], k.crisp);
                k.crisp.setStyle(android.graphics.Paint.Style.FILL);
                return;
            case "art":
                k.p.setColor(col);
                c.drawPath(k.taper(e[0], e[1], e[2], e[3], u * 4), k.p);
                return;
            case "space": {
                Path q = new Path(); q.moveTo(e[0], e[1]); q.lineTo(e[2], e[3]);
                k.holo(c, q, col, u * 2);
                return;
            }
            default:
                stroke(c, k, id, col, newest);
                c.drawLine(e[0], e[1], e[2], e[3], k.ln);
                k.ln.clearShadowLayer();
        }
    }

    private void stroke(Canvas c, BoardLook k, String id, int col, boolean newest) {
        float u = k.u;
        k.ln.setColor(col);
        switch (id) {
            case "retro": k.ln.setStrokeWidth(u * 7); break;
            case "cozy": k.ln.setStrokeWidth(u * 5); k.ln.setShadowLayer(u * 6, 0, 0, BoardLook.a(0xFFFFB04A, newest ? 0xAA : 0x44)); break;
            case "minimal": k.ln.setStrokeWidth(u * 1.4f); break;
            case "light": k.ln.setStrokeWidth(u * 4); break;
            default: k.ln.setStrokeWidth(u * 4); k.ln.setShadowLayer(u * 8, 0, 0, BoardLook.a(col, 0x99));
        }
    }

    private void asciiGallows(Canvas c, BoardLook k, float l, float t, float r, float b, int parts) {
        String[] art = {
                "  +----+  ",
                "  |    |  ",
                "  |    " + (parts > 0 ? "O" : " ") + "  ",
                "  |   " + (parts > 2 ? "/" : " ") + (parts > 1 ? "|" : " ") + (parts > 3 ? "\\" : " ") + " ",
                "  |   " + (parts > 4 ? "/" : " ") + " " + (parts > 5 ? "\\" : " ") + " ",
                "  |       ",
                "==========",
        };
        int cols = 10;
        float cw = Math.min((r - l) / cols, (b - t) / (art.length + 2) / 1.45f), rh = cw * 1.45f;
        float x0 = l + (r - l - cols * cw) / 2, y0 = t + (b - t - (art.length + 1.5f) * rh) / 2;
        k.tx.setTypeface(BoardLook.MONO_BOLD);
        k.tx.setTextSize(cw / 0.6f);
        GameSkin s = skin();
        for (int i = 0; i < art.length; i++) {
            k.glow(true, i == 6 ? s.inkDim : s.ink);
            k.glyphs(c, art[i], x0, y0 + rh * (i + 0.5f), cw);
        }
        k.glow(false, 0);
        k.tx.setTypeface(BoardLook.MONO);
        k.tx.setTextSize(cw * 1.1f);
        k.tx.setColor(s.inkDim);
        k.tx.setTextAlign(android.graphics.Paint.Align.LEFT);
        c.drawText("lives: " + "#######".substring(0, 6 - parts) + "......".substring(0, parts), x0, y0 + rh * (art.length + 1), k.tx);
        k.tx.setTextAlign(android.graphics.Paint.Align.CENTER);
    }

    private static String mirror(String line) {
        StringBuilder b = new StringBuilder();
        for (int i = line.length() - 1; i >= 0; i--) {
            char ch = line.charAt(i);
            b.append(ch == '/' ? '\\' : ch == '\\' ? '/' : ch);
        }
        return b.toString();
    }

    // --- the word

    private void drawWord(Canvas c, BoardLook k, String id, float l, float r, float cy, float maxH) {
        float u = k.u;
        String letters = entering ? dots(entry.length()).replace(" ", "") : word;
        int n = Math.max(1, letters.length());
        float slot = Math.min(Math.min(u * 58, maxH * 0.4f), (r - l - (id.equals("classic") ? u * 24 : 0)) / n), wx = (l + r) / 2 - slot * n / 2f;
        if (id.equals("classic")) {   // a sunken white text box
            float bh = slot * 1.25f;
            float bl = Math.round((wx - u * 10) / k.px) * k.px, br = Math.round((wx + slot * n + u * 10) / k.px) * k.px;
            float bt = Math.round((cy - bh / 2) / k.px) * k.px, bb = Math.round((cy + bh / 2) / k.px) * k.px;
            k.crisp.setColor(BoardLook.W_WHITE);
            c.drawRect(bl, bt, br, bb, k.crisp);
            k.bevel(c, bl, bt, br, bb, false);
        }
        for (int i = 0; i < letters.length(); i++) {
            float x = wx + slot * i, cx = x + slot / 2;
            char ch = letters.charAt(i);
            boolean show = entering || over || guessed.contains(ch);
            boolean miss = over && !entering && !guessed.contains(ch);
            letter(c, k, id, x, cy, slot, show ? String.valueOf(ch) : null, miss, i);
        }
    }

    private void letter(Canvas c, BoardLook k, String id, float x, float cy, float slot, String ch, boolean miss, int i) {
        float u = k.u, cx = x + slot / 2, s = slot;
        int col = miss ? missed() : ink();
        k.reset();
        switch (id) {
            case "classic":
                k.text95(c, ch == null ? "_" : ch, cx, cy, s * 0.6f, miss ? 0xFF800000 : 0xFF000000, true);
                return;
            case "retro": case "cozy": {
                float in = s * 0.07f, top = cy - s * 0.55f, bot = cy + s * 0.5f;
                if (ch == null) {   // an empty slot in the board
                    k.r.set(x + in, top, x + s - in, bot);
                    k.p.setColor(id.equals("retro") ? 0x2A5A3418 : 0x55000000);
                    c.drawRoundRect(k.r, s * 0.14f, s * 0.14f, k.p);
                    return;
                }
                int tile = id.equals("retro") ? (miss ? BoardLook.R_ORANGE : BoardLook.R_MUSTARD) : (miss ? 0xFFC0603A : 0xFFE8C890);
                k.chunky(c, x + in, top, x + s - in, bot - u * 3, s * 0.14f, tile);
                k.tx.setTypeface(id.equals("retro") ? BoardLook.COND : BoardLook.SERIF_BOLD);
                k.tx.setTextSize(s * 0.62f);
                k.tx.setColor(id.equals("retro") ? (miss ? BoardLook.R_CREAM : BoardLook.R_BROWN) : 0xFF3A2414);
                k.text(c, ch, cx, (top + bot - u * 3) / 2);
                return;
            }
            case "space": {
                float top = cy - s * 0.58f, bot = cy + s * 0.5f, in = s * 0.08f;
                k.r.set(x + in, top, x + s - in, bot);
                k.p.setColor(0xFF0A1210);
                c.drawRoundRect(k.r, u * 3, u * 3, k.p);
                k.ln.setStrokeWidth(Math.max(1f, u));
                k.ln.setColor(0x667CF29A);
                c.drawRoundRect(k.r, u * 3, u * 3, k.ln);
                if (ch == null) {
                    k.p.setColor(0x557CF29A);
                    c.drawRect(cx - s * 0.18f, bot - s * 0.2f, cx + s * 0.18f, bot - s * 0.15f, k.p);
                    return;
                }
                k.tx.setTypeface(BoardLook.MONO_BOLD);
                k.tx.setTextSize(s * 0.6f);
                int lc = miss ? BoardLook.S_RED : BoardLook.S_AMBER;
                k.tx.setColor(lc);
                k.tx.setShadowLayer(u * 6, 0, 0, BoardLook.a(lc, 0xAA));
                k.text(c, ch, cx, (top + bot) / 2);
                return;
            }
            case "cs":
                k.tx.setTypeface(BoardLook.MONO_BOLD);
                k.tx.setTextSize(s * 0.75f);
                k.glow(true, ch == null ? skin().inkDim : miss ? 0xFFFFFFFF : skin().ink);
                if (miss) k.tx.setColor(skin().inkDim);
                k.text(c, ch == null ? "_" : ch, cx, cy);
                return;
            default: {
                // a line to write on, and the letter above it
                float base = cy + s * 0.42f;
                k.ln.setStrokeWidth(id.equals("minimal") ? Math.max(1f, u) : id.equals("art") ? u * 1.5f : u * 2.5f);
                k.ln.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                k.ln.setColor(BoardLook.a(ink(), id.equals("aurora") ? 0x99 : 0xCC));
                c.drawLine(cx - s * 0.34f, base, cx + s * 0.34f, base, k.ln);
                if (ch == null) return;
                k.tx.setTypeface(id.equals("art") ? BoardLook.SERIF : id.equals("minimal") ? BoardLook.SERIF : id.equals("light") ? BoardLook.SANS : BoardLook.SANS_LIGHT);
                k.tx.setTextSize(s * 0.74f);
                k.tx.setColor(col);
                if (id.equals("aurora")) k.tx.setShadowLayer(u * 8, 0, 0, BoardLook.a(col, 0x88));
                c.drawText(ch, cx, base - s * 0.14f, k.tx);
            }
        }
    }

    // --- the letter picker

    private void drawKeys(Canvas c, BoardLook k, String id, float L, float R, float ky, float kw, float kh, float gap) {
        for (int r = 0; r < 4; r++) {
            int cnt = rowLength(r);
            float rowW = cnt * kw + (cnt - 1) * gap, x0 = (L + R) / 2 - rowW / 2, y = ky + r * (kh + gap);
            if (id.equals("classic")) x0 = Math.round(x0 / k.px) * k.px;
            for (int q = 0; q < cnt; q++) {
                float x = x0 + q * (kw + gap);
                String label;
                int state = 0;   // 0 free, 1 found, 2 missed
                if (q < KEYS[r].length()) {
                    char ch = KEYS[r].charAt(q);
                    label = String.valueOf(ch);
                    if (!entering && guessed.contains(ch)) state = word.indexOf(ch) >= 0 ? 1 : 2;
                } else label = q == KEYS[r].length() ? "⌫" : "DONE";
                boolean cur = !over && r == kr && q == kc;
                key(c, k, id, x, y, kw, kh, label, state, cur);
            }
        }
    }

    private void key(Canvas c, BoardLook k, String id, float x, float y, float w, float h, String label, int state, boolean cur) {
        float u = k.u, cx = x + w / 2, cy = y + h / 2;
        float ts = h * (label.length() > 1 ? 0.36f : 0.58f);
        float p = cur ? pulse() : 0;
        k.reset();
        switch (id) {
            case "classic": {
                k.button95(c, x, y, x + w, y + h, state != 0, BoardLook.W_FACE);
                float o = state != 0 ? k.px : 0;
                if (state == 2) k.disabled95(c, label, cx + o, cy + o, ts);
                else k.text95(c, label, cx + o, cy + o, ts, state == 1 ? BoardLook.W_NAVY : 0xFF000000, true);
                if (cur) {
                    k.edge(c, x - k.px, y - k.px, x + w + k.px, y + h + k.px, 0xFF000000, 0xFF000000);
                    k.focus95(c, x + k.px * 4, y + k.px * 4, x + w - k.px * 4, y + h - k.px * 4, 0xFF000000);
                }
                return;
            }
            case "cs": {
                if (cur) {
                    k.p.setColor(BoardLook.a(skin().accent, (int) (190 + 65 * p)));
                    c.drawRect(x, y, x + w, y + h, k.p);
                }
                k.tx.setTypeface(BoardLook.MONO_BOLD);
                k.tx.setTextSize(ts);
                int col = cur ? skin().onAccent : state == 2 ? 0xFF1E6A34 : state == 1 ? 0xFFFFFFFF : skin().ink;
                if (cur) k.glow(false, 0); else k.glow(state != 2, col);
                k.tx.setColor(col);
                String t = label.length() > 1 ? label.equals("DONE") ? "[OK]" : "[<]" : "[" + (state == 2 ? "-" : label) + "]";
                k.text(c, t, cx, cy);
                k.glow(false, 0);
                return;
            }
            case "space": {
                k.r.set(x, y, x + w, y + h);
                k.p.setShader(new android.graphics.LinearGradient(0, y, 0, y + h, 0xFF2A2F34, 0xFF14171A, android.graphics.Shader.TileMode.CLAMP));
                c.drawRoundRect(k.r, u * 4, u * 4, k.p);
                k.p.setShader(null);
                k.ln.setStrokeWidth(Math.max(1f, u));
                k.ln.setColor(0x80B8BEC4);
                c.drawRoundRect(k.r, u * 4, u * 4, k.ln);
                // a status LED in the corner
                if (state != 0) k.led(c, x + w - u * 6, y + u * 6, u * 1.8f, state == 1 ? BoardLook.S_GREEN : BoardLook.S_RED);
                k.tx.setTypeface(BoardLook.MONO_BOLD);
                k.tx.setTextSize(ts);
                int col = state == 0 ? 0xFFE8EAE6 : state == 1 ? BoardLook.S_GREEN : 0x66E8EAE6;
                k.tx.setColor(col);
                k.text(c, label, cx, cy);
                if (cur) k.brackets(c, x - u * 3, y - u * 3, x + w + u * 3, y + h + u * 3, BoardLook.a(BoardLook.S_AMBER, (int) (170 + 85 * p)), w * 0.22f);
                return;
            }
            case "retro": case "cozy": {
                boolean retro = id.equals("retro");
                int face = retro ? (state == 1 ? BoardLook.R_MUSTARD : state == 2 ? 0xFFB89A78 : BoardLook.R_CREAM)
                        : (state == 1 ? 0xFFE8C890 : state == 2 ? 0xFF8A6A4A : 0xFFE8D2A8);
                if (cur) {
                    int glow = retro ? 0xFFFFB347 : 0xFFFFB04A;
                    k.r.set(x - u * 4, y - u * 4, x + w + u * 4, y + h + u * 4);
                    k.p.setColor(BoardLook.a(glow, (int) (120 + 110 * p)));
                    k.p.setShadowLayer(u * 12, 0, 0, BoardLook.a(glow, 0xCC));
                    c.drawRoundRect(k.r, h * 0.28f, h * 0.28f, k.p);
                    k.p.clearShadowLayer();
                }
                k.chunky(c, x, y, x + w, y + h - u * 3, h * (retro ? 0.24f : 0.14f), face);
                k.tx.setTypeface(retro ? BoardLook.COND : BoardLook.SERIF_BOLD);
                k.tx.setTextSize(ts);
                k.tx.setColor(state == 2 ? 0x995A3418 : retro ? BoardLook.R_BROWN : 0xFF3A2414);
                k.text(c, label, cx, cy - u * 1.5f);
                if (state == 2) {
                    k.ln.setStrokeWidth(u * 3);
                    k.ln.setColor(retro ? BoardLook.R_ORANGE : 0xFFB4361E);
                    c.drawLine(x + w * 0.24f, y + h * 0.72f, x + w * 0.76f, y + h * 0.24f, k.ln);
                }
                return;
            }
            case "art": {
                k.r.set(x, y, x + w, y + h);
                k.p.setColor(state == 2 ? 0x66E6D9BC : 0xFFF8F0DC);
                k.p.setShadowLayer(u * 3, 0, u * 1.5f, 0x40000000);
                c.drawRoundRect(k.r, u * 3, u * 3, k.p);
                k.p.clearShadowLayer();
                k.ln.setStrokeWidth(u * (cur ? 2.5f : 1));
                k.ln.setColor(cur ? BoardLook.a(0xFFB8893A, (int) (170 + 85 * p)) : 0x809A7028);
                c.drawRoundRect(k.r, u * 3, u * 3, k.ln);
                k.tx.setTypeface(BoardLook.SERIF);
                k.tx.setTextSize(ts);
                k.tx.setColor(state == 1 ? BoardLook.A_GREEN : state == 2 ? 0x803A2A1E : BoardLook.A_INK);
                k.text(c, label, cx, cy);
                if (state == 2) {
                    k.p.setColor(BoardLook.A_OXBLOOD);
                    c.drawPath(k.taper(x + w * 0.2f, y + h * 0.75f, x + w * 0.8f, y + h * 0.25f, u * 3), k.p);
                }
                return;
            }
            case "minimal": {
                int ink = skin().ink;
                k.tx.setTypeface(BoardLook.SERIF);
                k.tx.setTextSize(ts);
                k.tx.setColor(state == 0 ? ink : state == 1 ? ink : BoardLook.a(ink, 0x55));
                k.text(c, label, cx, cy);
                if (state == 1) {
                    k.ln.setStrokeWidth(Math.max(1f, u));
                    k.ln.setColor(ink);
                    c.drawLine(cx - w * 0.18f, y + h * 0.86f, cx + w * 0.18f, y + h * 0.86f, k.ln);
                }
                if (state == 2) {
                    k.ln.setStrokeWidth(u * 1.3f);
                    k.ln.setColor(skin().p2);
                    c.drawLine(x + w * 0.28f, y + h * 0.72f, x + w * 0.72f, y + h * 0.28f, k.ln);
                }
                if (cur) {
                    k.ln.setStrokeWidth(Math.max(1f, u * 1.2f));
                    k.ln.setColor(BoardLook.a(ink, (int) (130 + 125 * p)));
                    c.drawRect(x + u * 2, y + u * 2, x + w - u * 2, y + h - u * 2, k.ln);
                }
                return;
            }
            case "light": {
                k.r.set(x, y, x + w, y + h);
                int fillCol = cur ? 0xFF1C1C1E : state == 1 ? 0xFFDCE8F6 : state == 2 ? 0xFFF1EEEA : 0xFFFFFFFF;
                k.p.setColor(fillCol);
                if (state != 2) k.p.setShadowLayer(u * 4, 0, u * 1.5f, 0x26000000);
                c.drawRoundRect(k.r, h * 0.3f, h * 0.3f, k.p);
                k.p.clearShadowLayer();
                k.tx.setTypeface(BoardLook.MEDIUM);
                k.tx.setTextSize(ts);
                k.tx.setColor(cur ? 0xFFFFFFFF : state == 1 ? skin().p1 : state == 2 ? 0x591C1C1E : 0xFF1C1C1E);
                k.text(c, label, cx, cy);
                if (state == 2 && !cur) {
                    k.ln.setStrokeWidth(u * 2);
                    k.ln.setColor(BoardLook.a(skin().p2, 0xCC));
                    c.drawLine(x + w * 0.28f, y + h * 0.72f, x + w * 0.72f, y + h * 0.28f, k.ln);
                }
                return;
            }
            default: {   // aurora glass keys
                k.r.set(x, y, x + w, y + h);
                int fillCol = cur ? BoardLook.a(0xFFFFFFFF, (int) (200 + 55 * p)) : state == 1 ? BoardLook.a(skin().p1, 0x59) : state == 2 ? 0x0FFFFFFF : 0x2EFFFFFF;
                k.p.setColor(fillCol);
                if (cur) k.p.setShadowLayer(u * 12, 0, 0, 0x99FFFFFF);
                c.drawRoundRect(k.r, h * 0.32f, h * 0.32f, k.p);
                k.p.clearShadowLayer();
                if (!cur) {
                    k.ln.setStrokeWidth(u);
                    k.ln.setColor(state == 2 ? 0x1AFFFFFF : 0x40FFFFFF);
                    c.drawRoundRect(k.r, h * 0.32f, h * 0.32f, k.ln);
                }
                k.tx.setTypeface(BoardLook.SANS);
                k.tx.setTextSize(ts);
                k.tx.setColor(cur ? 0xFF1C1C1E : state == 2 ? 0x4DFFFFFF : 0xFFFFFFFF);
                k.text(c, label, cx, cy);
                if (state == 2 && !cur) {
                    k.ln.setStrokeWidth(u * 2);
                    k.ln.setColor(BoardLook.a(skin().p2, 0xCC));
                    c.drawLine(x + w * 0.3f, y + h * 0.7f, x + w * 0.7f, y + h * 0.3f, k.ln);
                }
            }
        }
    }
}
