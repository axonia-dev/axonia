package dev.axonia;

import dev.axonia.shared.api.persistence.Entity;
import lombok.experimental.UtilityClass;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
public class AssertjAssertions {

    public void assertEntityAuditingOnSave(Entity e) {

        assertThat(e.getId()).isNotNull();
        assertThat(e.getVersion()).isNotNull();
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
    }

    public void assertEntityAuditingOnUpdate(Entity before, Entity after) {

        assertThat(before.getId()).isEqualTo(after.getId());
        assertThat(before.getVersion()).isLessThan(after.getVersion());
        assertThat(before.getCreatedAt()).isEqualTo(after.getCreatedAt());
        assertThat(before.getUpdatedAt()).isBefore(after.getUpdatedAt());
    }
}
