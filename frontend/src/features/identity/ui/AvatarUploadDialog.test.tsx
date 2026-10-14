import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps, ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useClearAvatar,
  useSetAvatar,
} from "@/features/identity/api/use-avatar";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/identity/api/use-avatar", () => ({
  useSetAvatar: vi.fn(),
  useClearAvatar: vi.fn(),
}));

import { AvatarUploadDialog } from "./AvatarUploadDialog";

const mockUseSetAvatar = vi.mocked(useSetAvatar);
const mockUseClearAvatar = vi.mocked(useClearAvatar);

type DialogProps = ComponentProps<typeof AvatarUploadDialog>;

function withQueryClient({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function dialog(props?: Partial<DialogProps>) {
  return (
    <AvatarUploadDialog
      isOpen
      onOpenChange={vi.fn()}
      hasOverride={false}
      currentUrl={null}
      {...props}
    />
  );
}

function renderDialog(props?: Partial<DialogProps>) {
  return render(dialog(props), { wrapper: withQueryClient });
}

/** `FileTrigger` keeps its `input type=file` hidden and unnamed - the panel around it is
 *  what carries the affordance - so a chosen file has to be handed to the input directly. */
function pictureInput() {
  const input = screen
    .getByRole("dialog")
    .querySelector<HTMLInputElement>('input[type="file"]');
  if (input === null) {
    throw new Error("file input not found");
  }
  return input;
}

/** The footer is the row the action buttons share, and the only one under test here:
 *  the picker contributes buttons of its own to the dialog. */
function footerButtons() {
  const footer = screen.getByRole("button", { name: "Cancel" }).parentElement;
  if (footer === null) {
    throw new Error("footer not found");
  }
  return within(footer)
    .getAllByRole("button")
    .map((button) => button.textContent);
}

function stubSetAvatar(overrides?: { isPending?: boolean }) {
  const mutate = vi.fn((_file: File, opts?: { onSuccess?: () => void }) =>
    opts?.onSuccess?.(),
  );
  mockUseSetAvatar.mockReturnValue({
    mutate,
    isPending: overrides?.isPending ?? false,
  } as unknown as ReturnType<typeof useSetAvatar>);
  return mutate;
}

function stubClearAvatar(overrides?: { isPending?: boolean }) {
  const mutate = vi.fn((_variables: void, opts?: { onSuccess?: () => void }) =>
    opts?.onSuccess?.(),
  );
  mockUseClearAvatar.mockReturnValue({
    mutate,
    isPending: overrides?.isPending ?? false,
  } as unknown as ReturnType<typeof useClearAvatar>);
  return mutate;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stubSetAvatar();
  stubClearAvatar();
});

describe("AvatarUploadDialog", () => {
  it("uploads the chosen file and closes", async () => {
    const setAvatar = stubSetAvatar();
    const onOpenChange = vi.fn();
    renderDialog({ onOpenChange });

    await user.upload(
      pictureInput(),
      new File(["bytes"], "me.png", { type: "image/png" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(setAvatar).toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("offers removal only when there is an override to remove", () => {
    const { rerender } = renderDialog();
    expect(
      screen.queryByRole("button", { name: "Remove" }),
    ).not.toBeInTheDocument();

    rerender(dialog({ hasOverride: true }));
    expect(screen.getByRole("button", { name: "Remove" })).toBeInTheDocument();
  });

  it("refuses a file over the size limit without calling the gateway", async () => {
    const setAvatar = stubSetAvatar();
    renderDialog();

    await user.upload(
      pictureInput(),
      new File([new Uint8Array(3 * 1024 * 1024)], "big.png", {
        type: "image/png",
      }),
    );

    expect(screen.getByRole("alert")).toHaveTextContent("2 MB");
    expect(setAvatar).not.toHaveBeenCalled();
  });

  it("names the dialog Profile picture and hints at the accepted size and formats", () => {
    renderDialog();

    expect(
      screen.getByRole("dialog", { name: "Profile picture" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("PNG, JPEG or WebP, up to 2 MB"),
    ).toBeInTheDocument();
  });

  it("orders the footer Cancel, Remove, Save", () => {
    renderDialog({ hasOverride: true });

    expect(footerButtons()).toEqual(["Cancel", "Remove", "Save"]);
  });

  it("removes the override and closes", async () => {
    const clearAvatar = stubClearAvatar();
    const onOpenChange = vi.fn();
    renderDialog({ hasOverride: true, onOpenChange });

    await user.click(screen.getByRole("button", { name: "Remove" }));

    expect(clearAvatar).toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("shows a human message when the upload is rejected", async () => {
    mockUseSetAvatar.mockReturnValue({
      mutate: (_file: File, opts: { onError: () => void }) => opts.onError(),
      isPending: false,
    } as unknown as ReturnType<typeof useSetAvatar>);
    renderDialog();

    await user.upload(
      pictureInput(),
      new File(["bytes"], "me.png", { type: "image/png" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not save the picture. Try again.",
    );
  });

  it("shows the server's own message when the rejection carries one", async () => {
    mockUseSetAvatar.mockReturnValue({
      mutate: (_file: File, opts: { onError: (error: unknown) => void }) =>
        opts.onError(
          new ApiError(
            400,
            "Unsupported image type. Use PNG, JPEG or WebP",
            "UNSUPPORTED_MEDIA_TYPE",
          ),
        ),
      isPending: false,
    } as unknown as ReturnType<typeof useSetAvatar>);
    renderDialog();

    await user.upload(
      pictureInput(),
      new File(["bytes"], "me.png", { type: "image/png" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Unsupported image type. Use PNG, JPEG or WebP",
    );
  });

  it("does not carry a previous preview or error across a close and reopen", async () => {
    const { rerender } = renderDialog();

    await user.upload(
      pictureInput(),
      new File([new Uint8Array(3 * 1024 * 1024)], "big.png", {
        type: "image/png",
      }),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("2 MB");

    rerender(dialog({ isOpen: false }));
    rerender(dialog());

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
});
