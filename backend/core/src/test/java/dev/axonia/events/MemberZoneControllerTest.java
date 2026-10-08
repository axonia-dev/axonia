package dev.axonia.events;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = MemberZoneController.class)
class MemberZoneControllerTest extends ControllerTestSupport {

    @MockitoBean
    private MemberZoneService memberZoneService;

    @Test
    void servesTheZonesTheCommunitySpans() {

        // given
        when(memberZoneService.forCommunity("frontend-dojo"))
                .thenReturn(List.of(new MemberZone("Asia/Kolkata", 4), new MemberZone("Europe/Berlin", 5)));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/member-zones", "frontend-dojo");

        // then - a bare array, like the other list routes
        actual.assertThat().hasStatus2xxSuccessful().bodyJson()
                .isEqualTo("[{\"zone\":\"Asia/Kolkata\",\"count\":4},{\"zone\":\"Europe/Berlin\",\"count\":5}]");
    }

    @Test
    void aCommunityNobodyHasMeasuredServesAnEmptyArray() {

        // given
        when(memberZoneService.forCommunity("frontend-dojo")).thenReturn(List.of());

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/member-zones", "frontend-dojo");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isEqualTo("[]");
    }
}
