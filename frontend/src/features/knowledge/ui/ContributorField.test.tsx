import { render, renderHook, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMemberSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import {
  ContributorField,
  useCredit,
} from "@/features/knowledge/ui/ContributorField";

vi.mock("@/auth/current-user", () => ({ currentUserId: () => "olive" }));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: () => ({ data: {} }),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useRosterByName: vi.fn(),
  useMemberSearch: vi.fn(),
}));

const mockUseRosterByName = vi.mocked(useRosterByName);
const mockUseMemberSearch = vi.mocked(useMemberSearch);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUseRosterByName.mockReturnValue({
    data: { pages: [{ content: [{ userId: "bob" }] }] },
    hasNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
  mockUseMemberSearch.mockReturnValue({
    data: undefined,
    isFetching: false,
  } as never);
});

describe("useCredit", () => {
  it("credits the sole presenter without asking, for whoever may credit", () => {
    const { result } = renderHook(() => useCredit(["alice"], true));

    expect(result.current.credited).toEqual({ contributor: "alice" });
    expect(result.current.mustChoose).toBe(false);
  });

  it("names nobody when the caller may not credit, whoever presented", () => {
    const { result } = renderHook(() => useCredit(["alice", "bob"], false));

    expect(result.current.credited).toEqual({});
    expect(result.current.mustChoose).toBe(false);
  });

  it("waits for a choice between co-presenters", () => {
    const { result } = renderHook(() => useCredit(["alice", "bob"], true));

    expect(result.current.mustChoose).toBe(true);
  });
});

interface HarnessProps {
  canCredit: boolean;
  presenterUserIds: string[];
}

function Harness({ canCredit, presenterUserIds }: Readonly<HarnessProps>) {
  const credit = useCredit(presenterUserIds, canCredit);
  return (
    <>
      <ContributorField
        slug="backend-guild"
        presenterUserIds={presenterUserIds}
        credit={credit}
      />
      <output>{JSON.stringify(credit.credited)}</output>
    </>
  );
}

function renderField(overrides: Partial<HarnessProps> = {}) {
  render(<Harness canCredit presenterUserIds={[]} {...overrides} />);
}

describe("ContributorField", () => {
  it("renders nothing for a caller who may not credit", () => {
    renderField({ canCredit: false });

    expect(screen.queryByRole("combobox")).toBeNull();
    expect(screen.queryByRole("button", { name: /Contributor/ })).toBeNull();
  });

  it("credits the member picked from the roster when there are no presenters", async () => {
    renderField();

    await user.click(screen.getByRole("combobox", { name: "Contributor" }));
    await user.click(screen.getByRole("option", { name: "bob" }));

    expect(screen.getByRole("status")).toHaveTextContent(
      '{"contributor":"bob"}',
    );
  });

  /** A slot with presenters credits one of them by a fixed choice, not a roster search: the
   *  field appears only when there is nobody to choose from. */
  it("offers the member field only when the slot has no presenters", () => {
    renderField({ presenterUserIds: [] });

    expect(screen.getByRole("combobox")).toBeInTheDocument();
  });

  it("offers a fixed choice when the slot has several presenters", () => {
    renderField({ presenterUserIds: ["u-ana", "u-bea"] });

    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /Contributor/ }),
    ).toBeInTheDocument();
  });
});
