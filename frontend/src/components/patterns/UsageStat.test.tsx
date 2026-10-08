import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { UsageStat } from "@/components/patterns/UsageStat";

describe("UsageStat", () => {
  it("reads a zero count as 'not used yet'", () => {
    // given / when
    render(
      <UsageStat
        count={0}
        singular="community"
        plural="communities"
        icon={null}
      />,
    );

    // then
    expect(
      screen.getByText("Not used by any community yet."),
    ).toBeInTheDocument();
  });

  it("uses the singular noun for a count of one", () => {
    // given / when
    const { container } = render(
      <UsageStat
        count={1}
        singular="community"
        plural="communities"
        icon={null}
      />,
    );

    // then
    expect(container.textContent).toBe("Used by 1 community.");
  });

  it("uses the plural noun for many", () => {
    // given / when
    const { container } = render(
      <UsageStat
        count={4}
        singular="artifact"
        plural="artifacts"
        icon={null}
      />,
    );

    // then
    expect(container.textContent).toBe("Used by 4 artifacts.");
  });
});
