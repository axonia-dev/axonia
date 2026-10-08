import { describe, expect, it } from "vitest";

import { type Allowed, voiceOffenders } from "@/test/copy-voice";
import { TREE_WALK_TIMEOUT_MS } from "@/test/source-files";

// The Cloud's code is held to the same rule by its own guard, with its own exceptions.
const SKIP_DIRS = ["cloud", "test"];

// Each entry is scoped to the one file that earns the exception, not global: an
// allow-listed string elsewhere is still copy and still has to obey the voice rule.
const ALLOWED: ReadonlyArray<Allowed> = [
  // Route paths, slugs and module specifiers, which are identifiers rather than copy.
  // The page title changes to "Connect an agent"; the URL and MDX filename stay put.
  { file: "features/docs/domain/doc-registry.ts", text: "connect-your-agent" },
  {
    file: "features/docs/domain/doc-registry.ts",
    text: "../content/connect-your-agent.mdx",
  },
  {
    file: "features/agent/ui/ApiKeysView.tsx",
    text: "/docs/connect-your-agent",
  },

  // ISO locale codes fed to Intl APIs, not copy: "en-US" formats dates and happens to spell an allowed word.
  { file: "features/events/ui/action-item-labels.ts", text: "en-US" },
  { file: "features/events/ui/schedule-dates.ts", text: "en-US" },
  { file: "features/events/ui/timeline/timeline.ts", text: "en-US" },
];

describe("copy voice", () => {
  it(
    "never addresses the reader as you, or speaks as we",
    () => {
      expect(
        voiceOffenders({ root: "", skip: SKIP_DIRS, allowed: ALLOWED }),
      ).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
