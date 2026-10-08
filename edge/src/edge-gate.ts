// Everything a Worker needs to reach the backend origin, in one place because more than one module
// now does: the proxy in index.ts forwards to it, and auth.ts calls it directly to record a sign-in
// test's verdict.
//
// The header the backend's edge gate reads. A second literal would drift silently - the gate answers
// 401 to a request carrying the wrong header name, which reads as an authentication problem rather
// than as a typo. The value it carries is the edge's shared secret, which the backend holds too and
// which is rotated on both together.
export const EDGE_AUTH_HEADER = "X-Axonia-Edge-Auth";

// The header the backend reads the caller's address from (ClientAddress.HEADER). Every edge stamps it
// on every request it proxies, over whatever the caller sent. A misspelt copy would not fail: the
// backend falls back to the socket address, which is the edge's own, and every caller would share one
// rate-limit bucket.
export const CLIENT_IP_HEADER = "X-Axonia-Client-Ip";

// The two ways reaching the origin fails, as the exact strings alerting matches. Shared rather than
// redeclared per module for that reason and no other: a span whose axonia.edge.failure reads
// "origin-unreachable" matches no alert rule and opens no runbook, and nothing anywhere would
// report the mismatch.
export const ORIGIN_UNREACHABLE = "origin_unreachable";
export const ORIGIN_ERROR = "origin_error";
