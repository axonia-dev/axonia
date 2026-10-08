import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { setCurrentUserId } from "@/auth/current-user";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useReactionDefinitions,
  useReactionEligibility,
} from "@/features/recognition/api/use-reactions";
import {
  COMMON_HEADING,
  FREQUENT_HEADING,
} from "@/features/recognition/domain/picker-groups";
import { EmojiPicker } from "@/features/recognition/ui/EmojiPicker";
import {
  rememberRecent,
  storedRecents,
} from "@/features/recognition/ui/reaction-recents";

vi.mock("@/features/recognition/api/use-reactions", () => ({
  useReactionDefinitions: vi.fn(),
  useReactionEligibility: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  localStorage.clear();
  setCurrentUserId("member-1");
  vi.mocked(useReactionDefinitions).mockReturnValue({
    data: [
      {
        id: 1,
        name: "ship-it",
        imageUrl: "https://cdn.example/ship-it.png",
        pending: false,
        proposedByUserId: null,
      },
    ],
  } as never);
  vi.mocked(useReactionEligibility).mockReturnValue({
    data: {
      mayPropose: true,
      openProposals: 0,
      openProposalLimit: 3,
      approvedLimit: 60,
    },
  } as never);
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: { id: "org-1", name: "Acme Robotics", kind: "HUB" },
  } as never);
});

afterEach(() => {
  setCurrentUserId(undefined);
});

/** The organization's own group is on screen well before the catalog has loaded, so a bare
 *  `findAllByRole` resolves on a list of one and any assertion about order reads `undefined`.
 *  Waiting on the last standing group is what makes the order assertable. */
async function loadedGroups(): Promise<HTMLElement[]> {
  await screen.findByRole("group", { name: "Flags" });
  return screen.getAllByRole("group");
}

describe("EmojiPicker", () => {
  it("heads the organization's own reactions with its bare name, before every other group", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    expect((await loadedGroups())[0]).toHaveAccessibleName("Acme Robotics");
  });

  it("offers the common set above the alphabetical catalog", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    expect((await loadedGroups())[1]).toHaveAccessibleName(COMMON_HEADING);
  });

  it("leaves the heart out of the common set, so the one crediting gesture stays alone", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    const common = (await loadedGroups())[1] as HTMLElement;
    expect(
      within(common).queryByRole("button", { name: "red heart" }),
    ).toBeNull();
  });

  it("shows what this member reached for lately, once they have reached for something", async () => {
    rememberRecent("🧠");

    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    expect((await loadedGroups())[2]).toHaveAccessibleName(FREQUENT_HEADING);
  });

  it("leaves the frequent group out until this member has picked something", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await loadedGroups();
    expect(screen.queryByRole("group", { name: FREQUENT_HEADING })).toBeNull();
  });

  it("remembers a standard emoji the moment it is picked", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "brain" }));

    expect(storedRecents()).toEqual(["🧠"]);
  });

  it("remembers nothing for a custom reaction, which already stands in its own group", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "ship-it" }));

    expect(storedRecents()).toEqual([]);
  });

  it("names the entry under the pointer, for a grid whose buttons carry no words", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await user.hover(await screen.findByRole("button", { name: "brain" }));

    expect(await screen.findByText("brain")).toBeInTheDocument();
  });

  it("names a custom reaction, so the word is readable at the moment of choosing", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    expect(
      await screen.findByRole("button", { name: "ship-it" }),
    ).toBeInTheDocument();
  });

  it("hides the add entry from somebody not entitled", async () => {
    vi.mocked(useReactionEligibility).mockReturnValue({
      data: {
        mayPropose: false,
        openProposals: 0,
        openProposalLimit: 3,
        approvedLimit: 60,
      },
    } as never);

    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: /^add a reaction$/i }),
      ).toBeNull(),
    );
  });

  it("keeps the footer's height off its content, so the panel does not resize under the pointer", async () => {
    vi.mocked(useReactionEligibility).mockReturnValue({
      data: {
        mayPropose: false,
        openProposals: 0,
        openProposalLimit: 3,
        approvedLimit: 60,
      },
    } as never);

    const { container } = render(
      <EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />,
    );
    await screen.findByRole("searchbox");

    // A member who may not propose leaves that row holding nothing but the hovered word, so a
    // height taken from its content is zero until the pointer reaches a glyph - and the whole
    // popover grew a line the moment it did. The fixed height is the assertion.
    expect(container.firstElementChild?.lastElementChild).toHaveClass("h-10");
  });

  it("shows the add entry when the viewer may propose", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    expect(
      await screen.findByRole("button", { name: /^add a reaction$/i }),
    ).toBeInTheDocument();
  });

  it("calls onPropose when the add entry is pressed", async () => {
    const onPropose = vi.fn();
    render(<EmojiPicker onPick={vi.fn()} onPropose={onPropose} />);

    await user.click(
      await screen.findByRole("button", { name: /^add a reaction$/i }),
    );

    expect(onPropose).toHaveBeenCalledOnce();
  });

  it("filters as the term is typed", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await user.type(await screen.findByRole("searchbox"), "rocket");

    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /rocket/i }),
      ).toBeInTheDocument(),
    );
  });

  it("hands back an EMOJI ref with no definition id", async () => {
    const onPick = vi.fn();
    render(<EmojiPicker onPick={onPick} onPropose={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "brain" }));

    expect(onPick).toHaveBeenCalledWith({ kind: "EMOJI", emoji: "🧠" });
  });

  it("hands back a CUSTOM ref with no emoji", async () => {
    const onPick = vi.fn();
    render(<EmojiPicker onPick={onPick} onPropose={vi.fn()} />);

    await user.click(await screen.findByRole("button", { name: "ship-it" }));

    expect(onPick).toHaveBeenCalledWith({ kind: "CUSTOM", definitionId: 1 });
  });

  it("autofocuses the search field, so typing works the instant it opens", async () => {
    render(<EmojiPicker onPick={vi.fn()} onPropose={vi.fn()} />);

    await waitFor(() => expect(screen.getByRole("searchbox")).toHaveFocus());
  });
});
