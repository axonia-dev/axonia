import { ConfigurationError, readConfiguration } from "./config.ts";
import { createEdgeServer } from "./server.ts";

// Inside the thirty seconds Kubernetes and Docker give a container between SIGTERM and SIGKILL. An
// event stream still open by then is closed, and its client reconnects to another replica.
const STOP_GRACE_MS = 20_000;

const configuration = await readConfiguration(process.env).catch((error: unknown) => {
  if (!(error instanceof ConfigurationError)) {
    throw error;
  }
  process.stderr.write(`${error.message}\n`);
  process.exit(1);
});

const server = createEdgeServer(configuration);

server.once("error", (error) => {
  process.stderr.write(
    `${JSON.stringify({ level: "error", failure: "listen_failed", error: error.message })}\n`,
  );
  process.exit(1);
});

server.listen(configuration.port, () => {
  const address = server.address();
  const port = typeof address === "object" && address !== null ? address.port : configuration.port;
  process.stdout.write(
    `${JSON.stringify({ level: "info", event: "listening", port, publicUrl: configuration.publicOrigin })}\n`,
  );
});

const stop = (): void => {
  setTimeout(() => server.closeAllConnections(), STOP_GRACE_MS).unref();
  server.close(() => process.exit(0));
};

process.once("SIGTERM", stop);
process.once("SIGINT", stop);
