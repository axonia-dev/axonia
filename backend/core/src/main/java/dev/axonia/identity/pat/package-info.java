/**
 * The personal access token API: minting, authenticating, listing, revoking, and recording usage of {@code axp_} bearer
 * tokens, plus the owner-active check consumed by the {@code agent} module's MCP security chain.
 *
 * <p>
 * Exposed as a named interface (rather than folded into the {@code identity} module's root package) because this
 * subpackage is a deliberately cross-module port: the {@code agent} module's {@code AgentAuthenticationFilter} depends
 * on {@link dev.axonia.identity.pat.PersonalAccessTokens} and {@link dev.axonia.identity.pat.MemberAccounts} to
 * synthesize the in-process JWT that stands in for the token's owner.
 */
@NamedInterface("pat")
package dev.axonia.identity.pat;

import org.springframework.modulith.NamedInterface;
