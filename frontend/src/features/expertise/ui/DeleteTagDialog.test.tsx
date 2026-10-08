import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/features/expertise/api/use-tag-lifecycle", () => ({
  useTagUsage: vi.fn(),
}));

import { useTagUsage } from "@/features/expertise/api/use-tag-lifecycle";
import { DeleteTagDialog } from "@/features/expertise/ui/DeleteTagDialog";

const mockUsage = vi.mocked(useTagUsage);

function usageReturning(usage: {
  artifacts: number;
  topics: number;
  talks: number;
  declarations?: number;
  learningIntents?: number;
}) {
  mockUsage.mockReturnValue({
    data: { declarations: 0, learningIntents: 0, ...usage },
    isSuccess: true,
    isError: false,
  } as never);
}

function renderDialog(tag: string | null = "misc") {
  const onCancel = vi.fn();
  const onConfirm = vi.fn();
  render(
    <DeleteTagDialog
      slug="guild"
      tag={tag}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />,
  );
  return { onCancel, onConfirm };
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  usageReturning({ artifacts: 0, topics: 0, talks: 0 });
});

describe("DeleteTagDialog", () => {
  it("stays closed when no tag is being deleted", () => {
    renderDialog(null);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("names what the delete will strip", () => {
    usageReturning({ artifacts: 3, topics: 2, talks: 0 });

    renderDialog();

    expect(screen.getByText(/3 artifacts and 2 topics/i)).toBeInTheDocument();
    expect(screen.getByText(/cannot be undone/i)).toBeInTheDocument();
  });

  it("pluralizes a single item of a kind", () => {
    usageReturning({ artifacts: 1, topics: 0, talks: 1 });

    renderDialog();

    expect(screen.getByText(/1 artifact and 1 talk\./i)).toBeInTheDocument();
  });

  it("names the members whose declarations it removes alongside the content", () => {
    usageReturning({ artifacts: 2, topics: 0, talks: 1, declarations: 3 });

    renderDialog();

    expect(
      screen.getByText(
        /strips misc from 2 artifacts, 1 talk and 3 members’ declarations\./i,
      ),
    ).toBeInTheDocument();
  });

  it("reads sensibly for an area only members declared", () => {
    usageReturning({ artifacts: 0, topics: 0, talks: 0, declarations: 1 });

    renderDialog();

    expect(
      screen.getByText(/removes misc from 1 member’s declarations\./i),
    ).toBeInTheDocument();
  });

  it("counts several declarers as members", () => {
    usageReturning({ artifacts: 0, topics: 0, talks: 0, declarations: 4 });

    renderDialog();

    expect(
      screen.getByText(/removes misc from 4 members’ declarations\./i),
    ).toBeInTheDocument();
  });

  it("says nothing of declarations when there are none", () => {
    usageReturning({ artifacts: 2, topics: 0, talks: 0, declarations: 0 });

    renderDialog();

    expect(
      screen.getByText(/strips misc from 2 artifacts\./i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/declaration/i)).not.toBeInTheDocument();
  });

  it("reads sensibly for an area only members want to learn", () => {
    usageReturning({ artifacts: 0, topics: 0, talks: 0, learningIntents: 2 });

    renderDialog("helm");

    expect(
      screen.getByText("Removes helm from 2 members’ areas to learn."),
    ).toBeInTheDocument();
  });

  it("names the members whose areas to learn it removes alongside the content", () => {
    usageReturning({ artifacts: 1, topics: 0, talks: 0, learningIntents: 1 });

    renderDialog("helm");

    expect(
      screen.getByText(
        "Strips helm from 1 artifact and 1 member’s areas to learn.",
      ),
    ).toBeInTheDocument();
  });

  it("names declarers and learners together when no content carries the tag", () => {
    usageReturning({
      artifacts: 0,
      topics: 0,
      talks: 0,
      declarations: 3,
      learningIntents: 1,
    });

    renderDialog("helm");

    expect(
      screen.getByText(
        "Removes helm from 3 members’ declarations and 1 member’s areas to learn.",
      ),
    ).toBeInTheDocument();
  });

  it("says so plainly when nothing carries the tag", () => {
    renderDialog();
    expect(
      screen.getByText(/nothing in the community carries misc/i),
    ).toBeInTheDocument();
  });

  it("holds the delete back until it knows the size of the act", () => {
    mockUsage.mockReturnValue({
      data: undefined,
      isSuccess: false,
      isError: false,
    } as never);

    renderDialog();

    expect(
      screen.getByText(/checking what uses this tag/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
  });

  it("keeps the delete shut when the usage check fails", () => {
    mockUsage.mockReturnValue({
      data: undefined,
      isSuccess: false,
      isError: true,
    } as never);

    renderDialog();

    expect(screen.getByRole("alert")).toHaveTextContent(
      /couldn’t check what uses this tag/i,
    );
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
  });

  it("confirms the delete once the counts have landed", async () => {
    usageReturning({ artifacts: 3, topics: 0, talks: 0 });
    const { onConfirm } = renderDialog();

    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("cancels without deleting", async () => {
    const { onCancel, onConfirm } = renderDialog();

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
