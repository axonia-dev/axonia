package dev.axonia.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;

/**
 * Keeps a Throwable's internals out of every JSON body the application writes.
 *
 * <p>
 * Spring AI's streamable MCP transport answers several errors with {@code ServerResponse.body(McpError)}, which Spring
 * MVC serializes as a bean: the full stack trace, cause and suppressed list included. An {@link McpError} is written as
 * the JSON-RPC error it carries, and any other Throwable that reaches a body loses its stack trace, cause, suppressed
 * list and localized message.
 */
@Configuration(proxyBeanMethods = false)
class ThrowableJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer throwableInternalsStayOutOfJson() {

        return builder -> builder.addMixIn(Throwable.class, ThrowableMixin.class).addMixIn(McpError.class,
                McpErrorMixin.class);
    }

    @JsonIgnoreProperties({"stackTrace", "cause", "suppressed", "localizedMessage"})
    private abstract static class ThrowableMixin {
    }

    private abstract static class McpErrorMixin {

        @JsonValue
        abstract JSONRPCError getJsonRpcError();
    }
}
