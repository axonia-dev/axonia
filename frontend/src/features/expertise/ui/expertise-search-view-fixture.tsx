import { render } from "@testing-library/react";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigationType,
} from "react-router-dom";

import { ExpertiseSearchView } from "@/features/expertise/ui/ExpertiseSearchView";

/** Shared by the ExpertiseSearchView suites; each suite mocks the modules the view imports. */

function LocationProbe() {
  const location = useLocation();
  return (
    <output data-testid="location" data-navigation={useNavigationType()}>
      {location.search}
    </output>
  );
}

export function renderView(slug = "eng", search = "") {
  return render(
    <MemoryRouter initialEntries={[`/communities/${slug}/expertise${search}`]}>
      <Routes>
        <Route
          path="/communities/:slug/expertise"
          element={
            <>
              <ExpertiseSearchView />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}
