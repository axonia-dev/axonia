import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TopicListCard } from "@/features/events/ui/TopicListCard";

describe("TopicListCard", () => {
  it("renders leading, title, meta, and trailing slots", () => {
    render(
      <TopicListCard
        leading={<span>lead</span>}
        title="Contract testing with Pact"
        meta={<span>meta-line</span>}
        trailing={<button type="button">act</button>}
      />,
    );
    expect(screen.getByText("lead")).toBeInTheDocument();
    expect(screen.getByText("Contract testing with Pact")).toBeInTheDocument();
    expect(screen.getByText("meta-line")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "act" })).toBeInTheDocument();
  });

  it("renders without optional slots", () => {
    render(<TopicListCard title="Bare" />);
    expect(screen.getByText("Bare")).toBeInTheDocument();
  });
});
