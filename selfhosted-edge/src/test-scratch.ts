import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

export interface Scratch {
  /** A new empty directory. */
  directory: () => string;
  /** A new token file holding the content, alone in its own directory. */
  tokenFile: (content: string) => string;
  /** Removes everything made through this scratch, for a test file's afterAll. */
  remove: () => void;
}

/** Temporary files a test file makes under one root, so one afterAll removes them all. */
export const scratch = (): Scratch => {
  const root = mkdtempSync(join(tmpdir(), "selfhosted-edge-scratch-"));
  const directory = (): string => mkdtempSync(join(root, "dir-"));
  return {
    directory,
    tokenFile: (content) => {
      const path = join(directory(), "token");
      writeFileSync(path, content);
      return path;
    },
    remove: () => rmSync(root, { recursive: true, force: true }),
  };
};
