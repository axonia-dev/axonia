import type { OutgoingHttpHeaders } from "node:http";
import { MEDIA_POLICY } from "./shared.ts";

const MEDIA_PATH_PREFIX = "/public/media/";
// No includeSubDomains: every host under the installation's name is the company's to decide.
const STRICT_TRANSPORT = "max-age=31536000";

interface Policies {
  contentSecurityPolicy: string;
  strictTransport: boolean;
}

export const isMediaPath = (pathname: string): boolean => pathname.startsWith(MEDIA_PATH_PREFIX);

// Stamped on every answer the edge sends, its own and the backend's alike, over whatever policy the
// answer carried: the app's policy is the edge's to set. An uploaded image is the exception, and
// keeps the stricter one the backend serves it under.
export const withPolicies = (
  headers: OutgoingHttpHeaders,
  pathname: string,
  policies: Policies,
): OutgoingHttpHeaders => {
  const answer: OutgoingHttpHeaders = { ...headers, "x-robots-tag": "noindex" };
  if (!isMediaPath(pathname)) {
    answer["content-security-policy"] = policies.contentSecurityPolicy;
  } else if (answer["content-security-policy"] === undefined) {
    answer["content-security-policy"] = MEDIA_POLICY;
  }
  if (policies.strictTransport) {
    answer["strict-transport-security"] = STRICT_TRANSPORT;
  }
  return answer;
};
