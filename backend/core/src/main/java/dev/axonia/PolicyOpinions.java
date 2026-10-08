package dev.axonia;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import lombok.experimental.UtilityClass;

/**
 * Asks one question of every contributor to a policy port and returns the one answer given, if any.
 *
 * <p>
 * A policy port lets a module that is not always present decide a question an open module asks about an Organization. A
 * contributor answers only for the Organizations it governs and has no opinion on any other, so an empty result means
 * the asking module applies its own default. Two answers mean two modules both claim the same Organization, which is a
 * wiring error: it fails rather than letting registration order pick one.
 */
@UtilityClass
public class PolicyOpinions {

    public <P, T> Optional<T> sole(Collection<P> contributors, Function<P, Optional<T>> question) {

        var answers = contributors.stream().map(question).flatMap(Optional::stream).toList();
        if (answers.size() > 1) {
            throw new IllegalStateException("More than one policy answered the same question: " + answers);
        }
        return answers.stream().findFirst();
    }
}
