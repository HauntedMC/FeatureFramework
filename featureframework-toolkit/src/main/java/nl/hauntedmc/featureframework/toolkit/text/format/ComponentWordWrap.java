package nl.hauntedmc.featureframework.toolkit.text.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.Style.Merge;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** Word-wraps Adventure components without serializing them to plain or legacy text. */
public final class ComponentWordWrap {
    private ComponentWordWrap() {
    }

    public static List<Component> wrap(Component input, int width) {
        if (input == null) return List.of();
        if (width <= 1) return List.of(noItalics(input));
        List<Word> words = toWords(flattenToRuns(input));
        List<List<Word>> lines = wrapWords(words, width);
        rebalanceTail(lines, width);
        List<Component> result = new ArrayList<>(lines.size());
        for (List<Word> line : lines) {
            TextComponent.Builder builder = Component.text();
            boolean first = true;
            for (Word word : line) {
                if (!first) builder.append(Component.text(" "));
                builder.append(Component.text(word.text()).style(word.style()));
                first = false;
            }
            result.add(noItalics(builder.build()));
        }
        return result;
    }

    private record Run(String text, Style style) { }
    private record Word(String text, Style style, boolean leadingSpace) { }

    private static List<Run> flattenToRuns(Component root) {
        Deque<Style> stack = new ArrayDeque<>();
        List<Run> result = new ArrayList<>();
        Style[] current = {Style.empty()};
        ComponentFlattener.basic().flatten(root, new FlattenerListener() {
            @Override public void pushStyle(@NotNull Style style) {
                stack.push(style);
                current[0] = mergeAll(stack);
            }
            @Override public void popStyle(@NotNull Style style) {
                if (!stack.isEmpty()) stack.pop();
                current[0] = mergeAll(stack);
            }
            @Override public void component(String text) {
                if (!text.isEmpty()) result.add(new Run(text, current[0]));
            }
        });
        return result;
    }

    private static Style mergeAll(Deque<Style> stack) {
        Style merged = Style.empty();
        for (var iterator = stack.descendingIterator(); iterator.hasNext(); ) {
            merged = merged.merge(iterator.next(), Merge.all());
        }
        return merged;
    }

    private static List<Word> toWords(List<Run> runs) {
        List<Word> words = new ArrayList<>();
        boolean needSpace = false;
        for (Run run : runs) {
            String text = run.text();
            int index = 0;
            while (index < text.length()) {
                while (index < text.length() && text.charAt(index) == ' ') {
                    needSpace = true;
                    index++;
                }
                if (index >= text.length()) break;
                int start = index;
                while (index < text.length() && text.charAt(index) != ' ') index++;
                words.add(new Word(text.substring(start, index), run.style(), needSpace));
                needSpace = true;
            }
        }
        return words;
    }

    private static List<List<Word>> wrapWords(List<Word> words, int width) {
        List<List<Word>> lines = new ArrayList<>();
        List<Word> current = new ArrayList<>();
        int currentLength = 0;
        for (Word word : words) {
            int addition = (current.isEmpty() ? 0 : 1) + word.text().length();
            if (currentLength > 0 && currentLength + addition > width) {
                lines.add(current);
                current = new ArrayList<>();
                currentLength = 0;
            }
            boolean leadingSpace = !current.isEmpty();
            current.add(new Word(word.text(), word.style(), leadingSpace));
            currentLength += word.text().length() + (leadingSpace ? 1 : 0);
        }
        if (!current.isEmpty()) lines.add(current);
        return lines.isEmpty() ? List.of(List.of()) : lines;
    }

    private static void rebalanceTail(List<List<Word>> lines, int width) {
        if (lines.size() < 2) return;
        List<Word> last = lines.getLast();
        List<Word> previous = lines.get(lines.size() - 2);
        int lastLength = visibleLength(last);
        if (lastLength >= Math.max(2, (int) Math.floor(width * 0.35)) || previous.size() < 2) return;
        Word moved = previous.removeLast();
        if (!last.isEmpty()) moved = new Word(moved.text(), moved.style(), true);
        last.addFirst(moved);
        int beforeDifference = Math.abs(visibleLength(previous) + moved.text().length() - lastLength);
        if (Math.abs(visibleLength(previous) - visibleLength(last)) > beforeDifference) {
            last.removeFirst();
            previous.add(moved);
        }
    }

    private static int visibleLength(List<Word> line) {
        int length = 0;
        boolean first = true;
        for (Word word : line) {
            length += word.text().length();
            if (!first || word.leadingSpace()) length++;
            first = false;
        }
        return length;
    }

    private static Component noItalics(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
