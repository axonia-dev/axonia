import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import { NewMembers } from "@/features/community/ui/NewMembers";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/community/api/use-membership");
vi.mock("@/features/identity/api/use-users");

const thisMonthJoiner = {
  userId: "u1",
  joinedAt: new Date().toISOString(),
};

function mockMembers(content: unknown[], isPending = false) {
  vi.mocked(useMembers).mockReturnValue({
    data: { content },
    isPending,
    isError: false,
  } as never);
}

beforeEach(() => {
  mockMembers([thisMonthJoiner]);
  vi.mocked(useUsers).mockReturnValue({
    data: { u1: { id: "u1", displayName: "Dana" } },
  } as never);
});

describe("NewMembers", () => {
  it("celebrates the newest joiner", () => {
    render(<NewMembers slug="guild" />);
    expect(screen.getByText(/dana just joined/i)).toHaveTextContent(
      /^Dana just joined$/,
    );
    expect(screen.getByText(/\+1 new this month/i)).toBeInTheDocument();
  });

  it("renders nothing when no one joined this month", () => {
    mockMembers([{ userId: "old", joinedAt: "2000-01-01T00:00:00Z" }]);
    const { container } = render(<NewMembers slug="guild" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows profile pictures when available", () => {
    vi.mocked(useUsers).mockReturnValue({
      data: {
        u1: {
          id: "u1",
          displayName: "Dana",
          avatarUrl: "https://cdn.example.com/dana.png",
        },
      },
    } as never);
    render(<NewMembers slug="guild" />);
    expect(screen.getByRole("img", { name: "Dana" })).toHaveAttribute(
      "src",
      "https://cdn.example.com/dana.png",
    );
  });
});
