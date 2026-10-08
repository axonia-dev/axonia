import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useActionItems } from "@/features/events/api/use-action-items";
import { useRecordFollowUp } from "@/features/events/api/use-session";
import type { ActionItem } from "@/features/events/domain/action-item";
import type { FollowUpInput } from "@/features/events/domain/events-gateway";
import { FollowUpDialog } from "@/features/events/ui/FollowUpDialog";

vi.mock("@/features/events/api/use-session");
vi.mock("@/features/events/api/use-action-items");

const record =
  vi.fn<(input: FollowUpInput, options: { onSuccess: () => void }) => void>();

function item(overrides: Partial<ActionItem> = {}): ActionItem {
  return {
    id: 5,
    communityId: 1,
    title: "Renew the certificate",
    description: "",
    tags: [],
    raisedByUserId: "u-ada",
    raisedByDisplayName: "Ada",
    assigneeUserId: "u-grace",
    assigneeDisplayName: "Grace",
    dueDate: null,
    originSessionId: null,
    checkpoint: {
      sessionId: 10,
      scheduledAt: null,
      pinned: true,
      governingTimeZone: null,
    },
    carryCount: 0,
    outcome: "OPEN",
    canClose: true,
    canReopen: false,
    canEdit: true,
    canDelete: true,
    createdAt: "2026-09-19T08:00:00Z",
    updatedAt: "2026-09-19T08:00:00Z",
    ...overrides,
  };
}

/** A loaded, empty page unless the case says otherwise: `data: undefined` with `isPending` or
 *  `isError` is what the query really hands back in those two states. */
function mockOpenItems(state: Record<string, unknown> = {}) {
  vi.mocked(useActionItems).mockReturnValue({
    data: { content: [] },
    isPending: false,
    isError: false,
    ...state,
  } as never);
}

beforeEach(() => {
  record.mockReset();
  vi.mocked(useRecordFollowUp).mockReturnValue({
    mutate: record,
    isPending: false,
    error: null,
  } as unknown as ReturnType<typeof useRecordFollowUp>);
  mockOpenItems();
});

interface Carried {
  recordingUrl?: string | null;
  notes?: string | null;
}

/** An owner holding the open state, so closing is the real thing rather than a spied callback. */
function Harness({ recordingUrl = null, notes = null }: Readonly<Carried>) {
  const [open, setOpen] = useState(true);
  return (
    <FollowUpDialog
      slug="platform-guild"
      sessionId={10}
      recordingUrl={recordingUrl}
      notes={notes}
      isOpen={open}
      onOpenChange={setOpen}
    />
  );
}

function renderFollowUp({
  open = [],
  ...carried
}: Readonly<Carried & { open?: ActionItem[] }> = {}) {
  mockOpenItems({ data: { content: open } });
  return render(<Harness {...carried} />);
}

describe("FollowUpDialog", () => {
  it("opens on what the session already carries", () => {
    render(
      <Harness
        recordingUrl="https://meet.example.com/rec-92"
        notes="The p95 numbers landed."
      />,
    );

    expect(screen.getByLabelText(/recording link/i)).toHaveValue(
      "https://meet.example.com/rec-92",
    );
    expect(screen.getByLabelText(/note/i)).toHaveValue(
      "The p95 numbers landed.",
    );
  });

  it("stays shut until its owner opens it, since the strip and the menu both open this one", () => {
    render(
      <FollowUpDialog
        slug="platform-guild"
        sessionId={10}
        recordingUrl={null}
        notes={null}
        isOpen={false}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("records what was typed, trimmed", () => {
    renderFollowUp();

    fireEvent.change(screen.getByLabelText(/recording link/i), {
      target: { value: "  https://meet.example.com/rec-92  " },
    });
    fireEvent.change(screen.getByLabelText(/note/i), {
      target: { value: " Short session. " },
    });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(record).toHaveBeenCalledWith(
      {
        recordingUrl: "https://meet.example.com/rec-92",
        notes: "Short session.",
        decisions: [],
      },
      expect.anything(),
    );
  });

  it("closes once the record lands", () => {
    record.mockImplementation((_input, { onSuccess }) => {
      onSuccess();
    });
    renderFollowUp();

    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("leaves the dialog open so the typed text survives a failed record", () => {
    record.mockImplementation(() => undefined);
    renderFollowUp();

    fireEvent.change(screen.getByLabelText(/recording link/i), {
      target: { value: "https://meet.example.com/rec-92" },
    });
    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText(/recording link/i)).toHaveValue(
      "https://meet.example.com/rec-92",
    );
  });

  it("lists every open point pre-set to carry", async () => {
    renderFollowUp({ open: [item({ id: 5, title: "Renew the certificate" })] });

    expect(
      await screen.findByText("Renew the certificate"),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Carry" })).toBeChecked();
  });

  it("saves the shown outcomes when nothing was touched", async () => {
    const user = userEvent.setup();
    renderFollowUp({ open: [item({ id: 5 })] });

    await user.click(await screen.findByRole("button", { name: "Save" }));

    expect(record).toHaveBeenCalledWith(
      expect.objectContaining({
        decisions: [{ actionItemId: 5, outcome: "CARRIED" }],
      }),
      expect.anything(),
    );
  });

  it("saves a changed outcome", async () => {
    const user = userEvent.setup();
    renderFollowUp({ open: [item({ id: 5 })] });

    await user.click(await screen.findByRole("radio", { name: "Done" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(record).toHaveBeenCalledWith(
      expect.objectContaining({
        decisions: [{ actionItemId: 5, outcome: "DONE" }],
      }),
      expect.anything(),
    );
  });

  it("sends no decisions when the session owes nothing", async () => {
    const user = userEvent.setup();
    renderFollowUp({ open: [] });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(record).toHaveBeenCalledWith(
      expect.objectContaining({ decisions: [] }),
      expect.anything(),
    );
  });

  it("says so, and records nothing, when the open points did not load", async () => {
    const user = userEvent.setup();
    mockOpenItems({ data: undefined, isError: true });
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load what this session owes.",
    );
    expect(record).not.toHaveBeenCalled();
  });

  it("records nothing while the open points are still in flight", async () => {
    const user = userEvent.setup();
    mockOpenItems({ data: undefined, isPending: true });
    render(<Harness />);

    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByText(/loading what this session owes/i)).toBeVisible();
    expect(record).not.toHaveBeenCalled();
  });

  /**
   * The dialog writes a decision for every row it lists, so what it asks for is the whole
   * contract. An already-settled item is no longer pinned here: answering for it again is
   * refused as NOT_PINNED_HERE and rolls back the recording and the note with it. And the
   * server's default page caps at 20, which would leave a busy session's remaining items
   * carried by omission with nothing on screen saying so.
   */
  it("asks for this session's open points only, a page deep enough to hold them", () => {
    renderFollowUp();

    expect(useActionItems).toHaveBeenCalledWith("platform-guild", {
      checkpointSessionId: 10,
      outcome: "OPEN",
      page: 0,
      size: 50,
    });
  });

  it("names the decision section, since saving answers for every row shown", async () => {
    renderFollowUp({ open: [item({ id: 5 })] });

    const section = await screen.findByRole("region", {
      name: "Open points",
    });
    expect(
      within(section).getByRole("radiogroup", {
        name: "Outcome for Renew the certificate",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Saving records an outcome for every point listed."),
    ).toBeInTheDocument();
  });

  it("says what becomes of the points a capped page left off", async () => {
    mockOpenItems({ data: { content: [item({ id: 5 })], totalElements: 25 } });
    render(<Harness />);

    expect(
      await screen.findByText(
        "Showing the first 1 of 25. The rest stay open on this session.",
      ),
    ).toBeInTheDocument();
  });
});
