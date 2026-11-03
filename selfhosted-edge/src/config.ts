import { readFileSync, realpathSync, statSync } from "node:fs";
import { isAbsolute } from "node:path";
import {
  backendOriginProblem,
  edgeSecretProblem,
  identityHeaderProblem,
  publicUrlProblem,
  staticDirProblem,
} from "./deployment-problems.ts";
import { managedIdentityAssertion, tokenFileAssertion } from "./client-authentication.ts";
import {
  type AuthEnv,
  authConfiguration,
  type ClientAuthentication,
  contentSecurityPolicy,
  sealingKey,
} from "./shared.ts";

// An installation is the only app on its host, so the cookie names are not a setting.
const COOKIE_NAME_PREFIX = "axonia";

const REQUIRED = [
  "PUBLIC_URL",
  "BACKEND_ORIGIN",
  "OIDC_ISSUER",
  "OIDC_CLIENT_ID",
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

const UNRESOLVED_PLACEHOLDER = /\$\{[^}]*\}/;
const WHOLE_NUMBER = /^(0|[1-9][0-9]{0,5})$/;

// The settings read only in some OIDC_CLIENT_AUTH modes, each with a name: "..." literal that
// deploy/check-configuration-docs.sh reads. IDENTITY_ENDPOINT and IDENTITY_HEADER are the
// platform's, not an operator's, and are read without that pattern.
const CLIENT_SECRET = { name: "OIDC_CLIENT_SECRET" };
const CLIENT_AUTH = { name: "OIDC_CLIENT_AUTH" };
const MANAGED_IDENTITY_CLIENT_ID = { name: "OIDC_MANAGED_IDENTITY_CLIENT_ID" };
const FEDERATED_TOKEN_FILE = { name: "OIDC_FEDERATED_TOKEN_FILE" };

const CLIENT_AUTH_PROBLEM =
  "OIDC_CLIENT_AUTH must be client-secret, azure-managed-identity or federated-token-file.";
const FEDERATED_TOKEN_FILE_PROBLEM =
  "OIDC_FEDERATED_TOKEN_FILE must be an absolute path to a readable, non-empty file when OIDC_CLIENT_AUTH is federated-token-file.";
const MANAGED_IDENTITY_PLATFORM_PROBLEM =
  "OIDC_CLIENT_AUTH=azure-managed-identity needs IDENTITY_ENDPOINT and IDENTITY_HEADER, which App Service and Container Apps set when the site has a managed identity.";

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
const absenceProblem = (environment: Environment, name: string): string | null => {
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

interface ClientProof {
  secret: string;
  clientAuthentication?: ClientAuthentication;
}

const valueOf = (environment: Environment, name: string): string =>
  (environment[name] ?? "").trim();

const secretConflict = (mode: string): string =>
  `${CLIENT_SECRET.name} must not be set when ${CLIENT_AUTH.name} is ${mode}: the edge never sends it.`;

// The file is only checked here, never used: a token is fetched on each sign-in, not at start.
const tokenFileProblem = (path: string): string | null => {
  if (!isAbsolute(path)) {
    return FEDERATED_TOKEN_FILE_PROBLEM;
  }
  try {
    return statSync(path).isFile() && readFileSync(path, "utf8").trim() !== ""
      ? null
      : FEDERATED_TOKEN_FILE_PROBLEM;
  } catch {
    return FEDERATED_TOKEN_FILE_PROBLEM;
  }
};

const isHttpUrl = (raw: string): boolean => {
  if (!URL.canParse(raw)) {
    return false;
  }
  const { protocol } = new URL(raw);
  return protocol === "http:" || protocol === "https:";
};

const federatedTokenFile = (environment: Environment, problems: string[]): ClientProof => {
  const path = valueOf(environment, FEDERATED_TOKEN_FILE.name);
  const problem = tokenFileProblem(path);
  if (problem !== null) {
    problems.push(problem);
    return { secret: "" };
  }
  return { secret: "", clientAuthentication: tokenFileAssertion(path) };
};

const azureManagedIdentity = (environment: Environment, problems: string[]): ClientProof => {
  const clientId = valueOf(environment, MANAGED_IDENTITY_CLIENT_ID.name);
  const endpoint = valueOf(environment, "IDENTITY_ENDPOINT");
  const header = valueOf(environment, "IDENTITY_HEADER");
  if (clientId === "") {
    problems.push(
      `${MANAGED_IDENTITY_CLIENT_ID.name} is required when ${CLIENT_AUTH.name} is azure-managed-identity.`,
    );
  }
  const platformProblem =
    !isHttpUrl(endpoint) || header === ""
      ? MANAGED_IDENTITY_PLATFORM_PROBLEM
      : identityHeaderProblem(header);
  if (platformProblem !== null) {
    problems.push(platformProblem);
  }
  if (clientId === "" || platformProblem !== null) {
    return { secret: "" };
  }
  return {
    secret: "",
    clientAuthentication: managedIdentityAssertion({ endpoint, header, clientId }),
  };
};

// The secret is the client's proof unless OIDC_CLIENT_AUTH names a federated one, and then it must
// be absent so no deployment keeps a secret it believes is in use. A whitespace-only value is unset.
const clientProofOf = (environment: Environment, problems: string[]): ClientProof => {
  const mode = valueOf(environment, CLIENT_AUTH.name);
  if (mode === "" || mode === "client-secret") {
    const absent = absenceProblem(environment, CLIENT_SECRET.name);
    if (absent !== null) {
      problems.push(absent);
    }
    return { secret: environment[CLIENT_SECRET.name] ?? "" };
  }
  if (mode !== "federated-token-file" && mode !== "azure-managed-identity") {
    problems.push(CLIENT_AUTH_PROBLEM);
    return { secret: "" };
  }
  if (valueOf(environment, CLIENT_SECRET.name) !== "") {
    problems.push(secretConflict(mode));
  }
  return mode === "federated-token-file"
    ? federatedTokenFile(environment, problems)
    : azureManagedIdentity(environment, problems);
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

const authEnvironment = (
  settings: Readonly<Record<RequiredSetting, string>>,
  { secret, clientAuthentication }: ClientProof,
): AuthEnv => ({
  OIDC_ISSUER: settings.OIDC_ISSUER,
  OIDC_CLIENT_ID: settings.OIDC_CLIENT_ID,
  OIDC_CLIENT_SECRET: secret,
  ...(clientAuthentication === undefined ? {} : { clientAuthentication }),
  SESSION_COOKIE_KEY: settings.SESSION_COOKIE_KEY,
  SESSION_MAX_AGE_SECONDS: settings.SESSION_MAX_AGE_SECONDS,
  COOKIE_NAME_PREFIX,
  OIDC_SCOPES: settings.OIDC_SCOPES,
  // Unchecked here, refused by authProblem through authConfiguration when it is neither mode.
  OIDC_SESSION_END: settings.OIDC_SESSION_END as AuthEnv["OIDC_SESSION_END"],
});

export async function readConfiguration(environment: Environment): Promise<EdgeConfiguration> {
  const problems: string[] = [];
  const clientProof = clientProofOf(environment, problems);
  // A failed proof is already a problem: a stand-in keeps the auth module checking the rest.
  const clientProofFailed = problems.length > 0;
  const absent = REQUIRED.map((name) => absenceProblem(environment, name)).filter(isProblem);
  if (absent.length > 0) {
    throw new ConfigurationError([...problems, ...absent]);
  }
  const settings = Object.fromEntries(
    REQUIRED.map((name) => [name, environment[name] ?? ""]),
  ) as Record<RequiredSetting, string>;
  const auth = authEnvironment(settings, clientProof);

  const port = wholeNumber(environment, PORT, problems);
  const trustedProxyHops = wholeNumber(environment, TRUSTED_PROXY_HOPS, problems);
  problems.push(
    ...[
      publicUrlProblem(settings.PUBLIC_URL),
      backendOriginProblem(settings.BACKEND_ORIGIN),
      edgeSecretProblem(settings.EDGE_SHARED_SECRET),
      staticDirProblem(settings.STATIC_DIR),
      await authProblem(clientProofFailed ? { ...auth, OIDC_CLIENT_SECRET: "unchecked" } : auth),
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
