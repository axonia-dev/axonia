package dev.axonia.mentoring.domain;

import dev.axonia.shared.api.error.ConflictException;

/**
 * Whether a pairing shows on its community's Mentoring page. A pairing made under the old promise of privacy asks both
 * people: both yes shows it, one no keeps it private. Shown and private are final and carry no answers.
 */
public record Showing(Visibility visibility, boolean mentorSaidYes, boolean menteeSaidYes) {

    public static final Showing SHOWN = new Showing(Visibility.SHOWN, false, false);
    public static final Showing ASKING = new Showing(Visibility.ASKING, false, false);
    public static final Showing PRIVATE = new Showing(Visibility.PRIVATE, false, false);

    public Showing answer(Role side, ShowAnswer answer) {

        if (visibility != Visibility.ASKING) {
            throw new ConflictException("MENTORING_SHOWING_SETTLED", "Whether this pairing shows is already settled");
        }
        return switch (answer) {
            case NO -> PRIVATE;
            case UNSAID -> withSide(side, false);
            case YES -> saidYes(other(side)) ? SHOWN : withSide(side, true);
        };
    }

    public boolean saidYes(Role side) {

        return side == Role.MENTOR ? mentorSaidYes : menteeSaidYes;
    }

    private Showing withSide(Role side, boolean yes) {

        return side == Role.MENTOR
                ? new Showing(visibility, yes, menteeSaidYes)
                : new Showing(visibility, mentorSaidYes, yes);
    }

    private static Role other(Role side) {

        return side == Role.MENTOR ? Role.MENTEE : Role.MENTOR;
    }
}
