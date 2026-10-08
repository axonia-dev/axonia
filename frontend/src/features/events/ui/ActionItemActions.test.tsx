import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import {
  useDeleteActionItem,
  useEditActionItem,
  useSettleActionItem,
} from "@/features/events/api/use-action-items";
import { useUpcomingSessions } from "@/features/events/api/use-sessions";
import type { ActionItem } from "@/features/events/domain/action-item";
import { ActionItemActions } from "@/features/events/ui/ActionItemActions";
import { useUsers } from "@/features/identity/api/use-users";
import { anActionItem } from "@/test/action-item-fixture";
import { stubMemberSelectField } from "@/test/member-select-fixtures";

vi.mock("@/features/events/api/use-action-items", () => ({
  useEditActionItem: vi.fn(),
  useDeleteActionItem: vi.fn(),
  useSettleActionItem: vi.fn(),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useUpcomingSessions: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const remove = vi.fn();

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.mocked(useEditActionItem).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useDeleteActionItem).mockReturnValue({
    mutate: remove,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useSettleActionItem).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUpcomingSessions).mockReturnValue({
    data: { content: [] },
  } as never);
  stubMemberSelectField({
    useRosterByName: vi.mocked(useRosterByName),
    useMemberSearch: vi.mocked(useMemberSearch),
    useUsers: vi.mocked(useUsers),
  });
});

const item = anActionItem({
  id: 77,
  title: "Renew the certificate",
  canEdit: true,
  canDelete: true,
  canClose: true,
});

function renderActions(overrides: Partial<ActionItem> = {}) {
  render(
    <ActionItemActions slug="backend-guild" item={{ ...item, ...overrides }} />,
  );
}

describe("ActionItemActions", () => {
  it("offers a correction where the read model allows one", () => {
    renderActions();

    expect(
      screen.getByRole("button", { name: "Edit Renew the certificate" }),
    ).toBeInTheDocument();
  });

  it("offers no correction where the read model refuses one", () => {
    renderActions({ canEdit: false });

    expect(screen.queryByRole("button", { name: /^Edit/ })).toBeNull();
  });

  it("offers a removal where the read model allows one", () => {
    renderActions();

    expect(
      screen.getByRole("button", { name: "Delete Renew the certificate" }),
    ).toBeInTheDocument();
  });

  it("offers no removal where the read model refuses one", () => {
    renderActions({ canDelete: false });

    expect(screen.queryByRole("button", { name: /^Delete/ })).toBeNull();
  });

  it("offers no drop icon: settling is the row's own control", () => {
    renderActions();

    expect(
      screen.queryByRole("button", { name: /^Drop / }),
    ).not.toBeInTheDocument();
  });

  it("removes the item once the deletion is confirmed", async () => {
    renderActions();

    await user.click(
      screen.getByRole("button", { name: "Delete Renew the certificate" }),
    );
    await user.click(screen.getByRole("button", { name: "Remove" }));

    expect(remove).toHaveBeenCalledWith(77, expect.anything());
  });

  it("opens the correction on the item's own fields", async () => {
    renderActions();

    await user.click(
      screen.getByRole("button", { name: "Edit Renew the certificate" }),
    );

    expect(screen.getByText("Edit action point")).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveValue("Renew the certificate");
  });
});
