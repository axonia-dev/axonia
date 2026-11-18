import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { Proposal } from "@/features/mentoring/domain/mentoring";
import { proposalOf } from "@/features/mentoring/test-data";
import { ProposalLetter } from "@/features/mentoring/ui/my-mentoring/ProposalLetter";

function letter(proposal: Proposal, isPending = false) {
  const onAnswer = vi.fn();
  const view = render(
    <ul>
      <ProposalLetter
        proposal={proposal}
        isPending={isPending}
        onAnswer={onAnswer}
      />
    </ul>,
  );
  return { onAnswer, ...view };
}

describe("ProposalLetter", () => {
  it("from a mentee asks for a mentor and is accepted as mentor", () => {
    // when
    letter(proposalOf({ proposedBy: "MENTEE", partnerName: "Dan Okafor" }));

    // then
    expect(
      screen.getByText("Dan Okafor asks for a mentor · Platform"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Accept as mentor" }),
    ).toBeInTheDocument();
  });

  it("from a mentor offers to mentor and is accepted as mentee", () => {
    // when
    letter(proposalOf({ proposedBy: "MENTOR", partnerName: "Dan Okafor" }));

    // then
    expect(
      screen.getByText("Dan Okafor offers to mentor · Platform"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Accept as mentee" }),
    ).toBeInTheDocument();
  });

  it("tells before accepting that the pairing shows on the Mentoring page", () => {
    // when
    letter(proposalOf({ pairsShown: true }));

    // then
    expect(
      screen.getByText(
        "Once accepted, the pairing shows on the community’s Mentoring page.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/both people are asked/)).not.toBeInTheDocument();
  });

  it("tells before accepting an older proposal that both people are asked first", () => {
    // when
    letter(proposalOf({ pairsShown: false }));

    // then
    expect(
      screen.getByText(
        "Once accepted, both people are asked whether the pairing shows on the Mentoring page.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/the pairing shows on the community’s/),
    ).not.toBeInTheDocument();
  });

  it("carries the message and the topics", () => {
    // when
    letter(proposalOf({ topics: ["Kafka", "On-call habits"] }));

    // then
    expect(screen.getByText("“Happy to pair up?”")).toBeInTheDocument();
    expect(screen.getByText("On-call habits")).toBeInTheDocument();
  });

  it("an erased proposer reads Deleted member", () => {
    // when
    letter(proposalOf({ partnerId: null, partnerName: "" }));

    // then
    expect(
      screen.getByText("Deleted member asks for a mentor · Platform"),
    ).toBeInTheDocument();
  });

  it("declines", async () => {
    // given
    const { onAnswer } = letter(proposalOf());

    // when
    await userEvent.click(screen.getByRole("button", { name: "Decline" }));

    // then
    expect(onAnswer).toHaveBeenCalledWith("decline");
  });

  it("accepting answers at once and breaks the seal", async () => {
    // given
    const { onAnswer, container } = letter(proposalOf());

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Accept as mentor" }),
    );

    // then
    expect(onAnswer).toHaveBeenCalledWith("accept", expect.any(Function));
    expect(container.querySelector("[data-split]")).not.toBeNull();
    expect(
      container.querySelector(
        ".motion-safe\\:animate-\\[seal-split_0\\.4s_ease-in_both\\]",
      ),
    ).not.toBeNull();
  });

  it("an accept refused at once leaves the seal whole", async () => {
    // given
    const { onAnswer, container } = letter(proposalOf());
    onAnswer.mockImplementation((_answer: string, onRefused?: () => void) =>
      onRefused?.(),
    );

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Accept as mentor" }),
    );

    // then
    expect(onAnswer).toHaveBeenCalledWith("accept", expect.any(Function));
    expect(container.querySelector("[data-split]")).toBeNull();
  });

  it("offers nothing to press while an answer is on its way", () => {
    // when
    letter(proposalOf(), true);

    // then
    expect(screen.getByRole("button", { name: "Decline" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Accept as mentor" }),
    ).toBeDisabled();
  });
});
