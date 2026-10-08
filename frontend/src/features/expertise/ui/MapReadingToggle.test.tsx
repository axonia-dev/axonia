import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { MapReadingToggle } from "@/features/expertise/ui/MapReadingToggle";
import { ZOOM_MIN, ZOOM_STEP } from "@/features/expertise/ui/terrain-aim";

function draw(overrides: Partial<Parameters<typeof MapReadingToggle>[0]> = {}) {
  return render(
    <MapReadingToggle
      reading="terrain"
      onReadingChange={vi.fn()}
      projection="globe"
      onProjectionChange={vi.fn()}
      zoom={1}
      onZoom={vi.fn()}
      search={null}
      {...overrides}
    />,
  );
}

describe("MapReadingToggle", () => {
  it("offers the two readings of the one set", () => {
    draw();

    expect(screen.getByRole("radio", { name: "Terrain" })).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: "Over time" }),
    ).toBeInTheDocument();
  });

  it("names the reading that is showing", () => {
    draw({ reading: "stream" });

    expect(screen.getByRole("radio", { name: "Over time" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  it("chooses the other reading", async () => {
    const onReadingChange = vi.fn();
    draw({ onReadingChange });

    await userEvent.click(screen.getByRole("radio", { name: "Over time" }));

    expect(onReadingChange).toHaveBeenCalledWith("stream");
  });

  it("offers globe and flat only while the terrain is showing", () => {
    draw();

    expect(screen.getByRole("radio", { name: "Globe" })).toBeInTheDocument();
  });

  it("drops the projection with the terrain, because the stream has none", () => {
    draw({ reading: "stream" });

    expect(
      screen.queryByRole("radio", { name: "Globe" }),
    ).not.toBeInTheDocument();
  });

  it("reaches the zoom without a mouse", async () => {
    const onZoom = vi.fn();
    draw({ onZoom });

    await userEvent.click(screen.getByRole("button", { name: "Zoom in" }));

    expect(onZoom).toHaveBeenCalledWith(ZOOM_STEP);
  });

  it("stops at each end of the range", () => {
    draw({ zoom: ZOOM_MIN });

    expect(screen.getByRole("button", { name: "Zoom out" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Zoom in" })).toBeEnabled();
  });

  /** The stream is a history the reader has to be able to look into; it was once the one reading with no zoom at
   *  all, which left a decade of contributions as a single unreadable smear. */
  it("offers the zoom for the stream too", () => {
    draw({ reading: "stream" });

    expect(screen.getByRole("button", { name: "Zoom in" })).toBeInTheDocument();
  });

  it("says the globe turns and everything else moves", () => {
    const { rerender } = draw();

    expect(screen.getByText("Drag to turn, wheel to zoom")).toBeInTheDocument();

    rerender(
      <MapReadingToggle
        reading="terrain"
        onReadingChange={vi.fn()}
        projection="flat"
        onProjectionChange={vi.fn()}
        search={null}
        zoom={1}
        onZoom={vi.fn()}
      />,
    );

    expect(screen.getByText("Drag to move, wheel to zoom")).toBeInTheDocument();
  });

  it("unwraps the globe", async () => {
    const onProjectionChange = vi.fn();
    draw({ onProjectionChange });

    await userEvent.click(screen.getByRole("radio", { name: "Flat" }));

    expect(onProjectionChange).toHaveBeenCalledWith("flat");
  });
});
