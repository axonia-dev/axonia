import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MergeSuggestionCard } from "@/features/expertise/ui/MergeSuggestionCard";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const suggestion = { tagA: "react-hooks", tagB: "hooks", score: 0.92 };

describe("MergeSuggestionCard", () => {
  it("renders the similarity percentage and both fold directions", () => {
    render(
      <MergeSuggestionCard
        suggestion={suggestion}
        onMerge={vi.fn()}
        onDismiss={vi.fn()}
      />,
    );
    expect(screen.getByText("92%")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /merge hooks into react-hooks/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /merge react-hooks into hooks/i }),
    ).toBeInTheDocument();
  });

  it("merges the other tag into the chosen survivor", async () => {
    const onMerge = vi.fn();
    render(
      <MergeSuggestionCard
        suggestion={suggestion}
        onMerge={onMerge}
        onDismiss={vi.fn()}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /merge hooks into react-hooks/i }),
    );
    expect(onMerge).toHaveBeenCalledWith({
      variant: "hooks",
      canonical: "react-hooks",
    });
  });

  it("merges in the reverse direction when the other button is pressed", async () => {
    const onMerge = vi.fn();
    render(
      <MergeSuggestionCard
        suggestion={suggestion}
        onMerge={onMerge}
        onDismiss={vi.fn()}
      />,
    );
    await user.click(
      screen.getByRole("button", { name: /merge react-hooks into hooks/i }),
    );
    expect(onMerge).toHaveBeenCalledWith({
      variant: "react-hooks",
      canonical: "hooks",
    });
  });

  it("dismisses the pair when 'Not a match' is pressed", async () => {
    const onDismiss = vi.fn();
    render(
      <MergeSuggestionCard
        suggestion={suggestion}
        onMerge={vi.fn()}
        onDismiss={onDismiss}
      />,
    );
    await user.click(screen.getByRole("button", { name: /not a match/i }));
    expect(onDismiss).toHaveBeenCalledWith({
      tagA: "react-hooks",
      tagB: "hooks",
    });
  });
});
