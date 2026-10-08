import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import type { Slot } from "@/features/events/domain/slot";
import { SlotDialog } from "@/features/events/ui/SlotDialog";
import { useUsers } from "@/features/identity/api/use-users";
import { stubMemberSelectField } from "@/test/member-select-fixtures";
import {
  slotFormSection as section,
  slotFormSession as session,
  slotWith,
  stubSlotBackend,
  theWrite,
  typeTopics,
  Wrapped,
} from "@/test/slot-forms";

/**
 * What a talk was about, asserted on the request body that actually reaches the gateway: which
 * endpoint the form calls, and whether the body carries a topic, its own tags, or neither.
 */

vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  stubMemberSelectField({
    useRosterByName: vi.mocked(useRosterByName),
    useMemberSearch: vi.mocked(useMemberSearch),
    useUsers: vi.mocked(useUsers),
  });
  stubSlotBackend();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

function add() {
  return screen.getByRole("button", { name: /^add$/i });
}

function save() {
  return screen.getByRole("button", { name: /^save$/i });
}

describe("RecordSlotForm", () => {
  function renderRecord() {
    render(
      <Wrapped>
        <SlotDialog
          mode="record"
          slug="g"
          session={session}
          section={section}
          isOpen
          onOpenChange={vi.fn()}
        />
      </Wrapped>,
    );
  }

  it("sends the tags the organizer typed", async () => {
    renderRecord();
    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await typeTopics(user, "kafka", "streaming");
    await user.click(add());

    const write = await theWrite();
    expect(write.body.tags).toEqual(["kafka", "streaming"]);
  });

  it("records a slot with tags and no topic", async () => {
    renderRecord();
    await user.type(screen.getByLabelText(/subject/i), "Kafka 101");
    await typeTopics(user, "kafka");
    await user.click(add());

    const write = await theWrite();
    expect(write.url).toMatch(/\/sessions\/9\/sections\/3\/slots\/recorded$/);
    expect(write.method).toBe("POST");
    expect(write.body).toMatchObject({ title: "Kafka 101", tags: ["kafka"] });
    expect(write.body).not.toHaveProperty("topicId");
  });
});

describe("AddSlotForm", () => {
  function renderAdd() {
    render(
      <Wrapped>
        <SlotDialog
          mode="add"
          slug="g"
          session={session}
          section={section}
          isOpen
          onOpenChange={vi.fn()}
        />
      </Wrapped>,
    );
    return screen.findByRole("radio", { name: /new topic/i });
  }

  it("offers no tags field when the slot is filled from the backlog", async () => {
    await renderAdd();
    expect(screen.getByRole("radio", { name: /from backlog/i })).toBeChecked();
    expect(screen.queryByLabelText("Topics")).not.toBeInTheDocument();
  });

  it("offers a tags field on the new-topic branch", async () => {
    await user.click(await renderAdd());
    expect(screen.getByLabelText("Topics")).toBeInTheDocument();
  });

  it("sends the tags on the new-topic branch", async () => {
    await user.click(await renderAdd());
    await user.type(screen.getByLabelText(/subject/i), "Hexagons");
    await typeTopics(user, "architecture");
    await user.click(add());

    const write = await theWrite();
    expect(write.url).toMatch(/\/sessions\/9\/sections\/3\/slots$/);
    expect(write.method).toBe("POST");
    expect(write.body).toMatchObject({
      title: "Hexagons",
      tags: ["architecture"],
    });
    expect(write.body).not.toHaveProperty("topicId");
  });

  it("sends no tags from the backlog branch, where the topic is the subject", async () => {
    await renderAdd();
    await user.click(await screen.findByRole("button", { name: /topic/i }));
    await user.click(screen.getByRole("option", { name: /Event Sourcing/i }));
    await user.click(add());

    const write = await theWrite();
    expect(write.body).toMatchObject({ topicId: 42 });
    expect(write.body).not.toHaveProperty("tags");
  });
});

describe("EditSlotForm", () => {
  function renderEdit(slot: Slot) {
    render(
      <Wrapped>
        <SlotDialog
          mode="edit"
          slug="g"
          session={session}
          section={section}
          slot={slot}
          isOpen
          onOpenChange={vi.fn()}
        />
      </Wrapped>,
    );
  }

  it("offers no tags field for a slot that carries a topic", () => {
    renderEdit(slotWith({ topicId: 42 }));
    expect(screen.queryByLabelText("Topics")).not.toBeInTheDocument();
  });

  it("sends no tags for a slot that carries a topic", async () => {
    renderEdit(slotWith({ topicId: 42 }));
    await user.click(save());

    const write = await theWrite();
    expect(write.method).toBe("PUT");
    expect(write.body).toMatchObject({ topicId: 42 });
    expect(write.body).not.toHaveProperty("tags");
  });

  it("edits the tags of a slot that carries none", async () => {
    renderEdit(slotWith({ tags: ["kafka"] }));
    await typeTopics(user, "streams");
    await user.click(save());

    const write = await theWrite();
    expect(write.url).toMatch(/\/sessions\/9\/sections\/3\/slots\/7$/);
    expect(write.body.tags).toEqual(["kafka", "streams"]);
  });

  it("keeps the slot's tags when only the title is edited", async () => {
    renderEdit(slotWith({ tags: ["kafka"] }));
    await user.type(screen.getByLabelText(/title/i), " v2");
    await user.click(save());

    const write = await theWrite();
    expect(write.body).toMatchObject({
      title: "Hexagons v2",
      tags: ["kafka"],
    });
  });

  it("clears the subject with an explicit empty list", async () => {
    renderEdit(slotWith({ tags: ["kafka"] }));
    await user.click(screen.getByRole("button", { name: /remove tag kafka/i }));
    await user.click(save());

    const write = await theWrite();
    expect(write.body.tags).toEqual([]);
  });
});
