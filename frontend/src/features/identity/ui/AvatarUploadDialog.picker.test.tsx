import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useClearAvatar,
  useSetAvatar,
} from "@/features/identity/api/use-avatar";
import { fileTransfer } from "@/test/file-drop";

// Split out of AvatarUploadDialog.test.tsx (300-line ceiling): that file covers
// what the dialog does with a picture, this one covers how a picture gets in -
// the affordance, the preview, and the drop target.

vi.mock("@/features/identity/api/use-avatar", () => ({
  useSetAvatar: vi.fn(),
  useClearAvatar: vi.fn(),
}));

import { AvatarUploadDialog } from "./AvatarUploadDialog";

const mockUseSetAvatar = vi.mocked(useSetAvatar);
const mockUseClearAvatar = vi.mocked(useClearAvatar);

function withQueryClient({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
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

/** The DropZone root holds the drop handlers; its accessible label sits on an inner button. */
function pictureDropZone() {
  const zone = screen
    .getByLabelText("Profile picture drop zone")
    .closest(".border-dashed");
  if (zone === null) {
    throw new Error("drop zone not found");
  }
  return zone;
}

function stubSetAvatar() {
  const mutate = vi.fn((_file: File, opts?: { onSuccess?: () => void }) =>
    opts?.onSuccess?.(),
  );
  mockUseSetAvatar.mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useSetAvatar>);
  return mutate;
}

function renderDialog(currentUrl: string | null = null) {
  render(
    <AvatarUploadDialog
      isOpen
      onOpenChange={vi.fn()}
      hasOverride={false}
      currentUrl={currentUrl}
    />,
    { wrapper: withQueryClient },
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  stubSetAvatar();
  mockUseClearAvatar.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useClearAvatar>);
});

describe("AvatarUploadDialog picker", () => {
  it("names the picker so that it reads as pressable rather than as a bare file input", () => {
    renderDialog();

    expect(
      screen.getByRole("button", { name: /drag an image here, or browse/i }),
    ).toBeInTheDocument();
  });

  it("opens on the picture already in force rather than on the empty panel", () => {
    renderDialog("https://cdn.example.test/avatar.png");

    expect(
      screen.getByRole("img", { name: "Current picture" }),
    ).toHaveAttribute("src", "https://cdn.example.test/avatar.png");
    expect(
      screen.getByRole("button", { name: /drag an image here, or browse/i }),
    ).toBeInTheDocument();
  });

  it("replaces the picture in force with the one just chosen", async () => {
    renderDialog("https://cdn.example.test/avatar.png");

    await user.upload(
      pictureInput(),
      new File(["bytes"], "new.png", { type: "image/png" }),
    );

    expect(
      screen.queryByRole("img", { name: "Current picture" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Preview" })).toBeInTheDocument();
  });

  it("previews the chosen picture and names the file", async () => {
    renderDialog();

    await user.upload(
      pictureInput(),
      new File(["bytes"], "me.png", { type: "image/png" }),
    );

    expect(screen.getByRole("img", { name: "Preview" })).toBeInTheDocument();
    expect(screen.getByText("me.png")).toBeInTheDocument();
  });

  it("marks the panel as the drop target while an image is dragged over it", () => {
    renderDialog();
    const file = new File(["x"], "dragged.png", { type: "image/png" });

    fireEvent.dragEnter(pictureDropZone(), {
      dataTransfer: fileTransfer(file),
    });

    expect(pictureDropZone()).toHaveAttribute("data-drop-target");
  });

  it("takes an image dropped onto the picker", async () => {
    const setAvatar = stubSetAvatar();
    renderDialog();
    const file = new File(["bytes"], "dropped.png", { type: "image/png" });

    fireEvent.drop(pictureDropZone(), { dataTransfer: fileTransfer(file) });

    await waitFor(() =>
      expect(screen.getByText("dropped.png")).toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(setAvatar).toHaveBeenCalledWith(file, expect.anything());
  });

  it("ignores a dropped file that is not an accepted image", () => {
    renderDialog();
    const file = new File(["x"], "notes.pdf", { type: "application/pdf" });

    fireEvent.drop(pictureDropZone(), { dataTransfer: fileTransfer(file) });

    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });
});
