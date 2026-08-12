package nl.hauntedmc.featureframework.paper.ui.inventory.menu.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.Style.Merge;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Word-wrap Adventure Components without serializing to plain/legacy.
 */
public final class ComponentWordWrap {
    private ComponentWordWrap() {
    }

    /**
     * Wrap a component into multiple lines (by character count), preserving styles.
     */
    public static List<Component> wrap(Component input, int width) {
        if (input == null) return List.of();
        if (width <= 1) return List.of(noItalics(input));

        // Flatten to (text, effective Style) runs
        List<Run> runs = flattenToRuns(input);

        // Split into styled words (space kept as separators, not included in length of first word)
        List<Word> words = toWords(runs);

        // Greedy wrap with a tiny rebalance to avoid a 1-word last line
        List<List<Word>> lines = wrapWords(words, width);
        rebalanceTail(lines, width);

        // Rebuild Components (apply style per word, preserve hover/click/etc.)
        List<Component> out = new ArrayList<>(lines.size());
        for (List<Word> line : lines) {
            TextComponent.Builder b = Component.text();
            boolean first = true;
            for (Word w : line) {
                if (!first) b.append(Component.text(" ")); // <-- fixed
                b.append(Component.text(w.text()).style(w.style));
                first = false;
            }
            out.add(noItalics(b.build()));
        }
        return out;
    }

    /* ---------------- internals ---------------- */

    private record Run(String text, Style style) {
    }

    private record Word(String text, Style style, boolean leadingSpace) {
    }

    private static List<Run> flattenToRuns(Component root) {
        Deque<Style> stack = new ArrayDeque<>();
        List<Run> out = new ArrayList<>();
        final Style[] current = {Style.empty()};

        FlattenerListener listener = new FlattenerListener() {
            @Override
            public void pushStyle(@NotNull Style style) {
                stack.push(style);
                current[0] = mergeAll(stack);
            }

            @Override
            public void popStyle(@NotNull Style style) {
                // pop the most recently pushed style
                if (!stack.isEmpty()) stack.pop();
                current[0] = mergeAll(stack);
            }

            @Override
            public void component(String text) {
                if (!text.isEmpty()) out.add(new Run(text, current[0]));
            }
        };

        // Include vanilla-like handling for translatables/keybinds/etc.
        ComponentFlattener.basic().flatten(root, listener); // Javadoc: ComponentFlattener#basic().flatten(...)
        return out;
    }

    private static Style mergeAll(Deque<Style> stack) {
        Style merged = Style.empty();
        for (Iterator<Style> it = stack.descendingIterator(); it.hasNext(); ) {
            merged = merged.merge(it.next(), Merge.all()); // Javadoc: Style.Merge#all
        }
        return merged;
    }

    private static List<Word> toWords(List<Run> runs) {
        List<Word> words = new ArrayList<>();
        boolean needSpace = false;
        for (Run r : runs) {
            String s = r.text();
            int i = 0, n = s.length();
            while (i < n) {
                // skip extra spaces
                while (i < n && s.charAt(i) == ' ') {
                    needSpace = true;
                    i++;
                }
                if (i >= n) break;
                int start = i;
                while (i < n && s.charAt(i) != ' ') i++;
                String w = s.substring(start, i);
                words.add(new Word(w, r.style(), needSpace));
                needSpace = true;
            }
        }
        return words;
    }

    private static List<List<Word>> wrapWords(List<Word> words, int width) {
        List<List<Word>> lines = new ArrayList<>();
        List<Word> cur = new ArrayList<>();
        int curLen = 0;

        for (Word w : words) {
            int add = (cur.isEmpty() ? 0 : 1) + w.text().length();
            if (curLen > 0 && curLen + add > width) {
                lines.add(cur);
                cur = new ArrayList<>();
                curLen = 0;
            }
            boolean leadingSpace = !cur.isEmpty();
            cur.add(new Word(w.text(), w.style(), leadingSpace));
            curLen += w.text().length() + (leadingSpace ? 1 : 0);
        }
        if (!cur.isEmpty()) lines.add(cur);
        return lines.isEmpty() ? List.of(List.of()) : lines;
    }


    // If the last line is very short, pull one word from the previous line if it improves balance.
    private static void rebalanceTail(List<List<Word>> lines, int width) {
        if (lines.size() < 2) return;
        List<Word> last = lines.getLast();
        List<Word> prev = lines.get(lines.size() - 2);

        int lastLen = visibleLen(last);
        if (lastLen >= Math.max(2, (int) Math.floor(width * 0.35))) return;

        if (prev.size() >= 2) {
            Word moved = prev.removeLast();
            // Fix spacing on moved/prev tail
            if (!last.isEmpty()) {
                moved = new Word(moved.text(), moved.style(), true);
            }

            last.addFirst(moved);
            // If this made it worse, undo
            int newPrev = visibleLen(prev), newLast = visibleLen(last);
            int beforeDiff = Math.abs(visibleLen(prev) + moved.text().length() - lastLen);
            int afterDiff = Math.abs(newPrev - newLast);
            if (afterDiff > beforeDiff) { // revert
                last.removeFirst();
                prev.add(moved);
            }
        }
    }

    private static int visibleLen(List<Word> line) {
        int len = 0;
        boolean first = true;
        for (Word w : line) {
            len += w.text().length();
            if (!first || w.leadingSpace) len += 1;
            first = false;
        }
        return len;
    }

    private static Component noItalics(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
