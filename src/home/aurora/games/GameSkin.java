package home.aurora.games;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import home.aurora.R;

/**
 * The games dressed in the home screen's look. Every game draws with these
 * colors instead of its own, so Chess in Retro sits in cream enamel and walnut,
 * in Computer Science it glows green on black, and so on.
 *
 * Colors are semantic: a board is drawn on {@link #board} inside a frame of
 * {@link #frame}/{@link #frameHi}; squares alternate {@link #squareLight} and
 * {@link #squareDark}; the two sides are {@link #p1} and {@link #p2} (or
 * {@link #pieceLight}/{@link #pieceDark} for chess and checkers); the cursor
 * and highlights use {@link #accent}.
 */
public final class GameSkin {

    /** The home look this skin belongs to. */
    public final String id;
    /** Behind everything; null shows the Aurora wallpaper. */
    public Drawable surface;
    /** Text on the surface, and its quieter form. */
    public int ink, inkDim;
    public Typeface titleFont, textFont;
    /** The cursor, selections and "your move" marks. */
    public int accent;
    /** Text drawn on top of an accent fill. */
    public int onAccent;
    /** Player colors for X/O, discs, lines and boxes. */
    public int p1, p2;
    /** Chess and checkers men. */
    public int pieceLight, pieceDark, pieceEdge;
    /** A board or table the game is played on, and its frame. */
    public int board, boardEdge, frame, frameHi;
    public int squareLight, squareDark;
    /** Cells waiting to be opened/filled (Minesweeper, 2048, Connect 4 holes). */
    public int cell, cellHi, cellLo, cellOpen;
    /** Solitaire: the table and the backs of the cards. */
    public int felt, cardBack, cardBackHi, cardFace, cardRed, cardBlack;
    /** 2048 tiles from 2 upward (11 steps) and the text on them. */
    public int[] tiles;
    public int tileInk, tileInkLight;
    /** Hub cards and side panels. */
    public int panel, panelEdge, panelFocus;
    /** Corner radius for boards, cells and cards, as a fraction of a cell. */
    public float round;
    /** Phosphor screens: everything single-colored and glowing. */
    public boolean glow;
    /** Windows 95: square corners, four-line bevels, no smoothing. */
    public boolean bevel;

    private GameSkin(String id) { this.id = id; }

    private static final Typeface SANS_LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface SANS_THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private static final Typeface SANS = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface SANS_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface COND = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private static final Typeface SERIF = Typeface.create("serif", Typeface.NORMAL);
    private static final Typeface SERIF_BOLD = Typeface.create("serif", Typeface.BOLD);
    private static final Typeface SERIF_ITALIC = Typeface.create("serif", Typeface.ITALIC);
    private static final Typeface MONO = Typeface.MONOSPACE;

    /** The 2048 ramp most looks share: warm to hot. */
    private static final int[] WARM_TILES = {0xFFEEE4DA, 0xFFEDE0C8, 0xFFF2B179, 0xFFF59563, 0xFFF67C5F, 0xFFF65E3B,
            0xFFEDCF72, 0xFFEDCC61, 0xFFEDC850, 0xFFEDC53F, 0xFFEDC22E};

    /** The skin for a home look id (as in HomeTheme.IDS; Day & Night passes "light" or "cozy"). */
    public static GameSkin forTheme(Context c, String themeId, boolean night) {
        GameSkin s = new GameSkin(themeId == null ? "aurora" : themeId);
        switch (s.id) {
            case "classic": {
                s.surface = new Solid(0xFF008080);
                s.ink = 0xFFFFFFFF; s.inkDim = 0xCCFFFFFF;
                s.titleFont = SANS_BOLD; s.textFont = SANS;
                s.accent = 0xFF000080; s.onAccent = 0xFFFFFFFF;
                s.p1 = 0xFF000080; s.p2 = 0xFF800000;
                s.pieceLight = 0xFFFFFFFF; s.pieceDark = 0xFF000000; s.pieceEdge = 0xFF808080;
                s.board = 0xFFC0C0C0; s.boardEdge = 0xFF808080; s.frame = 0xFFC0C0C0; s.frameHi = 0xFFFFFFFF;
                s.squareLight = 0xFFFFFFFF; s.squareDark = 0xFF808080;
                s.cell = 0xFFC0C0C0; s.cellHi = 0xFFFFFFFF; s.cellLo = 0xFF808080; s.cellOpen = 0xFFC0C0C0;
                s.felt = 0xFF008000; s.cardBack = 0xFF000080; s.cardBackHi = 0xFF0000A8; s.cardFace = 0xFFFFFFFF;
                s.cardRed = 0xFFFF0000; s.cardBlack = 0xFF000000;
                s.tiles = new int[]{0xFFFFFFFF, 0xFFFFFF80, 0xFFFFFF00, 0xFF80C0C0, 0xFF00C0C0, 0xFF008080,
                        0xFFC0C000, 0xFF808000, 0xFFFF8000, 0xFFFF0000, 0xFF800000};
                s.tileInk = 0xFF000000; s.tileInkLight = 0xFFFFFFFF;
                s.panel = 0xFFC0C0C0; s.panelEdge = 0xFF000000; s.panelFocus = 0xFF000080;
                s.round = 0f; s.bevel = true;
                break;
            }
            case "retro": {
                s.surface = new Textured(c, R.drawable.tx_walnut, 0xFF4A2A16, 0x33000000);
                s.ink = 0xFFF6EBD0; s.inkDim = 0xB3F6EBD0;
                s.titleFont = SERIF_BOLD; s.textFont = COND;
                s.accent = 0xFFFFB347; s.onAccent = 0xFF3A2414;
                s.p1 = 0xFFC4552A; s.p2 = 0xFF2F6A6A;
                s.pieceLight = 0xFFF6EBD0; s.pieceDark = 0xFF3A2414; s.pieceEdge = 0xFFB8893A;
                s.board = 0xFFEFE3C6; s.boardEdge = 0xFFB9A57E; s.frame = 0xFFE2D3B0; s.frameHi = 0xFFF8F0DC;
                s.squareLight = 0xFFF2E6CA; s.squareDark = 0xFFB98A5A;
                s.cell = 0xFFEFE3C6; s.cellHi = 0xFFF8F0DC; s.cellLo = 0xFFB9A57E; s.cellOpen = 0xFFE2D3B0;
                s.felt = 0xFF3F5E3A; s.cardBack = 0xFFC4552A; s.cardBackHi = 0xFFE07A4A; s.cardFace = 0xFFF8F0DC;
                s.cardRed = 0xFFB4361E; s.cardBlack = 0xFF2B1A10;
                s.tiles = WARM_TILES; s.tileInk = 0xFF5A4030; s.tileInkLight = 0xFFF8F0DC;
                s.panel = 0xFFEFE3C6; s.panelEdge = 0xFFB9A57E; s.panelFocus = 0xFFFFB347;
                s.round = 0.14f;
                break;
            }
            case "art": {
                s.surface = new Textured(c, R.drawable.art_damask, 0xFF5A1A1E, 0x40000000);
                s.ink = 0xFFF3E7CF; s.inkDim = 0xB3F3E7CF;
                s.titleFont = SERIF_ITALIC; s.textFont = SERIF;
                s.accent = 0xFFE2C27A; s.onAccent = 0xFF2A1408;
                s.p1 = 0xFF2E4A6E; s.p2 = 0xFF9A2A22;
                s.pieceLight = 0xFFF4EEE2; s.pieceDark = 0xFF1C1612; s.pieceEdge = 0xFFC9A24A;
                s.board = 0xFFEDE4D0; s.boardEdge = 0xFF8A6A2A; s.frame = 0xFFB8893A; s.frameHi = 0xFFF2D48A;
                s.squareLight = 0xFFEDE4D0; s.squareDark = 0xFF6B4A33;
                s.cell = 0xFFE6DCC6; s.cellHi = 0xFFF6EFE0; s.cellLo = 0xFFB8A888; s.cellOpen = 0xFFF2EADA;
                s.felt = 0xFF2E3A2E; s.cardBack = 0xFF6E1E22; s.cardBackHi = 0xFFB8893A; s.cardFace = 0xFFF8F2E6;
                s.cardRed = 0xFF9A2A22; s.cardBlack = 0xFF1C1612;
                s.tiles = WARM_TILES; s.tileInk = 0xFF4A3424; s.tileInkLight = 0xFFF8F2E6;
                s.panel = 0xFF1A1412; s.panelEdge = 0xFFB8893A; s.panelFocus = 0xFFE2C27A;
                s.round = 0.04f;
                break;
            }
            case "space": {
                s.surface = new Brushed(0xFF2A2D31, 0xFF16181B);
                s.ink = 0xFFE8EAE6; s.inkDim = 0x99E8EAE6;
                s.titleFont = COND; s.textFont = MONO;
                s.accent = 0xFFFFC56A; s.onAccent = 0xFF1E140A;
                s.p1 = 0xFF6FE08A; s.p2 = 0xFFFFA24A;
                s.pieceLight = 0xFFE8EAE6; s.pieceDark = 0xFF2A2D31; s.pieceEdge = 0xFF8A9096;
                s.board = 0xFF0C0E10; s.boardEdge = 0xFF4A4F55; s.frame = 0xFF5A6066; s.frameHi = 0xFFB8BEC4;
                s.squareLight = 0xFF3A4046; s.squareDark = 0xFF1A1D20;
                s.cell = 0xFF23272B; s.cellHi = 0xFF3C4248; s.cellLo = 0xFF0E1012; s.cellOpen = 0xFF101316;
                s.felt = 0xFF14181C; s.cardBack = 0xFF3A4046; s.cardBackHi = 0xFFFFC56A; s.cardFace = 0xFFE8EAE6;
                s.cardRed = 0xFFD8582E; s.cardBlack = 0xFF1A1D20;
                s.tiles = new int[]{0xFF3A4046, 0xFF4A5056, 0xFF6FE08A, 0xFF52C070, 0xFFFFC56A, 0xFFFFA24A,
                        0xFFE88A3A, 0xFFD8582E, 0xFFE8EAE6, 0xFFFFE6B0, 0xFFFFFFFF};
                s.tileInk = 0xFF0C0E10; s.tileInkLight = 0xFFE8EAE6;
                s.panel = 0xFF1A1D20; s.panelEdge = 0xFF5A6066; s.panelFocus = 0xFFFFC56A;
                s.round = 0.1f;
                break;
            }
            case "cozy": {
                s.surface = new Photo(c, R.drawable.cozy_fire_blur, 0x99140A04);
                s.ink = 0xFFF6E6CC; s.inkDim = 0xB3F6E6CC;
                s.titleFont = SERIF_BOLD; s.textFont = SERIF;
                s.accent = 0xFFFFB04A; s.onAccent = 0xFF2A1406;
                s.p1 = 0xFFE07A3A; s.p2 = 0xFF8FB0A8;
                s.pieceLight = 0xFFF2E2C4; s.pieceDark = 0xFF2A1A10; s.pieceEdge = 0xFFB8893A;
                s.board = 0xFF5A3A22; s.boardEdge = 0xFF2A1A10; s.frame = 0xFF6E4423; s.frameHi = 0xFFB07840;
                s.squareLight = 0xFFD9B98A; s.squareDark = 0xFF6E4423;
                s.cell = 0xFF5A3A22; s.cellHi = 0xFF8A5A34; s.cellLo = 0xFF2A1A10; s.cellOpen = 0xFF3A2414;
                s.felt = 0xFF2E4A30; s.cardBack = 0xFF8A2A1E; s.cardBackHi = 0xFFE0A050; s.cardFace = 0xFFF8EEDC;
                s.cardRed = 0xFFB4361E; s.cardBlack = 0xFF2A1A10;
                s.tiles = WARM_TILES; s.tileInk = 0xFF5A3A22; s.tileInkLight = 0xFFFFF6E8;
                s.panel = 0xCC1A0E06; s.panelEdge = 0x80FFB04A; s.panelFocus = 0xFFFFB04A;
                s.round = 0.16f;
                break;
            }
            case "minimal": {
                boolean dark = night;
                s.surface = dark ? new Solid(0xFF0E0E0E) : new Photo(c, R.drawable.minimal_paper, 0x00000000);
                int ink = dark ? 0xFFEDEAE4 : 0xFF161616;
                int paper = dark ? 0xFF0E0E0E : 0xFFF1EDE4;
                s.ink = ink; s.inkDim = (ink & 0xFFFFFF) | 0x99000000;
                s.titleFont = SERIF_ITALIC; s.textFont = SANS_LIGHT;
                s.accent = ink; s.onAccent = paper;
                s.p1 = ink; s.p2 = 0xFFB4361E;
                s.pieceLight = paper; s.pieceDark = ink; s.pieceEdge = ink;
                s.board = paper; s.boardEdge = (ink & 0xFFFFFF) | 0x40000000; s.frame = paper; s.frameHi = paper;
                s.squareLight = paper; s.squareDark = dark ? 0xFF2A2A2A : 0xFFD9D3C6;
                s.cell = dark ? 0xFF1C1C1C : 0xFFE6E0D4; s.cellHi = s.cell; s.cellLo = s.cell; s.cellOpen = paper;
                s.felt = paper; s.cardBack = ink; s.cardBackHi = ink; s.cardFace = dark ? 0xFF1C1C1C : 0xFFFFFFFF;
                s.cardRed = 0xFFB4361E; s.cardBlack = ink;
                s.tiles = dark ? new int[]{0xFF1C1C1C, 0xFF262626, 0xFF333333, 0xFF444444, 0xFF5A5A5A, 0xFF777777,
                        0xFF999999, 0xFFBBBBBB, 0xFFDDDDDD, 0xFFB4361E, 0xFFEDEAE4}
                        : new int[]{0xFFE6E0D4, 0xFFDAD3C4, 0xFFC9C0AE, 0xFFB3A994, 0xFF978C76, 0xFF7A705C,
                        0xFF5E5646, 0xFF443E32, 0xFF2C2822, 0xFFB4361E, 0xFF161616};
                s.tileInk = ink; s.tileInkLight = paper;
                s.panel = paper; s.panelEdge = (ink & 0xFFFFFF) | 0x33000000; s.panelFocus = ink;
                s.round = 0f;
                break;
            }
            case "cs": {
                s.surface = new Phosphor();
                int g = 0xFF46F07A;
                s.ink = 0xFFB4FFC8; s.inkDim = 0xFF22A048;
                s.titleFont = MONO; s.textFont = MONO;
                s.accent = g; s.onAccent = 0xFF02140A;
                s.p1 = g; s.p2 = 0xFFB4FFC8;
                s.pieceLight = 0xFFB4FFC8; s.pieceDark = 0xFF22A048; s.pieceEdge = g;
                s.board = 0xFF020A04; s.boardEdge = 0xFF22A048; s.frame = 0xFF0E4A20; s.frameHi = g;
                s.squareLight = 0xFF0C2A14; s.squareDark = 0xFF020A04;
                s.cell = 0xFF06160A; s.cellHi = 0xFF0E4A20; s.cellLo = 0xFF020A04; s.cellOpen = 0xFF020A04;
                s.felt = 0xFF020A04; s.cardBack = 0xFF0E4A20; s.cardBackHi = g; s.cardFace = 0xFF06160A;
                s.cardRed = 0xFFB4FFC8; s.cardBlack = g;
                s.tiles = new int[]{0xFF06160A, 0xFF0A2410, 0xFF0E3418, 0xFF124420, 0xFF165428, 0xFF1A6630,
                        0xFF1E7838, 0xFF22A048, 0xFF34C860, g, 0xFFB4FFC8};
                s.tileInk = 0xFFB4FFC8; s.tileInkLight = 0xFF02140A;
                s.panel = 0xFF020A04; s.panelEdge = 0xFF22A048; s.panelFocus = g;
                s.round = 0f; s.glow = true;
                break;
            }
            case "light": {
                s.surface = new Photo(c, R.drawable.light_room_blur, 0x59FFFFFF);
                s.ink = 0xFF1C1C1E; s.inkDim = 0x991C1C1E;
                s.titleFont = SANS_LIGHT; s.textFont = SANS;
                s.accent = 0xFF1C1C1E; s.onAccent = 0xFFFFFFFF;
                s.p1 = 0xFF2F6DB8; s.p2 = 0xFFD8573A;
                s.pieceLight = 0xFFFFFFFF; s.pieceDark = 0xFF2A2A2E; s.pieceEdge = 0xFF8E8E93;
                s.board = 0xF2FFFFFF; s.boardEdge = 0x26000000; s.frame = 0xFFFFFFFF; s.frameHi = 0xFFFFFFFF;
                s.squareLight = 0xFFF4F1EC; s.squareDark = 0xFFC9BCA8;
                s.cell = 0xFFECE8E2; s.cellHi = 0xFFFFFFFF; s.cellLo = 0xFFD8D2C8; s.cellOpen = 0xFFFFFFFF;
                s.felt = 0xFFE9E4DA; s.cardBack = 0xFF2F6DB8; s.cardBackHi = 0xFF6A9BD8; s.cardFace = 0xFFFFFFFF;
                s.cardRed = 0xFFD8402F; s.cardBlack = 0xFF1C1C1E;
                s.tiles = WARM_TILES; s.tileInk = 0xFF5A5048; s.tileInkLight = 0xFFFFFFFF;
                s.panel = 0xE6FFFFFF; s.panelEdge = 0x1F000000; s.panelFocus = 0xFF1C1C1E;
                s.round = 0.18f;
                break;
            }
            default: { // aurora: frosted glass over the wallpaper
                s.surface = null;
                s.ink = 0xFFFFFFFF; s.inkDim = 0xB3FFFFFF;
                s.titleFont = SANS_THIN; s.textFont = SANS_LIGHT;
                s.accent = 0xFFFFFFFF; s.onAccent = 0xFF1C1C1E;
                s.p1 = 0xFF8FC7FF; s.p2 = 0xFFFFB27A;
                s.pieceLight = 0xFFF7F5F0; s.pieceDark = 0xFF1E1E22; s.pieceEdge = 0xCCE6E6EA;
                s.board = 0x40FFFFFF; s.boardEdge = 0x40FFFFFF; s.frame = 0x33FFFFFF; s.frameHi = 0x66FFFFFF;
                s.squareLight = 0x4DFFFFFF; s.squareDark = 0x1AFFFFFF;
                s.cell = 0x33FFFFFF; s.cellHi = 0x4DFFFFFF; s.cellLo = 0x1AFFFFFF; s.cellOpen = 0x14FFFFFF;
                s.felt = 0x26FFFFFF; s.cardBack = 0xFF3A4A6A; s.cardBackHi = 0xFF8FC7FF; s.cardFace = 0xFFFFFFFF;
                s.cardRed = 0xFFD8402F; s.cardBlack = 0xFF1C1C1E;
                s.tiles = WARM_TILES; s.tileInk = 0xFF5A5048; s.tileInkLight = 0xFFFFFFFF;
                s.panel = 0x33FFFFFF; s.panelEdge = 0x4DFFFFFF; s.panelFocus = 0xFFFFFFFF;
                s.round = 0.2f;
                break;
            }
        }
        return s;
    }

    /** 2048: the tile color for a value (2, 4, 8 …). */
    public int tile(int value) {
        int i = 0;
        while ((2 << i) < value && i < tiles.length - 1) i++;
        return tiles[i];
    }

    /** A color with its alpha scaled by {@code a}. */
    public static int alpha(int color, float a) {
        return (Math.round((color >>> 24) * a) << 24) | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ surfaces

    private abstract static class Surface extends Drawable {
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private static final class Solid extends Surface {
        private final int color;
        Solid(int c) { color = c; }
        @Override public void draw(Canvas c) { c.drawColor(color); }
    }

    /** A photo filling the screen, with a tint laid over it. */
    private static final class Photo extends Surface {
        private final Bitmap bmp;
        private final int tint;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        Photo(Context c, int res, int tint) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            bmp = BitmapFactory.decodeResource(c.getResources(), res, o);
            this.tint = tint;
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawBitmap(bmp, null, b, p);
            if (tint != 0) c.drawColor(tint);
            // a soft vignette keeps the eye on the board
            Paint v = new Paint();
            v.setShader(new RadialGradient(b.exactCenterX(), b.exactCenterY(), Math.max(b.width(), b.height()) * 0.7f,
                    0x00000000, 0x59000000, Shader.TileMode.CLAMP));
            c.drawRect(b, v);
        }
    }

    /** A tiled texture (wood, damask) under a tint, lit from above. */
    private static final class Textured extends Surface {
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final int base, tint;
        Textured(Context c, int res, int base, int tint) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            Bitmap b = BitmapFactory.decodeResource(c.getResources(), res, o);
            BitmapShader s = new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Matrix m = new Matrix();
            m.setScale(1.2f, 1.2f);
            s.setLocalMatrix(m);
            p.setShader(s);
            this.base = base;
            this.tint = tint;
        }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawColor(base);
            c.drawRect(b, p);
            c.drawColor(tint);
            Paint v = new Paint();
            v.setShader(new RadialGradient(b.exactCenterX(), b.height() * 0.35f, Math.max(b.width(), b.height()) * 0.75f,
                    0x14FFE6C8, 0x8C000000, Shader.TileMode.CLAMP));
            c.drawRect(b, v);
        }
    }

    /** Brushed metal: fine horizontal streaks under a soft top light. */
    private static final class Brushed extends Surface {
        private final int top, bottom;
        private Bitmap streaks;
        Brushed(int top, int bottom) { this.top = top; this.bottom = bottom; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            Paint p = new Paint();
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            if (streaks == null) {
                int w = 512, h = 128;
                streaks = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                int[] px = new int[w * h];
                java.util.Random r = new java.util.Random(5);
                for (int y = 0; y < h; y++) {
                    int row = r.nextInt(18);
                    for (int x = 0; x < w; x++) px[y * w + x] = ((row + r.nextInt(6)) << 24) | 0xFFFFFF;
                }
                streaks.setPixels(px, 0, w, 0, 0, w, h);
            }
            Paint s = new Paint();
            BitmapShader sh = new BitmapShader(streaks, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Matrix m = new Matrix();
            m.setScale(3f, 1f);
            sh.setLocalMatrix(m);
            s.setShader(sh);
            c.drawRect(b, s);
        }
    }

    /** A dark phosphor screen with scanlines. */
    private static final class Phosphor extends Surface {
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawColor(0xFF020A04);
            Paint g = new Paint();
            g.setShader(new RadialGradient(b.exactCenterX(), b.exactCenterY(), Math.max(b.width(), b.height()) * 0.6f,
                    0x1422E05A, 0x0022E05A, Shader.TileMode.CLAMP));
            c.drawRect(b, g);
            Paint l = new Paint();
            l.setColor(0x33000000);
            for (int y = b.top; y < b.bottom; y += 3) c.drawRect(b.left, y + 2, b.right, y + 3, l);
        }
    }
}
