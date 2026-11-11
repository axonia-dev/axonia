import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";

import { DeclarationPreview } from "./DeclarationPreview";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));

beforeEach(() => {
  vi.mocked(useMe).mockReturnValue({
    data: {
      id: "gabriel",
      displayName: "Dinant, Gabriel",
      avatarUrl: null,
      team: "IT Strategic Engineering",
    },
  } as never);
});

describe("DeclarationPreview", () => {
  it("showsTheRingAndEveryDeclaredTag", () => {
    // when
    const { container } = render(
      <DeclarationPreview
        tags={["kafka", "observability"]}
        tier="PRACTITIONER"
        paused={false}
      />,
    );

    // then
    expect(screen.getByText("As others see it")).toBeInTheDocument();
    expect(screen.getByText("Dinant, Gabriel")).toBeInTheDocument();
    expect(screen.getByText("Practitioner")).toBeInTheDocument();
    expect(screen.getByText("IT Strategic Engineering")).toBeInTheDocument();
    expect(container.querySelector("[data-open='true']")).not.toBeNull();
    expect(screen.getByTestId("ask-about")).toHaveTextContent(
      /^Ask about kafka, observability$/,
    );
    expect(screen.queryByText("Paused")).not.toBeInTheDocument();
  });

  it("showsPausedWithoutTheRing", () => {
    // when
    const { container } = render(
      <DeclarationPreview tags={["kafka"]} tier="OPEN_TO_QUESTIONS" paused />,
    );

    // then
    expect(container.querySelector("[data-open]")).toBeNull();
    expect(screen.queryByTestId("ask-about")).not.toBeInTheDocument();
    expect(screen.getByText("Paused")).toBeInTheDocument();
  });
});
