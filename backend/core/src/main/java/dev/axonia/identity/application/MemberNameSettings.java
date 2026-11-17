package dev.axonia.identity.application;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.api.spi.MemberNamesGate;
import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.NamePattern;
import dev.axonia.identity.domain.NamePatterns;
import dev.axonia.identity.domain.ReadName;
import dev.axonia.identity.domain.UserAccount;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * The caller's Organization's member name patterns: what it declared, what a candidate list would read out of its
 * members' names, and saving a list, which reads every member's name again at once. The preview runs the same
 * {@link NamePatterns} the save does, so what an admin sees is what saving writes.
 */
@Service
@RequiredArgsConstructor
public class MemberNameSettings {

    static final int PREVIEW_ROWS = 12;

    private static final Comparator<PreviewRow> CHANGED_FIRST = Comparator.comparing((PreviewRow row) -> !row.changes())
            .thenComparing(PreviewRow::received, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final MemberNamesGate gate;
    private final NamePatternStore patternStore;
    private final UserStore users;

    /** One member's name as a candidate list would read it; {@code matched} is the pattern's index, null for none. */
    @Builder
    public record PreviewRow(String received, String displayName, String givenName, Integer matched) {

        boolean changes() {

            return !received.equals(displayName);
        }
    }

    @Transactional(readOnly = true)
    public List<String> patterns() {

        gate.requireRead();
        return patternStore.find(CurrentToken.organization()).texts();
    }

    @Transactional(readOnly = true)
    public List<PreviewRow> preview(List<String> texts) {

        gate.requireRead();
        var patterns = validated(texts);
        return users.findAllByOrganizationId(CurrentToken.organization()).stream()
                .map(member -> rowOf(patterns, member.receivedName())).sorted(CHANGED_FIRST).limit(PREVIEW_ROWS)
                .toList();
    }

    /** Replaces the list and reads every member's name through it, in one transaction; answers how many changed. */
    @Transactional
    public int save(List<String> texts) {

        gate.requireWrite();
        var patterns = validated(texts);
        var organizationId = CurrentToken.organization();
        patternStore.replace(organizationId, patterns);
        var changed = 0;
        for (var member : users.findAllByOrganizationId(organizationId)) {
            var name = patterns.read(member.receivedName());
            if (!name.equals(currentNameOf(member))) {
                users.updateNames(organizationId, member.id(), name);
                changed++;
            }
        }
        return changed;
    }

    private static NamePatterns validated(List<String> texts) {

        try {
            return NamePatterns.of(texts);
        } catch (ValidationException refusal) {
            throw new MemberNamesRefusedException(refusal, fieldAtFault(texts));
        }
    }

    private static String fieldAtFault(List<String> texts) {

        if (texts.size() > NamePatterns.MAX) {
            return "patterns";
        }
        var index = IntStream.range(0, texts.size()).filter(i -> !parses(texts.get(i))).findFirst().orElseThrow();
        return "patterns[" + index + "]";
    }

    private static boolean parses(String text) {

        try {
            NamePattern.parse(text);
            return true;
        } catch (ValidationException refusal) {
            return false;
        }
    }

    private static PreviewRow rowOf(NamePatterns patterns, String received) {

        var name = patterns.read(received);
        var matched = patterns.matchedIndex(received);
        return PreviewRow.builder().received(received).displayName(name.displayName()).givenName(name.givenName())
                .matched(matched < 0 ? null : matched).build();
    }

    private static ReadName currentNameOf(UserAccount member) {

        return new ReadName(member.displayName(), member.givenName());
    }
}
