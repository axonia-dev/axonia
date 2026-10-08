import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  SettingsCard,
  SettingsDivider,
  SettingsFooter,
  SettingsRow,
} from "@/components/patterns/SettingsCard";

describe("SettingsCard", () => {
  it("renders its title as a heading and its action beside it", () => {
    render(
      <SettingsCard
        title="Destinations"
        action={<button>Add destination</button>}
      >
        <SettingsRow>a row</SettingsRow>
      </SettingsCard>,
    );

    expect(screen.getByRole("heading", { name: "Destinations" })).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Add destination" }),
    ).toBeVisible();
  });

  it("renders without an action", () => {
    render(
      <SettingsCard title="Notice">
        <SettingsRow>a row</SettingsRow>
      </SettingsCard>,
    );

    expect(screen.getByRole("heading", { name: "Notice" })).toBeVisible();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders its row and footer children", () => {
    render(
      <SettingsCard title="Destinations">
        <SettingsRow>row content</SettingsRow>
        <SettingsFooter>footer content</SettingsFooter>
      </SettingsCard>,
    );

    expect(screen.getByText("row content")).toBeVisible();
    expect(screen.getByText("footer content")).toBeVisible();
  });
});

describe("SettingsDivider", () => {
  it("renders as a decorative rule", () => {
    const { container } = render(<SettingsDivider />);

    const divider = container.firstElementChild;
    expect(divider).toHaveAttribute("aria-hidden");
  });
});
