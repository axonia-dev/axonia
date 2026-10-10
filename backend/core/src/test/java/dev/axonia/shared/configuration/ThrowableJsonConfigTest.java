package dev.axonia.shared.configuration;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ThrowableJsonConfigTest {

    private final JsonMapper mapper = configuredMapper();

    @Test
    void mcpError_isWrittenAsItsJsonRpcErrorOnly() {

        // given
        var error = McpError.builder(McpSchema.ErrorCodes.METHOD_NOT_FOUND).message("Session ID missing").build();

        // when
        var actual = mapper.writeValueAsString(error);

        // then
        assertThat(actual).isEqualTo("{\"code\":-32601,\"message\":\"Session ID missing\"}");
    }

    @Test
    void mcpError_keepsItsData() {

        // given
        var error = McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS).message("Bad").data(Map.of("field", "x"))
                .build();

        // when
        var actual = mapper.writeValueAsString(error);

        // then
        assertThat(actual).isEqualTo("{\"code\":-32602,\"message\":\"Bad\",\"data\":{\"field\":\"x\"}}");
    }

    @Test
    void anyOtherThrowable_isWrittenWithoutItsInternals() {

        // given
        var error = new IllegalStateException("boom", new IOException("disk"));

        // when
        var actual = mapper.writeValueAsString(error);

        // then
        assertThat(actual).contains("\"message\":\"boom\"").doesNotContain("stackTrace", "\"cause\"", "suppressed",
                "localizedMessage", "disk");
    }

    private static JsonMapper configuredMapper() {

        var builder = JsonMapper.builder();
        new ThrowableJsonConfig().throwableInternalsStayOutOfJson().customize(builder);
        return builder.build();
    }
}
