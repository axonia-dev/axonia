package dev.axonia.knowledge.application.port.out;

import dev.axonia.knowledge.domain.RecordOpeningCommand;

/** Where an opening is recorded. A repeat of the same member, artifact and day records nothing new. */
public interface ArtifactOpeningLog {

    void record(RecordOpeningCommand command);
}
