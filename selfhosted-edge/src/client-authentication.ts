import { readFile } from "node:fs/promises";
import type { ClientAuthentication } from "./shared.ts";

export const JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

const readUtf8 = (path: string): Promise<string> => readFile(path, "utf8");

/** The kubelet rotates the projected token about hourly, so it is read fresh on every call. */
export const tokenFileAssertion =
  (path: string, read: (path: string) => Promise<string> = readUtf8): ClientAuthentication =>
  async () => {
    const token = (await read(path)).trim();
    if (token === "") {
      throw new Error(`The federated token file ${path} is empty.`);
    }
    return { client_assertion_type: JWT_BEARER, client_assertion: token };
  };
