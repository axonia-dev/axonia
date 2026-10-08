import { z } from "zod";

const envSchema = z.object({
  // Absent means the page's own origin: a Self-hosted image is installed under any host.
  VITE_API_BASE_URL: z.url().optional(),
  // The identity provider's address and client id are deliberately absent. The browser is not the
  // OAuth client: it navigates to /auth/* on its own origin and never speaks to the identity
  // provider directly, so neither value has anything to configure here. The local dev server still
  // needs them to stand in for the edge, and reads them from its mode's env file under names with no
  // VITE_ prefix - which is what keeps them out of the bundle by construction rather than by review.
});

export type Env = z.infer<typeof envSchema>;

export function parseEnv(raw: unknown): Env {
  return envSchema.parse(raw);
}

// The key by name, never the whole `import.meta.env`: Vite inlines a bare `import.meta.env` as
// an object holding every VITE_ value of the build's mode, settings this module does not read
// included.
const env: Env = parseEnv({
  VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
});

export function apiBaseUrl(): string {
  return resolveApiBase(env.VITE_API_BASE_URL, pageOrigin());
}

export function resolveApiBase(
  configured: string | undefined,
  origin: string,
): string {
  return configured ?? origin;
}

function pageOrigin(): string {
  return typeof window === "undefined" ? "" : window.location.origin;
}
