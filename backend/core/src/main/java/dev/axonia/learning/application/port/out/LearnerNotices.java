package dev.axonia.learning.application.port.out;

import dev.axonia.learning.domain.OpenDoorNotice;

/** Tells the learners of an area, in the panel only, that a member is now open to questions on it. */
public interface LearnerNotices {

    /** Sent once per declarer, area and learner, ever: a repeat of the same notice reaches nobody twice. */
    void notify(OpenDoorNotice notice);
}
