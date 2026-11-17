package dev.axonia.mentoring.application.port.out;

import java.util.List;
import java.util.Map;

import dev.axonia.mentoring.domain.Pointer;
import dev.axonia.mentoring.domain.PointerTarget;

/** What each pointer into the organization points at today, by pointer id; a target gone is absent. */
public interface PointerTitles {

    Map<Long, PointerTarget> resolve(List<Pointer> pointers);
}
