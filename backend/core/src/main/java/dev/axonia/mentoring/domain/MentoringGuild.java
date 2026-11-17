package dev.axonia.mentoring.domain;

/** The community a mentoring notice comes from: its name signs the notice and its slug is where the link opens. */
public record MentoringGuild(String slug, String name) {

    public static MentoringGuild of(MentoringCommunity community) {

        return new MentoringGuild(community.slug(), community.name());
    }
}
