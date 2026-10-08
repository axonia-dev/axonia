import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ProviderMark } from "./ProviderMark";

describe("ProviderMark", () => {
  it("renders the Microsoft mark for an Entra-federated label", () => {
    const { container } = render(
      <ProviderMark label="Acme SSO, via Microsoft Entra ID" />,
    );

    expect(container.querySelector("svg")).toHaveAttribute(
      "viewBox",
      "0 0 23 23",
    );
  });

  it("renders the Microsoft mark even when the display name collides with another provider's key", () => {
    const { container } = render(
      <ProviderMark label="Google SSO, via Microsoft Entra ID" />,
    );

    expect(container.querySelector("svg")).toHaveAttribute(
      "viewBox",
      "0 0 23 23",
    );
  });

  it("renders no mark for an unrecognised label", () => {
    const { container } = render(<ProviderMark label="Okta" />);

    expect(container.querySelector("svg")).not.toBeInTheDocument();
  });
});
