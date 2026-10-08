import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Skeleton } from "@/components/ui/Skeleton";

describe("Skeleton", () => {
  it("renders a decorative, aria-hidden placeholder", () => {
    // when
    const { container } = render(<Skeleton className="h-4 w-20" />);

    // then
    const el = container.firstChild as HTMLElement;
    expect(el.getAttribute("aria-hidden")).toBe("true");
    expect(el.className).toContain("h-4");
  });
});
