import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

// Every temporary file a test makes lands in one directory for the run, which the run removes.
// Test workers inherit TMPDIR, so os.tmpdir() points them here.
export default (): (() => void) => {
  const root = mkdtempSync(join(tmpdir(), "selfhosted-edge-tests-"));
  process.env.TMPDIR = root;
  return () => {
    rmSync(root, { recursive: true, force: true });
  };
};
