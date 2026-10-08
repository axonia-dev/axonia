import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MembersToolbar } from "@/features/community/ui/MembersToolbar";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderToolbar(
  over: Partial<Parameters<typeof MembersToolbar>[0]> = {},
) {
  render(
    <MembersToolbar
      counts={{ all: 16, organizers: 2, members: 14 }}
      segment="all"
      onSegmentChange={vi.fn()}
      query=""
      onQueryChange={vi.fn()}
      {...over}
    />,
  );
}

describe("MembersToolbar", () => {
  it("shows live counts in the segment labels", () => {
    renderToolbar();
    expect(screen.getByRole("radio", { name: /All · 16/ })).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /Organizers · 2/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /Members · 14/ }),
    ).toBeInTheDocument();
  });

  it("reports a segment change", async () => {
    const onSegmentChange = vi.fn();
    renderToolbar({ onSegmentChange });
    await user.click(screen.getByRole("radio", { name: /Organizers · 2/ }));
    expect(onSegmentChange).toHaveBeenCalledWith("organizers");
  });

  it("reports search input", async () => {
    const onQueryChange = vi.fn();
    renderToolbar({ onQueryChange });
    await user.type(
      screen.getByRole("searchbox", { name: /search members/i }),
      "a",
    );
    expect(onQueryChange).toHaveBeenCalledWith("a");
  });
});
