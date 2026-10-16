package dev.axonia.recognition.adapter.out.persistence;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.recognition.api.ContributionType;

import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcLoveTallies.class)
class JdbcLoveTalliesTest extends CoreJdbcTestSupport {

    @Autowired
    private RecognizedContributionRepository contributions;

    @Autowired
    private LoveRepository loves;

    @Autowired
    private JdbcLoveTallies sut;

    private Long recognize(String organizationId, ContributionType type, Long contributionId) {

        return contributions.save(RecognizedContributionEntityMother.any().organizationId(organizationId)
                .contributionType(type).contributionId(contributionId).build()).getId();
    }

    private void love(Long recognizedContributionId, String loverUserId, String organizationId) {

        loves.save(LoveEntityMother.any().organizationId(organizationId)
                .recognizedContributionId(recognizedContributionId).loverUserId(loverUserId).build());
    }

    @Test
    void countsLovesPerContributionOfOneTypeInOneOrganization() {

        // given
        var slot7 = recognize("default", ContributionType.SLOT_PRESENTED, 7L);
        var slot8 = recognize("default", ContributionType.SLOT_PRESENTED, 8L);
        var artifact7 = recognize("default", ContributionType.ARTIFACT_CAPTURED, 7L);
        var otherOrgSlot7 = recognize("other", ContributionType.SLOT_PRESENTED, 7L);
        love(slot7, "u1", "default");
        love(slot7, "u2", "default");
        love(slot8, "u1", "default");
        love(artifact7, "u1", "default");
        love(otherOrgSlot7, "u9", "other");
        recognize("default", ContributionType.SLOT_PRESENTED, 9L);

        // when
        var actual = sut.countFor("default", ContributionType.SLOT_PRESENTED, List.of(7L, 8L, 9L, 10L));

        // then
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(7L, 2L, 8L, 1L));
    }
}
