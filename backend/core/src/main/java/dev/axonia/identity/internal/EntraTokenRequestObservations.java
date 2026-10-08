package dev.axonia.identity.internal;

import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

import io.micrometer.common.KeyValue;

/**
 * Names the Entra token endpoint by its template, so no customer's tenant id reaches a metric or a span, under the
 * client-request meter name every other outbound call is observed under.
 */
class EntraTokenRequestObservations extends DefaultClientRequestObservationConvention {

    static final String TOKEN_URI_TEMPLATE = "/{aadTenantId}/oauth2/v2.0/token";

    EntraTokenRequestObservations(String name) {
        super(name);
    }

    @Override
    protected KeyValue uri(ClientRequestObservationContext context) {

        return KeyValue.of("uri", TOKEN_URI_TEMPLATE);
    }
}
