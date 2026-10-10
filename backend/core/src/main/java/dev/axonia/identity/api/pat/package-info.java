/**
 * The personal access token API: minting, authenticating, listing, revoking, and recording usage of {@code axp_} bearer
 * tokens, plus the owner-active check consumed by the {@code agent} module's MCP security chain.
 *
 * <p>
 * Exposed as a named interface (each package under {@code api} declares itself) because this subpackage is a
 * deliberately cross-module port: the {@code agent} module's {@code AgentAuthenticationFilter} depends on
 * {@link dev.axonia.identity.api.pat.PersonalAccessTokens} and {@link dev.axonia.identity.api.pat.MemberAccounts} to
 * synthesize the in-process JWT that stands in for the token's owner.
 */
@NamedInterface("api")
package dev.axonia.identity.api.pat;

import org.springframework.modulith.NamedInterface;
