import { once } from "node:events";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import {
  createServer,
  type IncomingHttpHeaders,
  type IncomingMessage,
  request,
  type Server,
  type ServerResponse,
} from "node:http";
import { type AddressInfo, createConnection } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { type EdgeConfiguration, readConfiguration } from "./config.ts";
import { createEdgeServer } from "./server.ts";

export const SHELL_HTML = '<!doctype html><title>axonia</title><div id="root"></div>';
export const EDGE_SECRET = "an-edge-secret";
// Thirty-two zero bytes in base64url: a key sealingKey accepts, and that no log may ever print.
const SESSION_KEY = "A".repeat(43);

type Settings = Record<string, string | undefined>;

const listening = async (server: Server): Promise<string> => {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
};

const closed = (server: Server): Promise<void> =>
  new Promise((resolve, reject) => {
    server.close((error) => (error === undefined ? resolve() : reject(error)));
    server.closeAllConnections();
  });

const bodyOf = async (message: IncomingMessage): Promise<string> => {
  const chunks: Buffer[] = [];
  for await (const chunk of message) {
    chunks.push(chunk as Buffer);
  }
  return Buffer.concat(chunks).toString("utf8");
};

interface Sent {
  method?: string;
  path: string;
  headers?: Record<string, string>;
  body?: string;
}

/** One request through no agent and no URL parsing: the target reaches the edge exactly as given. */
export const send = async (
  origin: string,
  { method = "GET", path, headers = {}, body }: Sent,
): Promise<{ status: number; headers: IncomingHttpHeaders; body: string }> => {
  const { hostname, port } = new URL(origin);
  const outbound = request({ hostname, port, method, path, headers, agent: false });
  outbound.end(body);
  const [answer] = (await once(outbound, "response")) as [IncomingMessage];
  return { status: answer.statusCode ?? 0, headers: answer.headers, body: await bodyOf(answer) };
};

/**
 * Writes the request exactly as given and reads the answer until the edge closes. The socket stays
 * open for writing: a caller who half-closes has left, and the edge ends the backend request.
 */
export const sendRaw = async (origin: string, request: string): Promise<string> => {
  const { hostname, port } = new URL(origin);
  const socket = createConnection({ host: hostname, port: Number(port) });
  socket.on("error", () => undefined);
  socket.write(request, "latin1");
  let answer = "";
  for await (const chunk of socket) {
    answer += (chunk as Buffer).toString("latin1");
  }
  return answer;
};

type Responder = (request: IncomingMessage, response: ServerResponse) => void;

interface Received {
  method: string;
  url: string;
  headers: IncomingHttpHeaders;
  body: string;
}

export interface FakeBackend {
  origin: string;
  received: Received[];
  answerWith: (responder: Responder) => void;
  reset: () => void;
  close: () => Promise<void>;
}

const ANSWER_PLAINLY: Responder = (_request, response) => {
  response.end("from the backend");
};

export const fakeBackend = async (): Promise<FakeBackend> => {
  const received: Received[] = [];
  let responder = ANSWER_PLAINLY;
  const server = createServer((request, response) => {
    void bodyOf(request).then((body) => {
      received.push({
        method: request.method ?? "",
        url: request.url ?? "",
        headers: request.headers,
        body,
      });
      responder(request, response);
    });
  });
  const origin = await listening(server);
  return {
    origin,
    received,
    answerWith: (next) => {
      responder = next;
    },
    reset: () => {
      received.length = 0;
      responder = ANSWER_PLAINLY;
    },
    close: () => closed(server),
  };
};

interface TokenAnswer {
  status: number;
  document: object;
}

export const GRANTED: TokenAnswer = {
  status: 200,
  document: {
    access_token: "an-access-token",
    refresh_token: "a-refresh-token",
    id_token: "an-id-token",
    expires_in: 300,
  },
};

export interface FakeIdentityProvider {
  issuer: string;
  tokenRequests: URLSearchParams[];
  /** What the token endpoint answers, a grant until a test says otherwise. */
  tokenAnswer: TokenAnswer;
  close: () => Promise<void>;
}

const sendJson = (response: ServerResponse, document: object, status = 200): void => {
  response.writeHead(status, { "content-type": "application/json" });
  response.end(JSON.stringify(document));
};

/** Discovery and a token endpoint, the two things the auth module asks a provider for. */
export const fakeIdentityProvider = async (): Promise<FakeIdentityProvider> => {
  let issuer = "";
  const server = createServer((request, response) => {
    void bodyOf(request).then((body) => {
      if (request.url === "/a-tenant/token") {
        provider.tokenRequests.push(new URLSearchParams(body));
        sendJson(response, provider.tokenAnswer.document, provider.tokenAnswer.status);
        return;
      }
      sendJson(response, {
        issuer,
        authorization_endpoint: `${issuer}/authorize`,
        token_endpoint: `${issuer}/token`,
        end_session_endpoint: `${issuer}/logout`,
      });
    });
  });
  issuer = `${await listening(server)}/a-tenant`;
  const provider: FakeIdentityProvider = {
    issuer,
    tokenRequests: [],
    tokenAnswer: GRANTED,
    close: () => closed(server),
  };
  return provider;
};

/** A build as Vite writes one, in a directory that also holds a file no request may reach. */
export const staticBuild = (): string => {
  const parent = mkdtempSync(join(tmpdir(), "selfhosted-edge-"));
  mkdirSync(join(parent, "app", "assets"), { recursive: true });
  const files: Record<string, string> = {
    "app/index.html": SHELL_HTML,
    "app/assets/selfhosted-CfJAUbZ1.js": "export {};",
    "app/assets/selfhosted-CfJAUbZ1.js.br": "brotli",
    "app/assets/selfhosted-CfJAUbZ1.js.gz": "gzip",
    "app/favicon.svg": "<svg/>",
    "app/manifest.webmanifest": "{}",
    "secret.txt": "never served",
  };
  for (const [path, content] of Object.entries(files)) {
    writeFileSync(join(parent, path), content);
  }
  return join(parent, "app");
};

let sharedBuild: string | undefined;

export const environment = (overrides: Settings = {}): Settings => ({
  PUBLIC_URL: "https://axonia.example.com",
  BACKEND_ORIGIN: "http://127.0.0.1:9",
  OIDC_ISSUER: "https://login.example.com/a-tenant/v2.0",
  OIDC_CLIENT_ID: "axonia",
  OIDC_CLIENT_SECRET: "a-client-secret",
  OIDC_SCOPES: "openid profile email offline_access",
  OIDC_SESSION_END: "revoke",
  SESSION_COOKIE_KEY: SESSION_KEY,
  SESSION_MAX_AGE_SECONDS: "5184000",
  EDGE_SHARED_SECRET: EDGE_SECRET,
  STATIC_DIR: (sharedBuild ??= staticBuild()),
  ...overrides,
});

export interface RunningEdge {
  origin: string;
  configuration: EdgeConfiguration;
  close: () => Promise<void>;
}

export const startEdge = async (overrides: Settings = {}): Promise<RunningEdge> => {
  const configuration = await readConfiguration(environment(overrides));
  const server = createEdgeServer(configuration);
  const origin = await listening(server);
  return { origin, configuration, close: () => closed(server) };
};

/** An origin nothing listens on any more. */
export const deadOrigin = async (): Promise<string> => {
  const server = createServer();
  const origin = await listening(server);
  await closed(server);
  return origin;
};

/** Everything the code under test wrote to stderr, which is where every failure is logged. */
export const loggedLines = (spy: { mock: { calls: unknown[][] } }): string =>
  spy.mock.calls.map(([line]) => String(line)).join("");
