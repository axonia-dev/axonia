import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MemberFacepile } from "@/components/patterns/MemberFacepile";

const members = Array.from({ length: 7 }, (_, i) => ({
  id: `u${i}`,
  name: `Member ${i}`,
  hue: "#1aa6a0",
  src: `https://cdn/${i}.png`,
}));

describe("MemberFacepile", () => {
  it("caps the faces it draws", () => {
    render(<MemberFacepile members={members} max={5} />);

    expect(screen.getAllByAltText(/Member \d/)).toHaveLength(5);
  });

  it("draws every face when there are fewer than the cap", () => {
    render(<MemberFacepile members={members.slice(0, 2)} max={5} />);

    expect(screen.getAllByAltText(/Member \d/)).toHaveLength(2);
  });

  it("overlaps every face but the first, on the wrapper rather than the avatar", () => {
    const { container } = render(
      <MemberFacepile members={members.slice(0, 3)} />,
    );
    const wrappers = container.querySelectorAll("[data-facepile-item]");

    expect(wrappers).toHaveLength(3);
    expect(wrappers[0]?.className).not.toContain("-ml-2");
    expect(wrappers[1]?.className).toContain("-ml-2");
    expect(wrappers[2]?.className).toContain("-ml-2");
  });

  it("renders nothing at all when there are no members", () => {
    const { container } = render(<MemberFacepile members={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});
