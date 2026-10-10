package dev.axonia.onboarding.adapter.out.privacy;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.personaldata.Classification;
import dev.axonia.shared.api.personaldata.ColumnFootprint;
import dev.axonia.shared.api.personaldata.DataCategory;
import dev.axonia.shared.api.personaldata.Disposition;
import dev.axonia.shared.api.personaldata.PersonalDataFootprint;
import dev.axonia.shared.api.personaldata.TableFootprint;

/**
 * A path is rooted at a person, not at an Organization (V99): {@code onboarding_path} and {@code onboarding_step} carry
 * no {@code organization_id} at all, only {@code subject}. Both are claimed here under
 * {@link DataCategory#HOW_YOU_TAKE_PART}, the same category the schema already uses for attendance, sign-ups and votes:
 * a path is how somebody took part in getting started, not a credential or a thing they produced.
 *
 * <p>
 * {@code subject} is the only personal column on either table, and it is {@link Disposition#DELETE_ROW} on both: a path
 * is a guide somebody was shown, not a record anybody needs kept, so there is nothing here to anonymise and keep.
 * {@code onboarding_step} goes with its parent through V99's {@code ON DELETE CASCADE} regardless, but
 * {@link OnboardingPersonDataEraser} deletes it explicitly so the count an erasure returns is the number of rows
 * actually removed rather than one plus however many the database took with it silently.
 *
 * <p>
 * Every other column of both tables is classified not personal. On {@code onboarding_path}: {@code variant},
 * {@code community_slug}, the two {@code anchor_*} columns, {@code closed_at}, {@code created_at} and
 * {@code updated_at} describe which path this is and where it points, never who the person is. On
 * {@code onboarding_step}: {@code ordinal}, {@code step_key} and {@code visited_at} describe the step itself.
 *
 * <p>
 * {@code onboarding_step.detail} is the one column that needs a decision recorded rather than assumed: it is a copy of
 * a community's own artifact title or session date, frozen onto the step at assignment (see
 * {@code OnboardingStepEntity}). That is content the community produced, not content about this subject, so it is
 * classified not personal rather than {@link Classification#PERSONAL_CONTENT}. It joins {@code route} and
 * {@code step_key} in {@code exportedContext} because a person's own export of their path already carries what step
 * they were shown and where it pointed, and withholding only the human-readable label of it would be arbitrary.
 *
 * <p>
 * This module deliberately implements no {@code OrganizationDataPurger}. A path outlives any one hub - closing an
 * Organization must never re-onboard or delete somebody's progress - and
 * {@code OnboardingIsNotAnOrganizationDataPurgerTest} fails the build if one is ever added by reflex. The
 * classpath-scanning test requires every purger to also declare a footprint and an exporter, which is a one-way
 * implication and is satisfied by a module that has the latter two and not the first.
 */
@Component
public class OnboardingPersonalDataFootprint implements PersonalDataFootprint {

    static final String ONBOARDING_PATH = "onboarding_path";
    static final String ONBOARDING_STEP = "onboarding_step";

    /** The only personal column on either table, and what an export is matched on. */
    static final String SUBJECT = "subject";

    @Override
    public List<TableFootprint> tables() {

        return List.of(
                TableFootprint.builder().table(ONBOARDING_PATH).category(DataCategory.HOW_YOU_TAKE_PART)
                        .personal(List.of(new ColumnFootprint(SUBJECT, Classification.SUBJECT, Disposition.DELETE_ROW)))
                        .notPersonal(
                                Set.of("variant", "community_slug", "anchor_organization_id", "anchor_community_id",
                                        "closed_at", "created_at", "updated_at"))
                        .exportedContext(Set.of("variant", "community_slug", "closed_at", "created_at")).build(),
                TableFootprint.builder().table(ONBOARDING_STEP).category(DataCategory.HOW_YOU_TAKE_PART)
                        .personal(List.of(new ColumnFootprint(SUBJECT, Classification.SUBJECT, Disposition.DELETE_ROW)))
                        .notPersonal(Set.of("ordinal", "step_key", "route", "detail", "visited_at"))
                        .exportedContext(Set.of("ordinal", "step_key", "route", "detail", "visited_at")).build());
    }
}
