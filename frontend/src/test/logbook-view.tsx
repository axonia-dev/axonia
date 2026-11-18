import { vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";
import {
  useJournalWrite,
  useMeetingPlan,
} from "@/features/mentoring/api/use-mentoring";

/** Shared by the logbook suites; each declares its own `vi.mock` of the hook modules, this only feeds them. */

const mutation = () => ({
  mutate: vi.fn(),
  reset: vi.fn(),
  isPending: false,
  error: null,
});

export function logbookHooks() {
  const write = {
    addNote: mutation(),
    updateNote: mutation(),
    removeNote: mutation(),
    addPointer: mutation(),
    removePointer: mutation(),
  };
  const meeting = { plan: mutation(), change: mutation(), remove: mutation() };
  vi.mocked(useJournalWrite).mockReturnValue(write as never);
  vi.mocked(useMeetingPlan).mockReturnValue(meeting as never);
  vi.mocked(useMe).mockReturnValue({
    data: { id: "u-2", displayName: "Noah", avatarUrl: null },
  } as never);
  return { write, meeting };
}
