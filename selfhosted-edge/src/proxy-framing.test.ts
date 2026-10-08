import { once } from "node:events";
import { type AddressInfo, createServer, type Server, type Socket } from "node:net";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import {
  type FakeBackend,
  fakeBackend,
  type RunningEdge,
  send,
  sendRaw,
  startEdge,
} from "./test-support.ts";

// The backend is read as bytes, never through a parser: a body the edge leaves unframed would be
// parsed as a request of its own, and that is the failure these tests look for.
interface RawBackend {
  origin: string;
  received: () => string;
  answer: () => void;
  close: () => Promise<void>;
}

const NO_CONTENT = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n";

/** Answers when told to, or, answering at once, as soon as anything arrives. */
const rawBackend = async (answeringAtOnce = false): Promise<RawBackend> => {
  const open: Socket[] = [];
  const unanswered: Socket[] = [];
  let bytes = "";
  const server: Server = createServer((socket) => {
    open.push(socket);
    unanswered.push(socket);
    socket.on("data", (chunk: Buffer) => {
      bytes += chunk.toString("latin1");
      if (answeringAtOnce && !socket.writableEnded) {
        socket.end(NO_CONTENT);
      }
    });
    socket.on("error", () => undefined);
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  return {
    origin: `http://127.0.0.1:${(server.address() as AddressInfo).port}`,
    received: () => bytes,
    answer: () => {
      for (const socket of unanswered.splice(0)) {
        socket.end(NO_CONTENT);
      }
    },
    close: async () => {
      for (const socket of open) {
        socket.destroy();
      }
      server.close();
      await once(server, "close");
    },
  };
};

const FRAMING_HEADER = /^(content-length|transfer-encoding):/i;

const split = (message: string): { line: string; framing: string[]; body: string } => {
  const end = message.indexOf("\r\n\r\n");
  const [line = "", ...headers] = message.slice(0, end).split("\r\n");
  return {
    line,
    framing: headers
      .filter((header) => FRAMING_HEADER.test(header))
      .map((header) => header.toLowerCase()),
    body: message.slice(end + 4),
  };
};

interface Framing {
  name: string;
  headers: string;
  body: string;
  forwarded: (method: string) => { framing: string[]; body: string };
}

const WITHOUT_BODY_BY_DEFAULT: ReadonlySet<string> = new Set([
  "GET",
  "HEAD",
  "DELETE",
  "OPTIONS",
  "TRACE",
]);

const FRAMINGS: Framing[] = [
  {
    name: "a chunked body",
    headers: "Connection: close\r\nTransfer-Encoding: chunked\r\n",
    body: "3\r\nabc\r\n0\r\n\r\n",
    forwarded: () => ({ framing: ["transfer-encoding: chunked"], body: "3\r\nabc\r\n0\r\n\r\n" }),
  },
  {
    name: "a chunked body, the coding named in capitals",
    headers: "Connection: close\r\nTransfer-Encoding: Chunked\r\n",
    body: "3\r\nabc\r\n0\r\n\r\n",
    forwarded: () => ({ framing: ["transfer-encoding: chunked"], body: "3\r\nabc\r\n0\r\n\r\n" }),
  },
  {
    name: "a body of known length",
    headers: "Connection: close\r\nContent-Length: 3\r\n",
    body: "abc",
    forwarded: () => ({ framing: ["content-length: 3"], body: "abc" }),
  },
  {
    name: "a length written with leading zeros",
    headers: "Connection: close\r\nContent-Length: 003\r\n",
    body: "abc",
    forwarded: () => ({ framing: ["content-length: 3"], body: "abc" }),
  },
  {
    name: "a body whose length the Connection header names",
    headers: "Connection: close, content-length\r\nContent-Length: 3\r\n",
    body: "abc",
    forwarded: () => ({ framing: ["content-length: 3"], body: "abc" }),
  },
  {
    name: "no body",
    headers: "Connection: close\r\n",
    body: "",
    forwarded: (method) => ({
      framing: WITHOUT_BODY_BY_DEFAULT.has(method) ? [] : ["content-length: 0"],
      body: "",
    }),
  },
];

const METHODS = ["GET", "HEAD", "DELETE", "OPTIONS", "TRACE", "POST", "PUT", "PATCH"];

const CASES = METHODS.flatMap((method) =>
  FRAMINGS.map((framing) => [method, framing.name, framing] as const),
);

describe("a forwarded body", () => {
  let backend: RawBackend;
  let edge: RunningEdge;

  beforeAll(async () => {
    backend = await rawBackend();
    edge = await startEdge({ BACKEND_ORIGIN: backend.origin });
  });

  afterAll(async () => {
    await edge.close();
    await backend.close();
  });

  afterEach(() => {
    backend.answer();
  });

  it.each(CASES)("on %s, sent as %s, reaches the backend framed", async (method, _, framing) => {
    const before = backend.received().length;
    const answered = sendRaw(
      edge.origin,
      `${method} /axonia/x HTTP/1.1\r\nHost: edge\r\n${framing.headers}\r\n${framing.body}`,
    );

    await vi.waitFor(() => {
      expect(split(backend.received().slice(before))).toEqual({
        line: `${method} /axonia/x HTTP/1.1`,
        ...framing.forwarded(method),
      });
    });
    backend.answer();

    expect(await answered).toMatch(/^HTTP\/1\.1 204 /);
    expect(split(backend.received().slice(before)).body).toBe(framing.forwarded(method).body);
  });
});

// Node's parser takes a coding before chunked, and the edge, which decodes none, would hand the
// backend gzip bytes as a body with no coding at all.
describe("a body under a coding other than chunked", () => {
  let backend: RawBackend;
  let edge: RunningEdge;

  beforeAll(async () => {
    backend = await rawBackend(true);
    edge = await startEdge({ BACKEND_ORIGIN: backend.origin });
  });

  afterAll(async () => {
    await edge.close();
    await backend.close();
  });

  it.each(["gzip, chunked", "identity, chunked"])(
    "is refused as %s, and nothing reaches the backend",
    async (coding) => {
      const before = backend.received().length;

      const answer = await sendRaw(
        edge.origin,
        `POST /axonia/x HTTP/1.1\r\nHost: edge\r\nConnection: close\r\n` +
          `Transfer-Encoding: ${coding}\r\n\r\n3\r\nabc\r\n0\r\n\r\n`,
      );

      expect(answer).toMatch(/^HTTP\/1\.1 501 Not Implemented\r\n/);
      expect(backend.received().slice(before)).toBe("");
    },
  );
});

// The attack the framing closes: a body the backend cannot delimit is a request of its own, left
// open mid-header on a pooled connection, which the next caller's request completes under that
// caller's credentials.
describe("a request smuggled in a body", () => {
  let backend: FakeBackend;
  let edge: RunningEdge;

  beforeAll(async () => {
    backend = await fakeBackend();
    edge = await startEdge({ BACKEND_ORIGIN: backend.origin });
  });

  afterAll(async () => {
    await edge.close();
    await backend.close();
  });

  it("never runs under the next member's credentials", async () => {
    await send(edge.origin, {
      path: "/axonia/me",
      headers: { "transfer-encoding": "chunked" },
      body: "POST /axonia/comments HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\nX-Ignore: ",
    });
    await send(edge.origin, {
      path: "/axonia/me",
      headers: { authorization: "Bearer VICTIM-TOKEN" },
    });

    expect(
      backend.received.map(({ method, url, headers }) => [method, url, headers.authorization]),
    ).toEqual([
      ["GET", "/axonia/me", undefined],
      ["GET", "/axonia/me", "Bearer VICTIM-TOKEN"],
    ]);
  });
});
