package dev.axonia.mentoring.application;

import java.util.List;

import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.PlannedMeeting;
import dev.axonia.mentoring.domain.Topic;

/** How far a pairing has come and what it plans: its topics in trail order, its notes counted and its meetings. */
record PairingProgress(List<Topic> trail, NoteTally tally, List<PlannedMeeting> meetings) {
}
