import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useEditTopic,
  useProposeTopic,
} from "@/features/events/api/use-topics";
import { ProposeTopicForm } from "@/features/events/ui/ProposeTopicForm";

vi.mock("@/features/events/api/use-topics", () => ({
  useProposeTopic: vi.fn(),
  useEditTopic: vi.fn(),
}));

const mockUsePropose = vi.mocked(useProposeTopic);
const mockUseEdit = vi.mocked(useEditTopic);

// delay: null drops per-event real-timer yields so these interaction-heavy
// tests don't time out under the CPU-starved full-suite coverage run.
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUsePropose.mockReturnValue({
    mutate: vi.fn(),
    isError: false,
    isPending: false,
  } as never);
  mockUseEdit.mockReturnValue({
    mutate: vi.fn(),
    isError: false,
    isPending: false,
  } as never);
});

describe("ProposeTopicForm", () => {
  it("submits the kind and title", async () => {
    // given
    const mutate = vi.fn();
    mockUsePropose.mockReturnValue({
      mutate,
      isError: false,
      isPending: false,
    } as never);
    render(<ProposeTopicForm slug="backend-guild" />);

    // when
    await user.type(screen.getByLabelText(/title/i), "ES intro");
    await user.click(screen.getByRole("button", { name: /kind/i }));
    await user.click(screen.getByRole("option", { name: "Offer" }));
    await user.click(screen.getByRole("button", { name: /^propose$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      kind: "OFFER",
      title: "ES intro",
    });
  }, 15_000);

  it("calls onProposed after successful propose", async () => {
    const onProposed = vi.fn();
    const mutate = vi.fn(
      (_input: unknown, { onSuccess }: { onSuccess: () => void }) =>
        onSuccess(),
    );
    mockUsePropose.mockReturnValue({
      mutate,
      isError: false,
      isPending: false,
    } as never);
    render(<ProposeTopicForm slug="backend-guild" onProposed={onProposed} />);
    await user.type(screen.getByLabelText(/title/i), "ES intro");
    await user.click(screen.getByRole("button", { name: /^propose$/i }));
    expect(onProposed).toHaveBeenCalled();
  }, 15_000);

  it("submits edits in edit mode", async () => {
    const mutate = vi.fn();
    mockUseEdit.mockReturnValue({
      mutate,
      isError: false,
      isPending: false,
    } as never);
    render(
      <ProposeTopicForm
        slug="backend-guild"
        topicId={42}
        initialTitle="Old title"
        initialKind="REQUEST"
        initialDescription="Old desc"
      />,
    );
    expect(screen.getByRole("button", { name: /^save$/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      topicId: 42,
      input: { kind: "REQUEST", title: "Old title" },
    });
  }, 15_000);

  it("calls onProposed after successful edit", async () => {
    const onProposed = vi.fn();
    const mutate = vi.fn(
      (_input: unknown, { onSuccess }: { onSuccess: () => void }) =>
        onSuccess(),
    );
    mockUseEdit.mockReturnValue({
      mutate,
      isError: false,
      isPending: false,
    } as never);
    render(
      <ProposeTopicForm
        slug="backend-guild"
        topicId={42}
        initialTitle="Old title"
        onProposed={onProposed}
      />,
    );
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(onProposed).toHaveBeenCalled();
  }, 15_000);
});
