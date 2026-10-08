// The Cloud edge's modules this edge runs as they stand: the OAuth client, the sealed cookie, the
// content security policy and the backend's edge headers. Every runtime use of edge/src goes
// through this file (their tests run through shared-modules.test.ts), so the four modules the
// public repository carries are named in one place, and a fifth shows up in review here first.
// The specifiers name .ts files because Node loads them as written.
export {
  type AuthEnv,
  authConfiguration,
  handleAuth,
  IDENTITY_PROVIDER_UNREACHABLE,
  isAuthPath,
} from "../../edge/src/auth.ts";
export { contentSecurityPolicy, MEDIA_POLICY } from "../../edge/src/csp.ts";
export {
  CLIENT_IP_HEADER,
  EDGE_AUTH_HEADER,
  ORIGIN_UNREACHABLE,
} from "../../edge/src/edge-gate.ts";
export { oauthCookieName, sealingKey, sessionCookieName } from "../../edge/src/sealed-cookie.ts";
