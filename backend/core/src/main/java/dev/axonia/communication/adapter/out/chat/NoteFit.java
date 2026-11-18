package dev.axonia.communication.adapter.out.chat;

import java.util.function.Function;

import dev.axonia.communication.application.note.FormattedNote;
import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.NotePrefix;

/**
 * Fits a rendered note into a platform's text limit: whole blocks where they fit, else the start of the first block,
 * cut on the note's tree so the platform markup stays whole.
 */
final class NoteFit {

    static final String ELLIPSIS = "…";

    private NoteFit() {
    }

    static String of(FormattedNote note, Function<Block, String> render, String separator, int maxLength) {

        var rendered = note.blocks().stream().map(render).toList();
        var joined = String.join(separator, rendered);
        if (joined.length() <= maxLength) {
            return joined;
        }
        var count = 0;
        var length = -separator.length();
        while (length + separator.length() + rendered.get(count).length() + ELLIPSIS.length() <= maxLength) {
            length += separator.length() + rendered.get(count).length();
            count++;
        }
        return count == 0
                ? startOf(note.blocks().getFirst(), render, maxLength)
                : String.join(separator, rendered.subList(0, count)) + ELLIPSIS;
    }

    private static String startOf(Block block, Function<Block, String> render, int maxLength) {

        var room = maxLength - ELLIPSIS.length();
        var best = "";
        var fewest = 0;
        var most = NotePrefix.lengthOf(block);
        while (fewest <= most) {
            var budget = (fewest + most) >>> 1;
            var text = NotePrefix.of(block, budget).map(render).orElse("").stripTrailing();
            if (text.length() <= room) {
                best = text;
                fewest = budget + 1;
            } else {
                most = budget - 1;
            }
        }
        return best + ELLIPSIS;
    }
}
