package dev.axonia.communication.application.note;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.FormattedNote.Inline;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;
import dev.axonia.communication.application.note.FormattedNote.Text;

/**
 * Markup nested past what both parsers read alike: each level takes two marks or one list marker and two columns of
 * indentation, so these counts bound the depth before parsing. Such a note is read as plain paragraphs.
 */
final class NoteNesting {

    private static final int MAX_MARKS_PER_RUN = 200;
    private static final int MAX_LIST_LEVELS = 32;
    private static final int TAB_STOP = 4;
    private static final Pattern BLANK_LINE = Pattern.compile("[ \\t]*");
    private static final Pattern EDGE_SPACE = Pattern.compile("^[ \\t]+|[ \\t]+$");
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*+]|\\d{1,9}[.)])(?=[ \\t]|$)[ \\t]*");

    private NoteNesting() {
    }

    static boolean isTooDeep(String markdown) {

        var lines = lines(markdown);
        return lines.stream().anyMatch(line -> listLevels(line) > MAX_LIST_LEVELS)
                || runs(lines).stream().anyMatch(run -> marks(String.join("\n", run)) > MAX_MARKS_PER_RUN);
    }

    static List<Block> plainBlocks(String markdown) {

        return runs(lines(markdown)).stream().<Block>map(NoteNesting::paragraph).toList();
    }

    private static Paragraph paragraph(List<String> run) {

        var inlines = new ArrayList<Inline>();
        for (var line : run) {
            if (!inlines.isEmpty()) {
                inlines.add(new LineBreak());
            }
            inlines.add(new Text(EDGE_SPACE.matcher(line).replaceAll("")));
        }
        return new Paragraph(inlines);
    }

    private static List<String> lines(String markdown) {

        return List.of(markdown.split("\n", -1));
    }

    private static List<List<String>> runs(List<String> lines) {

        var runs = new ArrayList<List<String>>();
        var current = new ArrayList<String>();
        for (var line : lines) {
            if (BLANK_LINE.matcher(line).matches()) {
                addIfAny(runs, current);
                current = new ArrayList<>();
            } else {
                current.add(line);
            }
        }
        addIfAny(runs, current);
        return runs;
    }

    private static void addIfAny(List<List<String>> runs, List<String> run) {

        if (!run.isEmpty()) {
            runs.add(run);
        }
    }

    private static int marks(String run) {

        var count = 0;
        for (var index = 0; index < run.length(); index++) {
            if (isMark(run, index)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isMark(String text, int index) {

        var c = text.charAt(index);
        return c == '*' || c == '[' || c == ']' || c == '_' && !isIntraword(text, index);
    }

    private static boolean isIntraword(String text, int index) {

        return index > 0 && index < text.length() - 1 && isWordChar(text.charAt(index - 1))
                && isWordChar(text.charAt(index + 1));
    }

    private static boolean isWordChar(char c) {

        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    private static int listLevels(String line) {

        var columns = 0;
        var index = 0;
        for (; index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t'); index++) {
            columns = line.charAt(index) == '\t' ? columns - columns % TAB_STOP + TAB_STOP : columns + 1;
        }
        var markers = 0;
        var matcher = LIST_MARKER.matcher(line).region(index, line.length());
        while (matcher.lookingAt()) {
            markers++;
            matcher.region(matcher.end(), line.length());
        }
        return columns / 2 + markers;
    }
}
