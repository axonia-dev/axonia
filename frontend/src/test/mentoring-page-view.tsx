import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useMe } from "@/features/identity/api/use-me";
import {
  useMentoringPage,
  usePropose,
  useSetAvailability,
} from "@/features/mentoring/api/use-mentoring";
import type { MentoringPage as MentoringPageData } from "@/features/mentoring/domain/mentoring";
import { MentoringPage } from "@/features/mentoring/ui/MentoringPage";

/** Shared by the Mentoring page suites; each declares its own `vi.mock` of the hook modules, this only feeds them. */

const EMPTY_PAGE: MentoringPageData = {
  archived: false,
  mine: { takingMentees: false, menteeLimit: null, lookingForMentor: false },
  mentors: [],
  mentees: [],
  canMentorOn: [],
  wantsToGrowIn: [],
  guidedStart: false,
};

export function page(overrides: Partial<MentoringPageData>) {
  vi.mocked(useMentoringPage).mockReturnValue({
    isLoading: false,
    data: { ...EMPTY_PAGE, ...overrides },
  } as never);
}

export function refused(error: Error) {
  vi.mocked(useMentoringPage).mockReturnValue({
    isLoading: false,
    data: undefined,
    error,
  } as never);
}

export function delegated(value: boolean) {
  vi.mocked(useIsDelegated).mockReturnValue(value);
}

export function mutation(overrides: Record<string, unknown> = {}) {
  return {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
    ...overrides,
  };
}

export function quietHooks() {
  delegated(false);
  vi.mocked(useMe).mockReturnValue({ data: { id: "me" } } as never);
  vi.mocked(useSetAvailability).mockReturnValue(mutation() as never);
  vi.mocked(usePropose).mockReturnValue(mutation() as never);
}

export function pageRoutes() {
  return (
    <MemoryRouter initialEntries={["/communities/platform/mentoring"]}>
      <Routes>
        <Route
          path="/communities/:slug/mentoring"
          element={<MentoringPage />}
        />
      </Routes>
    </MemoryRouter>
  );
}

export function renderPage() {
  return render(pageRoutes());
}
