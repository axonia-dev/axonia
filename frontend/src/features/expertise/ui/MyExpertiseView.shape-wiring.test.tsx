import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { useParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import {
  useDismissSignal,
  useHideTag,
  useSetOptOut,
  useUnhideTag,
} from "@/features/expertise/api/use-expertise-prefs";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import { MyExpertiseView } from "@/features/expertise/ui/MyExpertiseView";
import { ShapeSection } from "@/features/expertise/ui/ShapeSection";
import { useMe } from "@/features/identity/api/use-me";

vi.mock("@/features/expertise/api/use-self-view", () => ({
  useSelfView: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useSetOptOut: vi.fn(),
  useHideTag: vi.fn(),
  useUnhideTag: vi.fn(),
  useDismissSignal: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useParams: vi.fn(() => ({})),
}));
vi.mock("@/features/expertise/api/use-declarations", () => ({
  useRemoveDeclaration: vi.fn(),
}));
vi.mock("@/features/expertise/ui/AddDeclarationField", () => ({
  AddDeclarationField: ({ slug }: { slug: string }) => (
    <p>add field for {slug}</p>
  ),
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("@/features/expertise/ui/ShapeSection", () => ({
  ShapeSection: vi.fn(() => null),
}));

const mockUseSelfView = vi.mocked(useSelfView);
const mockUseParams = vi.mocked(useParams);
const mockUseCommunity = vi.mocked(useCommunity);

function mutation() {
  return { mutate: vi.fn(), isPending: false } as never;
}

function renderView() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MyExpertiseView />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSelfView.mockReturnValue({
    data: {
      optedOut: false,
      areas: [],
      declarations: [],
      declarableCommunities: [],
    },
    isLoading: false,
  } as never);
  vi.mocked(useSetOptOut).mockReturnValue(mutation());
  vi.mocked(useHideTag).mockReturnValue(mutation());
  vi.mocked(useUnhideTag).mockReturnValue(mutation());
  vi.mocked(useDismissSignal).mockReturnValue(mutation());
  vi.mocked(useRemoveDeclaration).mockReturnValue({
    mutate: vi.fn(),
    isRemoving: () => false,
  } as never);
  vi.mocked(useMe).mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant" },
  } as never);
  mockUseParams.mockReturnValue({});
  mockUseCommunity.mockReturnValue({
    data: undefined,
    isLoading: false,
  } as never);
});

describe("MyExpertiseView wiring the shape section", () => {
  it("hands the section no page and the all-communities lens on the global page", () => {
    renderView();
    expect(vi.mocked(ShapeSection)).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: null, lensId: ALL_COMMUNITIES }),
      undefined,
    );
  });

  it("reads the organization-wide self view on the global page", () => {
    renderView();
    expect(mockUseSelfView).toHaveBeenLastCalledWith(undefined);
  });

  it("reads the page's community alone on a community page", () => {
    mockUseParams.mockReturnValue({ slug: "engineering" });
    renderView();
    expect(mockUseSelfView).toHaveBeenLastCalledWith("engineering");
  });

  it("hands the section the page's community", () => {
    mockUseParams.mockReturnValue({ slug: "engineering" });
    mockUseCommunity.mockReturnValue({
      data: { id: 1, slug: "engineering", name: "Engineering" },
      isLoading: false,
    } as never);
    renderView();
    expect(vi.mocked(ShapeSection)).toHaveBeenLastCalledWith(
      expect.objectContaining({
        page: { id: 1, slug: "engineering", name: "Engineering" },
        lensId: ALL_COMMUNITIES,
      }),
      undefined,
    );
  });
});
