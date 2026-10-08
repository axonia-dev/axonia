import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SettingsListRow } from "@/components/patterns/SettingsListRow";

describe("SettingsListRow", () => {
  it("renders the body and the trailing actions", () => {
    render(
      <ul>
        <SettingsListRow actions={<button type="button">End</button>}>
          <p>203.0.113.4</p>
        </SettingsListRow>
      </ul>,
    );

    expect(screen.getByText("203.0.113.4")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "End" })).toBeInTheDocument();
  });

  // The prop's doc promises the actions never shrink to make room for the body. The row has to
  // be the one keeping that promise: a caller that forgot the class would break it silently,
  // and jsdom has no layout to catch it.
  it("holds the actions in a row of its own that cannot shrink", () => {
    render(
      <ul>
        <SettingsListRow actions={<button type="button">End</button>}>
          <p>203.0.113.4</p>
        </SettingsListRow>
      </ul>,
    );

    const actions = screen.getByRole("button", { name: "End" }).parentElement;
    expect(actions?.className).toContain("flex-none");
  });

  it("draws no trailing row at all when there are no actions", () => {
    const { container } = render(
      <ul>
        <SettingsListRow>
          <p>203.0.113.4</p>
        </SettingsListRow>
      </ul>,
    );

    expect(container.querySelectorAll("li > div")).toHaveLength(1);
  });
});
