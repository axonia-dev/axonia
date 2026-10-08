import { realpathSync, statSync } from "node:fs";
import { isAbsolute, join } from "node:path";
import { type AuthEnv, authConfiguration, contentSecurityPolicy, sealingKey } from "./shared.ts";

// An installation is the only app on its host, so the cookie names are not a setting.
const COOKIE_NAME_PREFIX = "axonia";

const REQUIRED = [
  "PUBLIC_URL",
  "BACKEND_ORIGIN",
  "OIDC_ISSUER",
  "OIDC_CLIENT_ID",
  "OIDC_CLIENT_SECRET",
  "OIDC_SCOPES",
  "OIDC_SESSION_END",
  "SESSION_COOKIE_KEY",
  "SESSION_MAX_AGE_SECONDS",
  "EDGE_SHARED_SECRET",
  "STATIC_DIR",
] as const;

type RequiredSetting = (typeof REQUIRED)[number];
type Environment = Readonly<Record<string, string | undefined>>;

interface WholeNumberRule {
  name: string;
  fallback: number;
  max: number;
}

const PORT: WholeNumberRule = { name: "PORT", fallback: 8080, max: 65535 };
const TRUSTED_PROXY_HOPS: WholeNumberRule = { name: "TRUSTED_PROXY_HOPS", fallback: 0, max: 10 };

const LOCAL_HOSTNAMES: ReadonlySet<string> = new Set(["localhost", "127.0.0.1", "[::1]"]);
const UNRESOLVED_PLACEHOLDER = /\$\{[^}]*\}/;
const HEADER_SAFE = /^[\x21-\x7e]+$/;
const WHOLE_NUMBER = /^(0|[1-9][0-9]{0,5})$/;

const notAnOrigin = (name: string, example: string): string =>
  `${name} must be an origin with no path, such as ${example}.`;
const STATIC_DIR_PROBLEM =
  "STATIC_DIR must be an absolute path to the built app, holding its index.html.";

export interface EdgeConfiguration {
  port: number;
  publicOrigin: string;
  backendOrigin: URL;
  edgeSharedSecret: string;
  trustedProxyHops: number;
  staticRoot: string;
  auth: AuthEnv;
  contentSecurityPolicy: string;
  strictTransport: boolean;
}

// Every problem at once, so an operator fixes the file in one pass rather than one restart each.
// No message carries a value: the settings include three secrets, and this text reaches a log.
export class ConfigurationError extends Error {
  readonly problems: readonly string[];

  constructor(problems: readonly string[]) {
    super(`The edge cannot start:\n${problems.map((problem) => `- ${problem}`).join("\n")}`);
    this.name = "ConfigurationError";
    this.problems = problems;
  }
}

const isProblem = (problem: string | null): problem is string => problem !== null;

// A literal ${NAME} is what a compose file or a chart hands over when the variable it names was
// never set, and it is truthy, so it would otherwise pass as a value.
const absenceProblem = (environment: Environment, name: RequiredSetting): string | null => {
  const value = environment[name];
  if (value === undefined || value.trim() === "") {
    return `${name} is required.`;
  }
  return UNRESOLVED_PLACEHOLDER.test(value) ? `${name} holds an unresolved placeholder.` : null;
};

const wholeNumber = (
  environment: Environment,
  rule: WholeNumberRule,
  problems: string[],
): number => {
  const raw = environment[rule.name];
  if (raw === undefined || raw === "") {
    return rule.fallback;
  }
  const parsed = WHOLE_NUMBER.test(raw) ? Number(raw) : Number.NaN;
  if (!(parsed <= rule.max)) {
    problems.push(`${rule.name} must be a whole number from 0 to ${rule.max}.`);
    return rule.fallback;
  }
  return parsed;
};

// Exactly an origin: written the way URL writes one back, so nothing a cookie, a redirect or the
// backend's own PUBLIC_URL is compared against can differ by a path, a port spelling or a case.
const originOf = (raw: string): URL | null => {
  if (!URL.canParse(raw)) {
    return null;
  }
  const url = new URL(raw);
  return raw.replace(/\/$/, "") === url.origin ? url : null;
};

const publicUrlProblem = (raw: string): string | null => {
  const url = originOf(raw);
  if (url === null) {
    return notAnOrigin("PUBLIC_URL", "https://axonia.example.com");
  }
  return url.protocol === "https:" || LOCAL_HOSTNAMES.has(url.hostname)
    ? null
    : "PUBLIC_URL must be https, or http on localhost: browsers refuse the session cookie anywhere else.";
};

const backendOriginProblem = (raw: string): string | null =>
  originOf(raw) === null ? notAnOrigin("BACKEND_ORIGIN", "http://axonia-backend:8080") : null;

const edgeSecretProblem = (raw: string): string | null =>
  HEADER_SAFE.test(raw)
    ? null
    : "EDGE_SHARED_SECRET must be printable ASCII with no spaces: it travels in a header.";

const staticDirProblem = (raw: string): string | null => {
  if (!isAbsolute(raw)) {
    return STATIC_DIR_PROBLEM;
  }
  try {
    return statSync(join(raw, "index.html")).isFile() ? null : STATIC_DIR_PROBLEM;
  } catch {
    return STATIC_DIR_PROBLEM;
  }
};

// The auth module's own validation, run once here rather than on the first sign-in, so a wrong
// setting stops the process instead of answering 502 to every member. The key is checked first
// because authConfiguration reports a bad key without naming the setting.
const authProblem = async (auth: AuthEnv): Promise<string | null> => {
  try {
    await sealingKey(auth.SESSION_COOKIE_KEY);
  } catch (error) {
    return (error as Error).message;
  }
  const verdict = await authConfiguration(auth);
  if (!(verdict instanceof Response)) {
    return null;
  }
  const { detail } = (await verdict.json()) as { detail: string };
  return detail;
};

const authEnvironment = (settings: Readonly<Record<RequiredSetting, string>>): AuthEnv => ({
  OIDC_ISSUER: settings.OIDC_ISSUER,
  OIDC_CLIENT_ID: settings.OIDC_CLIENT_ID,
  OIDC_CLIENT_SECRET: settings.OIDC_CLIENT_SECRET,
  SESSION_COOKIE_KEY: settings.SESSION_COOKIE_KEY,
  SESSION_MAX_AGE_SECONDS: settings.SESSION_MAX_AGE_SECONDS,
  COOKIE_NAME_PREFIX,
  OIDC_SCOPES: settings.OIDC_SCOPES,
  // Unchecked here, refused by authProblem through authConfiguration when it is neither mode.
  OIDC_SESSION_END: settings.OIDC_SESSION_END as AuthEnv["OIDC_SESSION_END"],
});

export async function readConfiguration(environment: Environment): Promise<EdgeConfiguration> {
  const absent = REQUIRED.map((name) => absenceProblem(environment, name)).filter(isProblem);
  if (absent.length > 0) {
    throw new ConfigurationError(absent);
  }
  const settings = Object.fromEntries(
    REQUIRED.map((name) => [name, environment[name] ?? ""]),
  ) as Record<RequiredSetting, string>;
  const auth = authEnvironment(settings);

  const problems: string[] = [];
  const port = wholeNumber(environment, PORT, problems);
  const trustedProxyHops = wholeNumber(environment, TRUSTED_PROXY_HOPS, problems);
  problems.push(
    ...[
      publicUrlProblem(settings.PUBLIC_URL),
      backendOriginProblem(settings.BACKEND_ORIGIN),
      edgeSecretProblem(settings.EDGE_SHARED_SECRET),
      staticDirProblem(settings.STATIC_DIR),
      await authProblem(auth),
    ].filter(isProblem),
  );
  if (problems.length > 0) {
    throw new ConfigurationError(problems);
  }

  const publicOrigin = new URL(settings.PUBLIC_URL).origin;
  return {
    port,
    publicOrigin,
    backendOrigin: new URL(settings.BACKEND_ORIGIN),
    edgeSharedSecret: settings.EDGE_SHARED_SECRET,
    trustedProxyHops,
    // Real, so static.ts can hold every file it serves, links followed, to lie inside it.
    staticRoot: realpathSync(settings.STATIC_DIR),
    auth,
    contentSecurityPolicy: contentSecurityPolicy({
      themeScriptHash: null,
      identityOrigin: new URL(settings.OIDC_ISSUER).origin,
      mediaOrigin: null,
      paymentHosts: null,
    }),
    strictTransport: publicOrigin.startsWith("https:"),
  };
}
