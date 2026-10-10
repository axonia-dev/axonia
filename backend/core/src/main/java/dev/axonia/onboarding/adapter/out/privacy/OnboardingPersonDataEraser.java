package dev.axonia.onboarding.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.PersonDataEraser;
import dev.axonia.shared.api.personaldata.PersonErasure;
import lombok.RequiredArgsConstructor;

/**
 * Drops a person's onboarding path and every step it froze, outright.
 *
 * <p>
 * A {@link PersonDataEraser} and emphatically not a {@code MemberDataEraser}: a member eraser runs inside
 * {@code MemberPurge}'s per-Organization transaction, entered through {@code OrganizationScope.runAs}, whose
 * authentication names an Organization and no subject. V99 scopes both tables on {@code app.current_subject}, so every
 * statement below would match zero rows there, silently, while every test connected as the Testcontainers table owner
 * stayed green. {@code PersonPurge} runs this one inside {@code SubjectScope.runAs} instead, wrapping the
 * {@code @Transactional} boundary below so the GUC is chosen when the transaction acquires its connection.
 *
 * <p>
 * No V66-style visibility declaration is needed: that exists because Postgres requires an UPDATEd row to stay visible
 * under its own policy, and a DELETE leaves no row for a policy to have an opinion about.
 */
@Component
@RequiredArgsConstructor
public class OnboardingPersonDataEraser implements PersonDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(PersonErasure erasure) {

        var params = Map.of("subject", erasure.subject());
        // onboarding_step goes with its parent through V99's ON DELETE CASCADE, but it is deleted explicitly so the
        // count this returns is the number of rows actually removed rather than one plus however many the database
        // took with it.
        var deleted = jdbc.update("DELETE FROM onboarding_step WHERE subject = :subject", params)
                + jdbc.update("DELETE FROM onboarding_path WHERE subject = :subject", params);

        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
