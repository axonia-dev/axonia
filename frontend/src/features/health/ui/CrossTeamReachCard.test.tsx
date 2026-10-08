import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CrossTeamReachCard } from "@/features/health/ui/CrossTeamReachCard";

const AVAILABLE = {
  available: true,
  teams: 6,
  departments: 3,
  businessUnits: 2,
  contributorsWithUnit: 11,
  totalContributors: 12,
  trend: { previous: 4, direction: "UP" as const },
};

describe("CrossTeamReachCard", () => {
  it("reports counts and never a unit name", () => {
    render(<CrossTeamReachCard reach={AVAILABLE} />);

    expect(screen.getByText("6")).toBeInTheDocument();
    expect(screen.getByText("teams")).toBeInTheDocument();
  });

  it("states its own coverage out loud when the sync is partial", () => {
    render(<CrossTeamReachCard reach={AVAILABLE} />);

    expect(
      screen.getByText(/11 of 12 contributors with team data/),
    ).toBeInTheDocument();
  });

  it("reads unavailable rather than a misleading zero", () => {
    render(<CrossTeamReachCard reach={{ ...AVAILABLE, available: false }} />);

    expect(screen.getByText(/Unavailable/)).toBeInTheDocument();
    expect(screen.queryByText("teams")).not.toBeInTheDocument();
  });

  it("singularises a label that counts exactly one", () => {
    render(
      <CrossTeamReachCard
        reach={{
          ...AVAILABLE,
          teams: 1,
          departments: 1,
          businessUnits: 1,
        }}
      />,
    );

    expect(screen.getByText("team")).toBeInTheDocument();
    expect(screen.getByText("department")).toBeInTheDocument();
    expect(screen.getByText("business unit")).toBeInTheDocument();
  });
});
