package dev.axonia.privacy.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.PersonDataEraser;
import dev.axonia.shared.api.personaldata.PersonErasure;
import lombok.RequiredArgsConstructor;

/**
 * The atomic database half of the person-rooted erasure: run every registered {@link PersonDataEraser} in ONE
 * transaction. Must run inside a {@code SubjectScope.runAs(subject, ...)} - every eraser relies on the connection's
 * {@code app.current_subject} as its isolation guarantee. A separate bean from
 * {@link dev.axonia.privacy.application.AccountErasure} so {@code @Transactional} actually applies - a self-invoked
 * method on a proxy gets no transaction - exactly the same shape, and the same reason, {@link MemberPurge} is separate
 * from {@code AccountErasure} on the per-Organization axis.
 *
 * <p>
 * Every module that holds a person's rows outside any Organization registers a {@link PersonDataEraser}, each carrying
 * its own {@code @Transactional} boundary, so a failure inside any one was already atomic on its own. What this class
 * buys is the fan-out ACROSS erasers, and that fan-out is now live: without this class, a failure in a later eraser
 * would leave an earlier eraser's writes committed on its own connection, with no rollback and no record of where the
 * fan-out stopped - the worst available outcome on a GDPR erasure path. This class is what keeps that impossible rather
 * than merely unlikely.
 */
@Component
@RequiredArgsConstructor
public class PersonPurge {

    private final List<PersonDataEraser> erasers;

    @Transactional
    public ErasureCounts eraseFrom(PersonErasure erasure) {

        return erasers.stream().map(eraser -> eraser.erase(erasure)).reduce(ErasureCounts.NONE, ErasureCounts::plus);
    }
}
