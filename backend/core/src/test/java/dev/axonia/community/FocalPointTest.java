package dev.axonia.community;

import org.junit.jupiter.api.Test;

import dev.axonia.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FocalPointTest {

    @Test
    void acceptsThePercentageRangeInclusive() {

        assertThat(FocalPoint.of(0, 0)).isEqualTo(new FocalPoint(0, 0));
        assertThat(FocalPoint.of(100, 100)).isEqualTo(new FocalPoint(100, 100));
        assertThat(FocalPoint.of(50, 78)).isEqualTo(new FocalPoint(50, 78));
    }

    @Test
    void rejectsAnythingOutsideThePercentageRange() {

        assertThatThrownBy(() -> FocalPoint.of(-1, 50)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> FocalPoint.of(101, 50)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> FocalPoint.of(50, -1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> FocalPoint.of(50, 101)).isInstanceOf(ValidationException.class);
    }

    @Test
    void buildsFromNullableColumnsAndIsNullWhenEitherIsMissing() {

        assertThat(FocalPoint.fromColumns(50, 78)).isEqualTo(new FocalPoint(50, 78));
        assertThat(FocalPoint.fromColumns(null, 78)).isNull();
        assertThat(FocalPoint.fromColumns(50, null)).isNull();
        assertThat(FocalPoint.fromColumns(null, null)).isNull();
    }
}
