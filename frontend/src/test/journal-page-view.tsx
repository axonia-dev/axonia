import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";
import {
  useEndPairing,
  useJournal,
  useJournalWrite,
  useMeetingPlan,
  useShowing,
  useWords,
} from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journal } from "@/features/mentoring/test-data";
import { JournalPage } from "@/features/mentoring/ui/JournalPage";

/** Shared by the JournalPage suites; each declares its own `vi.mock` of the hook modules, this only feeds them. */

const mutation = () => ({
  mutate: vi.fn(),
  reset: vi.fn(),
  isPending: false,
  isError: false,
  error: null,
});

export function hooks() {
  const write = {
    addTopic: mutation(),
    updateTopic: mutation(),
    removeTopic: mutation(),
    placeTopic: mutation(),
    addNote: mutation(),
    updateNote: mutation(),
    removeNote: mutation(),
    addPointer: mutation(),
    removePointer: mutation(),
  };
  vi.mocked(useJournalWrite).mockReturnValue(write as never);
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
  vi.mocked(useMeetingPlan).mockReturnValue({
    plan: mutation(),
    change: mutation(),
    remove: mutation(),
  } as never);
  vi.mocked(useWords).mockReturnValue({
    write: mutation(),
    takeBack: mutation(),
  } as never);
  const showing = mutation();
  vi.mocked(useShowing).mockReturnValue(showing as never);
  const end = mutation();
  vi.mocked(useEndPairing).mockReturnValue(end as never);
  return { write, end, showing };
}

export function show(overrides: Partial<Journal> = {}) {
  vi.mocked(useJournal).mockReturnValue({
    isLoading: false,
    data: { ...journal, ...overrides },
  } as never);
  render(
    <MemoryRouter initialEntries={["/me/mentoring/11"]}>
      <Routes>
        <Route path="/me/mentoring/:pairingId" element={<JournalPage />} />
      </Routes>
    </MemoryRouter>,
  );
}
