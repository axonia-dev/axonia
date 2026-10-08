# Microsoft Entra ID

Members sign in to axonia through the company's Microsoft Entra ID tenant.
This document registers the application, names the settings the registration produces, and covers checking a token, SCIM provisioning, the optional Microsoft Graph reads and the client secret's rotation.

## Recognising Microsoft Entra ID

- axonia treats the identity provider as Microsoft Entra ID when `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` is a workforce tenant's issuer in the global cloud: `https://login.microsoftonline.com/<tenant id>/v2.0` or `https://sts.windows.net/<tenant id>/`, the tenant id as a GUID.
- Any other issuer, a sovereign cloud's (`login.microsoftonline.us`, `login.partner.microsoftonline.cn`), an External ID tenant's (`ciamlogin.com`) or a multi-tenant authority, is read as a generic OpenID Connect provider: sign-in works as `other-identity-providers.md` describes, and Microsoft Graph reads stay off.

## The app registration

In the Microsoft Entra admin center, signed in with a role that can register applications:

1. **App registrations, New registration.** Name `axonia`, single tenant, redirect URI of type Web: `https://<host>/auth/callback`.
2. **Authentication.** Add `https://<host>/` as a second Web redirect URI, because sign-out returns there and Entra only returns to a registered address.
   Leave implicit grant and hybrid flows off.
3. **Certificates and secrets.** Create a client secret, copy its value now, and put its expiry date in a calendar with a reminder a month earlier.
   An expired secret stops every sign-in.
4. **Expose an API.** Set the Application ID URI to `api://<client-id>`, then add a scope named `axonia`, consent by admins and users, enabled.
5. **Manifest.** Set `requestedAccessTokenVersion` (in older manifests, `accessTokenAcceptedVersion`) to `2`, so access tokens carry the v2 issuer and the client id as audience.
6. **Token configuration.** Add a groups claim: choose "Groups assigned to the application", emitted as Group ID, on the access token.
   Assigned groups keep the token under Entra's group limit; "Security groups" risks the overage case, where the token carries no groups at all.
   Add the optional claims `email` and `preferred_username` to the access token.
7. **API permissions.** Microsoft Graph delegated: `openid`, `profile`, `email`, `offline_access`.
   Grant admin consent.
8. **Enterprise applications, axonia, Properties.** Set "Assignment required" to yes.
   **Users and groups:** assign every group that may use axonia, including the Owner group.
9. Copy the Owner group's object id from the Groups blade.

## The settings the registration produces

| Variable | Container | Value |
| --- | --- | --- |
| `OIDC_ISSUER` | edge | `https://login.microsoftonline.com/<tenant-id>/v2.0` |
| `OIDC_CLIENT_ID` | edge | the application (client) id |
| `OIDC_CLIENT_SECRET` | edge | the secret value from step 3 |
| `OIDC_SCOPES` | edge | `openid profile email offline_access api://<client-id>/axonia` |
| `OIDC_SESSION_END` | edge | `revoke` |
| `PUBLIC_URL` | edge | `https://<host>`, the address both redirect URIs above are registered under, equal to the backend's `AXONIA_PUBLIC_BASE_URL` |
| `SESSION_MAX_AGE_SECONDS` | edge | `7776000` |
| `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` | backend | same as `OIDC_ISSUER` |
| `AXONIA_SELFHOSTED_OIDC_AUDIENCE` | backend | the application (client) id |
| `AXONIA_SELFHOSTED_OIDC_SUBJECT_CLAIM` | backend | `oid`, and never changed afterwards (a different value refuses the start, naming both) |
| `AXONIA_SELFHOSTED_OIDC_GROUPS_CLAIM` | backend | `groups` |
| `AXONIA_SELFHOSTED_OWNER_GROUP` | backend | the Owner group's object id from step 9 |

Every other setting is the same for any identity provider (`configuration.md`).

## SESSION_MAX_AGE_SECONDS

`7776000`, the ninety days a refresh token lives without use in Entra ID.

## The Helm chart

`deploy/helm/examples/entra-aks-azure-blob.yaml` sets these values for an installation on AKS:

```yaml
ownerGroup: 6f1c2d3e-0000-4000-8000-000000000001
oidc:
  issuer: https://login.microsoftonline.com/11111111-2222-4333-8444-555555555555/v2.0
  clientId: 99999999-8888-4777-8666-555555555555
  scopes: openid profile email offline_access api://99999999-8888-4777-8666-555555555555/axonia
  sessionMaxAgeSeconds: 7776000
  subjectClaim: oid
entraGraph:
  clientId: 99999999-8888-4777-8666-555555555555
  clientSecret:
    existingSecret: axonia
    key: AXONIA_ENTRA_CLIENT_SECRET
```

The audience is left empty, so it is the client id.
The client secret is the key `OIDC_CLIENT_SECRET` of the Secret `existingSecret` names (`install.md`).

## Microsoft Graph, optional

- A second app registration (or the same one) with the Microsoft Graph application permissions `GroupMember.Read.All` and `ProfilePhoto.Read.All`, admin consent granted, and a client secret.
- Set `AXONIA_ENTRA_CLIENT_ID` and `AXONIA_ENTRA_CLIENT_SECRET` (in the Helm chart, `entraGraph.clientId` and `entraGraph.clientSecret`).
- With the Helm chart, add the client secret to the Secret `existingSecret` names as the key `AXONIA_ENTRA_CLIENT_SECRET`, as the example above reads it, or name another Secret in `entraGraph.clientSecret.existingSecret`:

  ```sh
  kubectl --namespace axonia patch secret axonia --type merge \
    -p '{"stringData":{"AXONIA_ENTRA_CLIENT_SECRET":"<the Graph client secret>"}}'
  ```
- With them, an Owner binding a role gets the directory's group names as suggestions, and members' photos follow the directory.
  Without them, suggestions come from SCIM alone and no photo is read.
- Rotate this secret like the sign-in client's (`upgrading.md`).

## SCIM, optional and recommended

1. In axonia, as an Owner: settings, directory sync, create a SCIM token.
2. In Entra, Enterprise applications, axonia, Provisioning: mode automatic, tenant URL `https://<host>/scim/v2`, secret token from step 1, test connection, save.
3. Scope it to the assigned users and groups, and start provisioning.
4. The Owner group arrives held, because the Owner role already names it: as an Owner whose token carries the group, confirm it in settings, directory sync, so members whose tokens are in group overage hold the role through the directory too.

## Checking the token

1. After a sign-in, copy the access token from the browser's network tab (the response of `POST /auth/refresh`) into `https://jwt.ms`, which decodes it in the browser.
2. Check: `aud` is the client id, `iss` ends in `/v2.0`, `oid` is present, `groups` contains the Owner group's object id.
   `iss` must equal `AXONIA_SELFHOSTED_OIDC_ISSUER_URI` character for character, trailing slash included; a v1 token (`https://sts.windows.net/<tenant>/`) is refused by a v2 issuer.
3. If `groups` is missing and `_claim_names` is present instead, the token is in overage: switch the groups claim to "Groups assigned to the application" (app registration step 6), or set up SCIM before relying on groups.
4. axonia reads a person's email from the first of `email` and `preferred_username` that the token carries.
   When the token marks its `email` unverified (`email_verified` false, as a boolean or as text in any case), that address is never read as the email, neither from `email` nor from any other claim carrying it in any case; a different address in `preferred_username` still is, and a token with no `email_verified` is read as verified.
   A profile the directory pushed over SCIM is adopted by that address at the person's first sign-in, so `preferred_username` must be a value only an administrator sets.
   Entra's `preferred_username` is the user principal name, which only an administrator sets.
5. A token marked `idtyp: app` is refused, and so is one with no `scp` claim: both are issued to an application rather than a person.
   Access tokens typed `JWT` or `at+jwt`, or untyped, are accepted.
   They must be signed with an RSA key (RS256, RS384, RS512, PS256, PS384, PS512) or an EC key (ES256, ES384, ES512); a token signed with a shared secret (HS256 and the like) or unsigned is refused.
6. A browser that returned to the sign-in page at once after every sign-in now stops, after one sign-in, on "Signed in, but this server refuses the session. An administrator can check the server's log." with a "Sign in" action.
   It shows when a request is refused within a minute of the browser leaving for the sign-in, and the token is the one that sign-in brought back.
   The backend's log names the reason once a minute, as `Refused a bearer token: <reason> (iss=<iss>, aud=<aud>)`, for example `The token's audience [api://<client-id>] does not include the configured AXONIA_SELFHOSTED_OIDC_AUDIENCE <client-id>.`; an expired token, which every browser renews on its own, is not logged, and the token itself never is.
   The screen stays until "Sign in" is pressed, so once the configuration is fixed, "Sign in" lets the person in; until then it ends on the same screen again.

## When the identity provider cannot be reached

axonia first reaches the identity provider when the first signed-in request arrives, for its discovery document and then its keys; each call waits at most five seconds to connect and ten in all, and axonia asks for the keys at most once every ten seconds.
If axonia has no keys then, because the identity provider cannot be reached or answers with anything but a key set, signed-in requests answer 503 with the code `issuer_unavailable` and `Retry-After: 10`, and the log names the cause in one warning per attempt.
The request that starts an attempt waits for it at most twenty seconds the first time (the discovery document, then the keys) and ten after that, those arriving while it runs at most five, and the others answer 503 at once until the next attempt.

Once it has keys, axonia refreshes them in the background about every five minutes, so a request signed with a key it holds never waits on the identity provider, and a key the identity provider withdraws stops working within five minutes while the identity provider serves its keys.
If a refresh fails, whatever the identity provider answered, axonia keeps verifying tokens with the keys it holds, a withdrawn key included, for up to fifteen minutes after the last refresh that worked (twenty at most), then answers 503 until a refresh succeeds; each failed refresh logs a warning.
After withdrawing a signing key because it leaked, restart every axonia backend instance at once: a restarted instance holds no key until it fetches the identity provider's current set, so the withdrawn key stops working immediately, and while the identity provider cannot serve its keys, signed-in requests answer 503 rather than accept it.
A token signed with a key axonia does not hold makes it ask again, at most once every ten seconds: while the identity provider cannot be reached (no connection, a timeout, or a 5xx answer), such a token answers 503 as above; if it answers anything else, a refusal for too many requests or a malformed key set included, the key is unknown and the token answers 401.

Nobody can sign in while the identity provider is unreachable, but nobody already signed in is signed out: the edge keeps a person's session, and their browser keeps the access token it holds.
The browser asks for a new one every fifteen seconds while that token still works, then less and less often, up to about six minutes apart; at once when the person comes back to the tab or the device comes back online; and at once in every waiting tab of the same browser when one of them renews.
If the token expires first, the page stays as it is when a request is refused and shows "Sign-in is unavailable. Retrying." with a "Sign in" action; a page opened during the outage shows the same message and action in place of the page.
"Sign in" leaves the page for a fresh sign-in, which keeps the session but only completes once the identity provider answers.
A tab whose token expired before it started a renewal, as on a device that wakes without the browser reporting the tab shown again or the network back, can still go to the sign-in when a request is refused first; that sign-in fails until the identity provider answers.
Once it answers, the session continues without a password and the page loads again what was refused meanwhile; apart from the case above, only a session the identity provider refuses leads to the sign-in.
While the identity provider cannot be reached, a refused request never shows "Signed in, but this server refuses the session.", even straight after a sign-in: the page waits for the renewal instead.
Nothing needs restarting once the identity provider answers again.

## Rotating the client secret

Rotate the client secret before its expiry: add the new one, update `OIDC_CLIENT_SECRET`, restart the edge, then delete the old one.
An expired secret signs everybody out at their next renewal, and each sign-in then ends on "The identity provider refused to complete the sign-in. The edge's client settings may be wrong."; the edge logs `"failure":"token_refused"` with `"oauth_error":"invalid_client"` once per refused renewal and once per refused sign-in.
A sign-in that ends on "The identity provider issued no refresh token. OIDC_SCOPES may lack offline_access." is logged as `"failure":"no_refresh_token"`: add `offline_access` to `OIDC_SCOPES` and restart the edge.
