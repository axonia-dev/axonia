import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SaveAction } from "@/features/bookmarks/ui/SaveAction";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const save = vi.fn();
const unsave = vi.fn();
let state: { viewerHasSaved: boolean; viewerHelped: boolean | null } = {
  viewerHasSaved: false,
  viewerHelped: null,
};
let onViewCallback: (() => void) | undefined;

let lastStateRef: unknown;
let lastSaveRef: unknown;
let lastUnsaveRef: unknown;

vi.mock("@/features/bookmarks/api/use-save", () => ({
  useSaveState: (ref: unknown) => {
    lastStateRef = ref;
    return { data: state };
  },
  useSave: (ref: unknown, onView?: () => void) => {
    lastSaveRef = ref;
    onViewCallback = onView;
    return { mutate: save, isPending: false };
  },
  useUnsave: (ref: unknown) => {
    lastUnsaveRef = ref;
    return { mutate: unsave, isPending: false };
  },
}));

const navigate = vi.fn();
vi.mock("react-router-dom", async (orig) => ({
  ...(await orig<typeof import("react-router-dom")>()),
  useNavigate: () => navigate,
}));

describe("SaveAction", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state = { viewerHasSaved: false, viewerHelped: null };
    onViewCallback = undefined;
  });

  it("saves when not yet saved", async () => {
    render(<SaveAction target={{ kind: "artifact", id: 100 }} />);
    await user.click(screen.getByRole("button", { name: /save/i }));
    expect(save).toHaveBeenCalled();
  });

  it("shows a saved (pressed) state and unsaves on click", async () => {
    state = { viewerHasSaved: true, viewerHelped: null };
    render(<SaveAction target={{ kind: "artifact", id: 100 }} />);
    const btn = screen.getByRole("button", { name: /saved/i });
    expect(btn).toHaveAttribute("aria-pressed", "true");
    await user.click(btn);
    expect(unsave).toHaveBeenCalled();
  });

  it("never renders a save count", () => {
    state = { viewerHasSaved: true, viewerHelped: null };
    render(<SaveAction target={{ kind: "artifact", id: 100 }} />);
    expect(screen.queryByText(/\d+/)).not.toBeInTheDocument();
  });

  it("wires the toast's View action to navigate to /me/saved", () => {
    render(<SaveAction target={{ kind: "artifact", id: 100 }} />);
    onViewCallback?.();
    expect(navigate).toHaveBeenCalledWith("/me/saved");
  });

  it("reflects useSaveState for a session target", async () => {
    state = { viewerHasSaved: true, viewerHelped: null };
    render(<SaveAction target={{ kind: "session", id: 42 }} />);
    const btn = screen.getByRole("button", { name: /saved/i });
    expect(btn).toHaveAttribute("aria-pressed", "true");
    expect(lastStateRef).toEqual({ kind: "session", id: 42 });
    await user.click(btn);
    expect(unsave).toHaveBeenCalled();
    expect(lastUnsaveRef).toEqual({ kind: "session", id: 42 });
    expect(lastSaveRef).toEqual({ kind: "session", id: 42 });
  });
});
