import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { FormattedNote } from "@/components/patterns/FormattedNote";
import cases from "@/lib/formatted-note/cases.json";

function normalised(markup: string): string {
  const holder = document.createElement("div");
  holder.innerHTML = markup;
  for (const element of holder.querySelectorAll("*")) {
    for (const name of element.getAttributeNames()) {
      if (name !== "href") {
        element.removeAttribute(name);
      }
    }
  }
  return holder.innerHTML;
}

describe("FormattedNote", () => {
  it.each(cases)("renders the shared case: $name", ({ input, html }) => {
    const { container } = render(<FormattedNote text={input} />);

    expect(
      normalised((container.firstElementChild as HTMLElement).innerHTML),
    ).toBe(normalised(html));
  });

  it("renders bold, a list and an external link", () => {
    const { container } = render(
      <FormattedNote
        text={"**Key** point\n\n- one\n- two\n\n[docs](https://e.com/a)"}
      />,
    );

    expect(container.querySelector("strong")).toHaveTextContent("Key");
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    const link = screen.getByRole("link", { name: "docs" });
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel")).toContain("noopener");
  });

  it("renders markup as text", () => {
    const { container } = render(
      <FormattedNote text="<script>alert(1)</script>" />,
    );

    expect(container.querySelector("script")).toBeNull();
    expect(container).toHaveTextContent("<script>alert(1)</script>");
  });

  it("renders no anchor for a javascript link", () => {
    render(<FormattedNote text="[x](javascript:alert(1))" />);

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("renders italics, ordered lists and nested lists", () => {
    const { container } = render(
      <FormattedNote text={"*it*\n\n1. a\n   - b"} />,
    );

    expect(container.querySelector("em")).toHaveTextContent("it");
    expect(container.querySelector("ol ul")).not.toBeNull();
  });
});
