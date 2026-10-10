package dev.axonia.roundup.configuration;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * All four values ship as defaults, so a deployment that configures none still gets the shipped behaviour rather than a
 * zero. Each test therefore binds only some OTHER key: an empty source binds no value object at all, and asserting a
 * default over a source that also sets it would assert nothing.
 */
class RoundupPropertiesTest {

    private static RoundupProperties bind(Map<String, Object> properties) {

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var binder = new Binder(new MapConfigurationPropertySource(properties));
        return binder.bind("axonia.roundup", Bindable.of(RoundupProperties.class), new ValidationBindHandler(validator))
                .get();
    }

    @Test
    void aQuarterIsLeftToSettleForTwoDaysUnlessConfiguredOtherwise() {

        var bound = bind(Map.of("axonia.roundup.community-cap", "4"));

        assertThat(bound.settleLag()).isEqualTo(Duration.ofDays(2));
        assertThat(bound.communityCap()).isEqualTo(4);
    }

    @Test
    void eightCommunitiesAreNamedIndividuallyUnlessConfiguredOtherwise() {

        var bound = bind(Map.of("axonia.roundup.settle-lag", "3d"));

        assertThat(bound.communityCap()).isEqualTo(8);
        assertThat(bound.settleLag()).isEqualTo(Duration.ofDays(3));
    }

    /**
     * Its own key rather than a second reading of {@code community-cap}: a tenant with one very busy guild and four
     * quiet ones should not have the quiet four pushed off a card by the busy one.
     */
    @Test
    void fourOfACommunitysSessionsAreListedIndividuallyUnlessConfiguredOtherwise() {

        var bound = bind(Map.of("axonia.roundup.community-cap", "4"));

        assertThat(bound.sessionsPerCommunityCap()).isEqualTo(4);
        assertThat(bound.communityCap()).isEqualTo(4);
    }

    /** A cloud is a picture rather than a list, and past a dozen words it stops being one. */
    @Test
    void twelveTopicsAreCloudedUnlessConfiguredOtherwise() {

        var bound = bind(Map.of("axonia.roundup.community-cap", "4"));

        assertThat(bound.topicCap()).isEqualTo(12);
        assertThat(bound.communityCap()).isEqualTo(4);
    }

    /** A cap of zero lists no session at all, which drops the community's card content rather than shortening it. */
    @Test
    void refusesASessionsPerCommunityCapThatWouldListNothing() {

        assertThatThrownBy(() -> bind(Map.of("axonia.roundup.sessions-per-community-cap", "0"))).rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("sessionsPerCommunityCap")
                .hasMessageContaining("must be greater than or equal to 1");
    }

    /** A cap of zero renders no tag at all, which drops the cloud rather than shrinking it. */
    @Test
    void refusesATopicCapThatWouldClearTheCloud() {

        assertThatThrownBy(() -> bind(Map.of("axonia.roundup.topic-cap", "0"))).rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("topicCap")
                .hasMessageContaining("must be greater than or equal to 1");
    }

    /** A negative lag produces the quarter before it has closed, reporting a period still being written into. */
    @Test
    void refusesASettleLagThatWouldProduceBeforeTheQuarterHasClosed() {

        assertThatThrownBy(() -> bind(Map.of("axonia.roundup.settle-lag", "-1d"))).rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("settleLag");
    }

    /**
     * The failure this one prevents is silence, not a wrong roundup. The most recently closed quarter's end is never
     * more than about 92 days behind now and the shortest quarter is 90 days, so a lag past that expires only once the
     * quarter it refers to has already been superseded: every tick returns at the settle guard, forever, with no error
     * and nothing in a log. Boot has to refuse it, because nothing downstream ever will.
     */
    @Test
    void refusesASettleLagThatWouldOutliveTheQuarterItWaitsFor() {

        assertThatThrownBy(() -> bind(Map.of("axonia.roundup.settle-lag", "120d"))).rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("settleLag");
    }

    /** A cap of zero names no community at all, which is a roundup with its point removed rather than a shorter one. */
    @Test
    void refusesACommunityCapThatWouldNameNobody() {

        assertThatThrownBy(() -> bind(Map.of("axonia.roundup.community-cap", "0"))).rootCause()
                .isInstanceOf(BindValidationException.class).hasMessageContaining("communityCap")
                .hasMessageContaining("must be greater than or equal to 1");
    }
}
