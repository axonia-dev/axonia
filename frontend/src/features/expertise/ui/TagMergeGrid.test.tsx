import { PointerSensor, useSensor, useSensors } from "@dnd-kit/core";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { TagMergeGrid } from "@/features/expertise/ui/TagMergeGrid";

const AREAS = [
  { canonicalTag: "ok", variants: [], peopleCount: 1 },
  { canonicalTag: "yes", variants: ["yeah"], peopleCount: 3 },
];

function Harness({ onDragEnd }: Readonly<{ onDragEnd: () => void }>) {
  // No activation distance here so a single pointer move starts the drag in
  // jsdom, which has no real layout to cross an 8px threshold against.
  const sensors = useSensors(useSensor(PointerSensor));
  return (
    <TagMergeGrid
      areas={AREAS}
      selected={new Set()}
      survivor=""
      onToggleSelect={vi.fn()}
      onSplit={vi.fn()}
      onDragEnd={onDragEnd}
      sensors={sensors}
    />
  );
}

describe("TagMergeGrid", () => {
  it("renders one card per area", () => {
    render(<Harness onDragEnd={vi.fn()} />);
    expect(screen.getByText("ok")).toBeInTheDocument();
    expect(screen.getByText("yes")).toBeInTheDocument();
  });

  it("shows a floating overlay while dragging and reports the drop", () => {
    const onDragEnd = vi.fn();
    render(<Harness onDragEnd={onDragEnd} />);

    const card = screen.getByText("yes");
    fireEvent.pointerDown(card, {
      button: 0,
      isPrimary: true,
      pointerId: 1,
      clientX: 0,
      clientY: 0,
    });
    // dnd-kit listens on the document once a drag begins.
    fireEvent.pointerMove(document, { pointerId: 1, clientX: 40, clientY: 40 });

    // The dragged card now appears twice: the dimmed source and the overlay.
    expect(screen.getAllByText("yes")).toHaveLength(2);

    fireEvent.pointerUp(document, { pointerId: 1 });
    expect(onDragEnd).toHaveBeenCalledTimes(1);
  });

  it("dismisses the overlay when the drag is cancelled", async () => {
    const onDragEnd = vi.fn();
    render(<Harness onDragEnd={onDragEnd} />);

    const card = screen.getByText("ok");
    fireEvent.pointerDown(card, {
      button: 0,
      isPrimary: true,
      pointerId: 1,
      clientX: 0,
      clientY: 0,
    });
    fireEvent.pointerMove(document, { pointerId: 1, clientX: 40, clientY: 40 });
    expect(screen.getAllByText("ok")).toHaveLength(2);

    fireEvent.keyDown(document, { key: "Escape", code: "Escape" });
    await waitFor(() => expect(screen.getAllByText("ok")).toHaveLength(1));
    expect(onDragEnd).not.toHaveBeenCalled();
  });
});
