import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useClearAvatar,
  useSetAvatar,
} from "@/features/identity/api/use-avatar";
import { fitImage } from "@/lib/fit-image";

import { AvatarUploadDialog } from "./AvatarUploadDialog";

vi.mock("@/features/identity/api/use-avatar", () => ({
  useSetAvatar: vi.fn(),
  useClearAvatar: vi.fn(),
}));
vi.mock("@/lib/fit-image", () => ({
  fitImage: vi.fn((file: File) => Promise.resolve(file)),
}));

const mockFitImage = vi.mocked(fitImage);

function withQueryClient({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient();
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function renderDialog() {
  render(
    <AvatarUploadDialog
      isOpen
      onOpenChange={vi.fn()}
      hasOverride={false}
      currentUrl={null}
    />,
    { wrapper: withQueryClient },
  );
}

function pictureInput() {
  const input = screen
    .getByRole("dialog")
    .querySelector<HTMLInputElement>('input[type="file"]');
  if (input === null) {
    throw new Error("file input not found");
  }
  return input;
}

const PHOTO = new File([new Uint8Array(3 * 1024 * 1024)], "me.jpg", {
  type: "image/jpeg",
});

let user: ReturnType<typeof userEvent.setup>;
let setAvatar: ReturnType<typeof vi.fn>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  setAvatar = vi.fn();
  vi.mocked(useSetAvatar).mockReturnValue({
    mutate: setAvatar,
    isPending: false,
  } as never);
  vi.mocked(useClearAvatar).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("AvatarUploadDialog fitting", () => {
  it("shrinks the picture before the size check, so a large photo that fits is saved", async () => {
    // given
    const fitted = new File(["small"], "me.jpg", { type: "image/jpeg" });
    mockFitImage.mockResolvedValueOnce(fitted);
    renderDialog();

    // when
    await user.upload(pictureInput(), PHOTO);
    await user.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(mockFitImage).toHaveBeenCalledWith(PHOTO, {
      maxEdge: 512,
      maxBytes: 2 * 1024 * 1024,
    });
    expect(setAvatar.mock.calls[0]?.[0]).toBe(fitted);
  });

  it("refuses an unsupported type before fitting it", async () => {
    // given
    renderDialog();
    const osFileDialog = user.setup({ applyAccept: false });

    // when
    await osFileDialog.upload(
      pictureInput(),
      new File([new Uint8Array(3 * 1024 * 1024)], "me.gif", {
        type: "image/gif",
      }),
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Unsupported image type. Use PNG, JPEG or WebP.",
    );
    expect(mockFitImage).not.toHaveBeenCalled();
  });

  it("holds Save while the picture is being fitted", async () => {
    // given
    let resolve: (file: File) => void = () => {};
    mockFitImage.mockReturnValueOnce(
      new Promise<File>((settle) => {
        resolve = settle;
      }),
    );
    renderDialog();

    // when
    await user.upload(pictureInput(), PHOTO);

    // then
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    resolve(new File(["small"], "me.jpg", { type: "image/jpeg" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Save" })).toBeEnabled(),
    );
  });
});
