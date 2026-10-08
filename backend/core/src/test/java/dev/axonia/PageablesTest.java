package dev.axonia;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageablesTest {

    @Test
    void requireSortIn_returnsThePageableWhenAllPropertiesAreAllowed() {

        // given
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "created_at"));

        // when
        var actual = Pageables.requireSortIn(pageable, Set.of("created_at"));

        // then
        assertThat(actual).isSameAs(pageable);
    }

    @Test
    void requireSortIn_allowsUnsortedPageables() {

        // given
        var pageable = Pageable.unpaged();

        // when
        var actual = Pageables.requireSortIn(pageable, Set.of("created_at"));

        // then
        assertThat(actual).isSameAs(pageable);
    }

    @Test
    void requireSortIn_rejectsAnUnknownSortProperty() {

        // given
        var pageable = PageRequest.of(0, 20, Sort.by("token_hash"));

        // when / then
        assertThatThrownBy(() -> Pageables.requireSortIn(pageable, Set.of("created_at")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("token_hash");
    }
}
