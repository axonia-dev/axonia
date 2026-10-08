import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { FormError } from "@/components/patterns/FormError";

describe("FormError", () => {
  it("renders nothing when there is no error", () => {
    // when
    const { container } = render(<FormError error={null} />);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the default message as an alert when an error is present", () => {
    // when
    render(<FormError error={new Error("boom")} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("renders a custom message when provided", () => {
    // when
    render(
      <FormError error={new Error("boom")} message="Slot already taken" />,
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("Slot already taken");
  });
});
