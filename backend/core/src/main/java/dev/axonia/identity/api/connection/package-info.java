/**
 * The MCP connection API: a person's consent to one MCP client, the authorization code it redeems, and the access and
 * refresh tokens it holds. The {@code agent} module's OAuth endpoints and MCP filter consume it.
 */
@NamedInterface("api")
package dev.axonia.identity.api.connection;

import org.springframework.modulith.NamedInterface;
