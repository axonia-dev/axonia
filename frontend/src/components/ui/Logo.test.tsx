import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Logo } from "@/components/ui/Logo";

describe("Logo", () => {
  it("renders an accessible image with the Axonia label", () => {
    // when
    render(<Logo />);

    // then
    expect(screen.getByRole("img", { name: /axonia/i })).toBeInTheDocument();
  });
});
