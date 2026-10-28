package home.aurora.games;

import java.util.HashSet;
import java.util.List;

/**
 * Plain Klondike logic for {@link Solitaire}: whether a deal can be won, and whether a
 * table has any move left. No Android, so it can be tested on its own.
 *
 * Cards are 0..51: suit = card / 13 (hearts and diamonds are 1 and 2, the red ones),
 * rank = card % 13 (0 is the ace). Lists are bottom first; the stock's top is its last card.
 */
public final class Klondike {

    private Klondike() { }

    static int suit(int card) { return card / 13; }
    static int rank(int card) { return card % 13; }
    static boolean red(int card) { return suit(card) == 1 || suit(card) == 2; }

    /** Can [card] go on a tableau column whose top is [top] (-1 for an empty column)? */
    static boolean onTableau(int card, int top) {
        if (top < 0) return rank(card) == 12;
        return red(top) != red(card) && rank(top) == rank(card) + 1;
    }

    // ------------------------------------------------------------------ is this deal winnable?

    /**
     * Deal [deck] the way Solitaire does (column c takes c + 1 cards from the end, the rest is
     * the stock) and look for a win, drawing [draw] cards at a time. True means a win was found;
     * false means none was found within [maxNodes] positions (it may still be winnable).
     * The search sticks to sensible moves, so a true answer is always a real win.
     */
    public static boolean winnable(List<Integer> deck, int draw, int maxNodes) {
        int[] d = new int[deck.size()];
        for (int i = 0; i < d.length; i++) d[i] = deck.get(i);
        State s = new State();
        int end = d.length;
        for (int c = 0; c < 7; c++) {
            for (int k = 0; k <= c; k++) s.tab[c][s.len[c]++] = d[--end];
            s.down[c] = c;
        }
        for (int i = 0; i < end; i++) s.stock[s.slen++] = d[i];
        Search search = new Search(draw, maxNodes);
        return search.solve(s);
    }

    private static final class State {
        final int[][] tab = new int[7][20];
        final int[] len = new int[7], down = new int[7];
        final int[] fnd = new int[4];               // cards on each suit's foundation
        final int[] stock = new int[24], waste = new int[24];
        int slen, wlen;

        State copy() {
            State o = new State();
            for (int c = 0; c < 7; c++) System.arraycopy(tab[c], 0, o.tab[c], 0, len[c]);
            System.arraycopy(len, 0, o.len, 0, 7);
            System.arraycopy(down, 0, o.down, 0, 7);
            System.arraycopy(fnd, 0, o.fnd, 0, 4);
            System.arraycopy(stock, 0, o.stock, 0, slen);
            System.arraycopy(waste, 0, o.waste, 0, wlen);
            o.slen = slen;
            o.wlen = wlen;
            return o;
        }

        int top(int c) { return len[c] == 0 ? -1 : tab[c][len[c] - 1]; }

        boolean toFoundation(int card) { return fnd[suit(card)] == rank(card); }

        /** A popped column turns its new top card face up. */
        void popped(int c) { if (down[c] >= len[c]) down[c] = Math.max(0, len[c] - 1); }

        String key() {
            StringBuilder b = new StringBuilder(120);
            for (int c = 0; c < 7; c++) {
                b.append((char) down[c]);
                for (int i = 0; i < len[c]; i++) b.append((char) (tab[c][i] + 64));
                b.append('|');
            }
            for (int i = 0; i < slen; i++) b.append((char) (stock[i] + 64));
            b.append('/');
            for (int i = 0; i < wlen; i++) b.append((char) (waste[i] + 64));
            return b.toString();
        }

        /** Nothing face down and nothing left to turn over: the rest plays itself out. */
        boolean won() {
            if (slen > 0 || wlen > 0) return false;
            for (int c = 0; c < 7; c++) if (down[c] > 0) return false;
            return true;
        }
    }

    private static final class Search {
        final int draw, maxNodes;
        final HashSet<String> seen = new HashSet<>();

        Search(int draw, int maxNodes) { this.draw = draw; this.maxNodes = maxNodes; }

        boolean solve(State s) {
            if (s.won()) return true;
            if (seen.size() >= maxNodes || !seen.add(s.key())) return false;

            // 1. Tableau and waste tops to the foundations.
            for (int c = 0; c < 7; c++) {
                int t = s.top(c);
                if (t >= 0 && s.toFoundation(t)) {
                    State n = s.copy();
                    n.len[c]--;
                    n.fnd[suit(t)]++;
                    n.popped(c);
                    if (solve(n)) return true;
                }
            }
            if (s.wlen > 0 && s.toFoundation(s.waste[s.wlen - 1])) {
                State n = s.copy();
                int w = n.waste[--n.wlen];
                n.fnd[suit(w)]++;
                if (solve(n)) return true;
            }
            // 2. A column's whole face-up run onto another column, when it uncovers a card.
            for (int c = 0; c < 7; c++) {
                if (s.len[c] == 0 || s.down[c] == 0) continue;
                int base = s.tab[c][s.down[c]];
                for (int to = 0; to < 7; to++) {
                    if (to == c || !onTableau(base, s.top(to))) continue;
                    State n = s.copy();
                    int run = s.len[c] - s.down[c];
                    System.arraycopy(s.tab[c], s.down[c], n.tab[to], n.len[to], run);
                    n.len[to] += run;
                    n.len[c] = s.down[c];
                    n.popped(c);
                    if (solve(n)) return true;
                    if (s.len[to] == 0) break;   // one empty column is as good as another
                }
            }
            // 3. The waste's top card onto a column.
            if (s.wlen > 0) {
                int w = s.waste[s.wlen - 1];
                for (int to = 0; to < 7; to++) {
                    if (!onTableau(w, s.top(to))) continue;
                    State n = s.copy();
                    n.wlen--;
                    n.tab[to][n.len[to]++] = w;
                    if (solve(n)) return true;
                    if (s.len[to] == 0) break;
                }
            }
            // 4. Turn over the stock, or start it again from the waste.
            if (s.slen > 0) {
                State n = s.copy();
                for (int i = 0; i < draw && n.slen > 0; i++) n.waste[n.wlen++] = n.stock[--n.slen];
                if (solve(n)) return true;
            } else if (s.wlen > 0) {
                State n = s.copy();
                for (int i = s.wlen - 1; i >= 0; i--) n.stock[n.slen++] = s.waste[i];
                n.wlen = 0;
                if (solve(n)) return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ any move left?

    /**
     * Is there still a useful move on this table? Counts cards that can reach a foundation,
     * face-up runs that can move to uncover a card or fill an empty column from a deeper one,
     * and any card the stock can still bring up (cycling through it as often as it likes).
     */
    public static boolean anyMove(List<Integer>[] tab, int[] down, List<Integer>[] found,
                                  List<Integer> stock, List<Integer> waste, int draw) {
        int[] fnd = new int[4];
        for (List<Integer> f : found) if (!f.isEmpty()) fnd[suit(f.get(0))] = f.size();
        int[] tops = new int[7];
        for (int c = 0; c < 7; c++) tops[c] = tab[c].isEmpty() ? -1 : tab[c].get(tab[c].size() - 1);

        for (int c = 0; c < 7; c++) {
            if (tab[c].isEmpty()) continue;
            int t = tops[c];
            if (fnd[suit(t)] == rank(t)) return true;
            // A face-up card (and what's on it) moving somewhere it does some good: it uncovers
            // a face-down card, empties a column, or lets the card it sat on go to a foundation.
            for (int i = down[c]; i < tab[c].size(); i++) {
                int card = tab[c].get(i);
                boolean useful = i == 0 || i == down[c] && down[c] > 0
                    || i > down[c] && fnd[suit(tab[c].get(i - 1))] == rank(tab[c].get(i - 1));
                if (!useful) continue;
                for (int to = 0; to < 7; to++) {
                    if (to == c || !onTableau(card, tops[to])) continue;
                    if (i == 0 && tops[to] < 0) continue;   // a whole column to an empty one changes nothing
                    return true;
                }
            }
        }
        // Every card the stock and waste can bring to the top, in order.
        for (int card : reachable(stock, waste, draw)) {
            if (fnd[suit(card)] == rank(card)) return true;
            for (int to = 0; to < 7; to++) if (onTableau(card, tops[to])) return true;
        }
        return false;
    }

    /** The cards that can turn up on the waste, cycling the stock (top of the waste first). */
    private static java.util.List<Integer> reachable(List<Integer> stock, List<Integer> waste, int draw) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (!waste.isEmpty()) out.add(waste.get(waste.size() - 1));
        // The talon in dealing order: what's left in the stock, then the waste again from the bottom.
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i = stock.size() - 1; i >= 0; i--) order.add(stock.get(i));
        java.util.List<Integer> again = new java.util.ArrayList<>(waste);
        if (draw <= 1) { out.addAll(order); out.addAll(again); return out; }
        // Drawing three, only every third card shows: this pass, then a full pass from the start.
        for (int i = Math.min(draw, order.size()) - 1; i < order.size(); i += draw) out.add(order.get(i));
        if (order.size() % draw != 0 && !order.isEmpty()) out.add(order.get(order.size() - 1));
        java.util.List<Integer> all = new java.util.ArrayList<>(again);
        all.addAll(order);
        for (int i = Math.min(draw, all.size()) - 1; i < all.size(); i += draw) out.add(all.get(i));
        if (all.size() % draw != 0 && !all.isEmpty()) out.add(all.get(all.size() - 1));
        return out;
    }
}
