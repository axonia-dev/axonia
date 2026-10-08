import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TopicKindBadge } from "@/features/events/ui/TopicKindBadge";

describe("TopicKindBadge", () => {
  it.each([
    ["REQUEST", "Request"],
    ["OFFER", "Offer"],
  ] as const)("labels %s as %s", (kind, label) => {
    render(<TopicKindBadge kind={kind} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
