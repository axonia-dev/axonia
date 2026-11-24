package dev.axonia.knowledge.adapter.in.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.knowledge.application.TrendingTagsService;
import dev.axonia.knowledge.domain.TagCount;

import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = TrendingTagsController.class)
class TrendingTagsControllerTest extends ControllerTestSupport {

    @MockitoBean
    private TrendingTagsService trendingTagsService;

    @Test
    void trendingTags() {

        // given
        when(trendingTagsService.trendingTags("frontend-guild"))
                .thenReturn(List.of(TagCount.builder().tag("kafka").count(4).build()));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/artifacts/trending-tags", "frontend-guild");

        // then
        actual.assertThat().hasStatus2xxSuccessful().bodyJson().isLenientlyEqualTo("""
                [{"tag": "kafka", "count": 4}]
                """);
    }
}
