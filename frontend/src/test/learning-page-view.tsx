import { render } from "@testing-library/react";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigationType,
} from "react-router-dom";
import { vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";
import {
  useAddIntent,
  useLearners,
  useLearningPage,
  useRemoveIntent,
} from "@/features/learning/api/use-learning";
import type {
  Learner,
  LearningPage as LearningPageData,
} from "@/features/learning/domain/learning";
import { LearningPage } from "@/features/learning/ui/LearningPage";

/** Shared by the LearningPage suites; each declares its own `vi.mock` of the hook modules, this only feeds them. */

const EMPTY_PAGE: LearningPageData = {
  member: false,
  archived: false,
  limit: 10,
  mine: [],
  wanted: [],
};

export function page(overrides: Partial<LearningPageData>) {
  vi.mocked(useLearningPage).mockReturnValue({
    isLoading: false,
    isError: false,
    data: { ...EMPTY_PAGE, ...overrides },
  } as never);
}

export function learners(list: Learner[]) {
  vi.mocked(useLearners).mockReturnValue({
    isLoading: false,
    isError: false,
    data: list,
  } as never);
}

export function delegated(value: boolean) {
  vi.mocked(useIsDelegated).mockReturnValue(value);
}

export function quietHooks() {
  delegated(false);
  learners([]);
  vi.mocked(useExpertiseAreas).mockReturnValue({ data: [] } as never);
  vi.mocked(useAddIntent).mockReturnValue({
    mutate: vi.fn(),
    reset: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useRemoveIntent).mockReturnValue({
    mutate: vi.fn(),
    isRemoving: () => false,
  } as never);
}

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="location" data-navigation={useNavigationType()}>
      {location.search}
    </output>
  );
}

export function renderPage(search = "") {
  return render(
    <MemoryRouter initialEntries={[`/communities/platform/learning${search}`]}>
      <Routes>
        <Route
          path="/communities/:slug/learning"
          element={
            <>
              <LearningPage />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}
