package dev.axonia.identity.adapter.out.entra;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.axonia.identity.api.IdentityProviderDirectory.IdpGroup;
import dev.axonia.identity.configuration.CacheConfig;

/**
 * Every group in one Microsoft Entra tenant, read from Microsoft Graph
 * ({@code GET /v1.0/groups?$select=id,displayName}, following {@code @odata.nextLink} until the last page) and held for
 * five minutes per tenant in the {@value CacheConfig#ENTRA_GROUPS} cache, so repeated admin-UI page loads do not repeat
 * the Graph call.
 *
 * <p>
 * Keyed by tenant because Graph's answer depends on the tenant alone: two Organizations on one tenant share one read,
 * and an Organization that changes its tenant reads the new one at once. Whether an Organization may read a tenant is
 * not asked here: {@link EntraDirectoryAdapter} asks it before every call, outside the cache, so a revoked proof takes
 * effect at once.
 *
 * <p>
 * {@link #listFor} is public because the cache is a proxy in front of it, and a proxy reliably intercepts only a public
 * method. The answer is immutable because every caller within the lifetime receives the same instance. A read that
 * fails throws and is not cached.
 */
@Component
public class GraphGroups {

    private static final String FIRST_PAGE = "/v1.0/groups?$select=id,displayName";

    private final RestClient graphClient;

    public GraphGroups(@Qualifier("entraGraphRestClient") RestClient graphClient) {
        this.graphClient = graphClient;
    }

    @Cacheable(cacheNames = CacheConfig.ENTRA_GROUPS, key = "#aadTenantId", sync = true)
    public List<IdpGroup> listFor(String aadTenantId) {
        var actingFor = EntraGraphRegistrations.actingFor(aadTenantId);
        var all = new ArrayList<IdpGroup>();
        String nextUri = FIRST_PAGE;
        while (nextUri != null) {
            var page = fetchGroupsPage(actingFor, URI.create(nextUri));
            @SuppressWarnings("unchecked")
            var value = (List<Map<String, Object>>) page.get("value");
            if (value != null) {
                for (var g : value) {
                    all.add(new IdpGroup((String) g.get("id"), (String) g.get("displayName")));
                }
            }
            nextUri = (String) page.get("@odata.nextLink");
        }
        return List.copyOf(all);
    }

    /**
     * Takes a {@link URI} rather than a String, because {@code RestClient.uri(String)} treats its argument as a uri
     * <em>template</em> and records it for the client observation, so the path and query of every page after the first
     * - Graph's {@code @odata.nextLink}, whose {@code $skiptoken} is an opaque directory cursor - would be exported
     * verbatim on the span's {@code uri} attribute, which {@code AttributeAllowlist} permits. {@code uri(URI)} records
     * no template, so the observation reports {@code uri="none"}. The wire request is unchanged either way: a relative
     * URI is still resolved against the client's base URL, which is what keeps the first page's {@code /v1.0/groups}
     * working.
     */
    private Map<String, Object> fetchGroupsPage(Consumer<Map<String, Object>> actingFor, URI uri) {
        var result = graphClient.get().uri(uri).attributes(actingFor).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return result != null ? result : Map.of();
    }
}
