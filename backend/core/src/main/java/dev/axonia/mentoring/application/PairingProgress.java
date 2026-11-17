package dev.axonia.mentoring.application;

import java.util.List;

import dev.axonia.mentoring.domain.NoteTally;
import dev.axonia.mentoring.domain.Topic;

/** How far a pairing has come: its topics in trail order and its notes counted. */
record PairingProgress(List<Topic> trail, NoteTally tally) {
}
