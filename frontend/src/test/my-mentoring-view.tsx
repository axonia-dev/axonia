import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { vi } from "vitest";

import {
  useAnswerProposal,
  useMyMentoring,
} from "@/features/mentoring/api/use-mentoring";
import type { MyMentoring } from "@/features/mentoring/domain/mentoring";
import { mine } from "@/features/mentoring/test-data";
import { MyMentoringView } from "@/features/mentoring/ui/MyMentoringView";

/** Shared by the My mentoring suites; each declares its own `vi.mock` of the hook modules, this only feeds them. */

export function feed(overrides: Partial<MyMentoring>) {
  vi.mocked(useMyMentoring).mockReturnValue({
    isLoading: false,
    data: {
      ...mine,
      received: [],
      sent: [],
      ongoing: [],
      ended: [],
      ...overrides,
    },
  } as never);
}

export function answering() {
  const mutate = vi.fn();
  vi.mocked(useAnswerProposal).mockReturnValue({
    mutate,
    isPending: false,
  } as never);
  return mutate;
}

export function view() {
  return render(
    <MemoryRouter>
      <MyMentoringView />
    </MemoryRouter>,
  );
}

export function viewIn(slug: string) {
  return render(
    <MemoryRouter initialEntries={[`/communities/${slug}/me/mentoring`]}>
      <Routes>
        <Route
          path="/communities/:slug/me/mentoring"
          element={<MyMentoringView />}
        />
      </Routes>
    </MemoryRouter>,
  );
}
