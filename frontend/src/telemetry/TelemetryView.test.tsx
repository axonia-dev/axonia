import { render } from "@testing-library/react";
import { useEffect } from "react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { reportView } from "./faro";
import { TelemetryView } from "./TelemetryView";

vi.mock("./faro", () => ({ reportView: vi.fn() }));

function GoBack() {
  const navigate = useNavigate();
  useEffect(() => {
    void navigate(-1);
  }, [navigate]);
  return null;
}

describe("TelemetryView", () => {
  it("reports the route it mounted on", () => {
    render(
      <MemoryRouter initialEntries={["/c/platform"]}>
        <TelemetryView />
      </MemoryRouter>,
    );

    expect(reportView).toHaveBeenCalledWith("/c/platform");
  });

  // A history move is the signal Faro's own NavigationInstrumentation cannot see: no pointer or key
  // event precedes it, so its activity window never opens and nothing is reported.
  it("reports a route reached without an interaction", () => {
    render(
      <MemoryRouter
        initialEntries={["/discover", "/c/platform"]}
        initialIndex={1}
      >
        <TelemetryView />
        <GoBack />
      </MemoryRouter>,
    );

    expect(reportView).toHaveBeenLastCalledWith("/discover");
  });
});
