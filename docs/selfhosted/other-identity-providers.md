# Other OpenID Connect identity providers

Keycloak, Okta, Auth0 and any other OpenID Connect provider work with axonia under the same rules.
Microsoft Entra ID has its own document, `entra.md`.

## What the provider must offer

An identity provider works with axonia when it offers five things:

1. Discovery: `<issuer>/.well-known/openid-configuration`, its `issuer` equal character for character to `iss` in its tokens.
2. A confidential client for the edge, with the redirect URIs `<PUBLIC_URL>/auth/callback` and `<PUBLIC_URL>/`.
3. Access tokens that are JWTs signed with a key from its `jwks_uri`, carrying an audience axonia is configured with (`AXONIA_SELFHOSTED_OIDC_AUDIENCE`).
   An opaque access token cannot be verified and every request is refused.
4. A refresh token at every sign-in, which is what the edge's session is built on.
5. A groups claim in the access token, whose values match `AXONIA_SELFHOSTED_OWNER_GROUP` and the groups Owners bind roles to.

Access tokens must be signed with an RSA key (RS256, RS384, RS512, PS256, PS384, PS512) or an EC key (ES256, ES384, ES512); a token signed with a shared secret (HS256 and the like) or unsigned is refused.
A token marked `idtyp: app` is refused: it is issued to an application rather than a person.
A token carrying an `act` claim is refused: it is issued for somebody acting on a person's behalf, as token exchange produces, and only a person's own sign-in is accepted.
Give no client but axonia's the axonia audience, and leave service accounts (client credentials) off on axonia's client.

## Refresh token reuse

With Microsoft Entra ID nothing more is needed: a refresh token it replaced stays usable.
A provider that rotates refresh tokens and revokes a reused one must allow a short reuse: in Keycloak, with Revoke Refresh Token on, a Refresh Token Max Reuse of 1; in Auth0 or Okta, a reuse interval or grace period of at least a minute.
Without it, a token answer that arrives after the edge's five second timeout leaves the previous refresh token in the session, and the next renewal signs that person out.

## The settings

| Variable | Value |
| --- | --- |
| `OIDC_ISSUER` and `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` | The issuer, character for character as its tokens write `iss`, trailing slash included or not |
| `OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET` | The edge's confidential client |
| `OIDC_SCOPES` | `openid`, and whatever makes the provider issue a refresh token (often `offline_access`) and an access token for axonia's audience |
| `AXONIA_SELFHOSTED_OIDC_AUDIENCE` | The audience the provider writes into axonia's access tokens |
| `SESSION_MAX_AGE_SECONDS` | The provider's refresh token lifetime when unused, in seconds |

Every other setting is the same for any identity provider (`configuration.md`).

## The claims

| Variable | Default |
| --- | --- |
| `AXONIA_SELFHOSTED_OIDC_SUBJECT_CLAIM` | `sub` |
| `AXONIA_SELFHOSTED_OIDC_GROUPS_CLAIM` | `groups` |
| `AXONIA_SELFHOSTED_OIDC_EMAIL_CLAIMS` | `email,preferred_username` |
| `AXONIA_SELFHOSTED_OIDC_NAME_CLAIM` | `name` |

The subject claim must never change for a person and never be reused for another: it is how axonia knows a person across sign-ins, and it is permanent for the installation.
A later start with another subject claim is refused, naming both.

## The address

The first claim of `AXONIA_SELFHOSTED_OIDC_EMAIL_CLAIMS` present in the token is the person's address, as the provider asserts it: axonia sends mail to it without a confirmation of its own, so use a claim the provider verifies.
When the token marks its `email` unverified (`email_verified` false, as a boolean or as text in any case), that address is never read as the email, neither from `email` nor from any other claim carrying it in any case; a token with no `email_verified` is read as verified.
A profile the directory pushed over SCIM is adopted by that address at the person's first sign-in, so a claim like `preferred_username` must be a value only an administrator sets.
At Keycloak, leave "Edit username" off and do not let people choose their username at registration: leave self-registration off, or turn "Email as username" on, so that the username follows an email the rule above checks; keep the `email` scope, with `email_verified`, in axonia's access token.

## Signing out

`OIDC_SESSION_END` says how a sign-out ends the session at the provider, besides the browser's redirect to its end-session endpoint, which happens either way.
Set `revoke` for a provider that publishes a `revocation_endpoint` in its discovery document: the edge revokes the refresh token there (RFC 7009, the standard).
Set `refresh-token-post` for Keycloak: its end-session endpoint takes the refresh token and ends its single sign-on session.

## Checking the token

Decode an access token the edge received, from the browser's network tab: the response of `POST /auth/refresh`, or the `Authorization` header of any `/axonia` request.
Compare `iss`, `aud`, the subject claim, the groups claim and the address claims with the settings above.
When the backend refuses a token, the person stops on "Signed in, but this server refuses the session. An administrator can check the server's log." and the backend's log line `Refused a bearer token: <reason> (iss=<iss>, aud=<aud>)` names the mismatch, once a minute; the token itself is never logged.
Once the setting is fixed, "Sign in" lets the person in.

When the identity provider cannot be reached, signed-in requests answer 503 with `issuer_unavailable`; `entra.md`, "When the identity provider cannot be reached", describes what axonia does meanwhile, and the same applies to any provider.
