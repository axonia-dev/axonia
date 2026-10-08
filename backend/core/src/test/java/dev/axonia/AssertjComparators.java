package dev.axonia;

import java.time.temporal.Temporal;
import java.util.Comparator;

import org.assertj.core.data.TemporalUnitOffset;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AssertjComparators {

    public Comparator<? super Temporal> temporalCloseTo(TemporalUnitOffset offset) {
        return Comparator.nullsFirst((a, b) -> Boolean.compare(offset.isBeyondOffset(a, b), false));
    }

}
