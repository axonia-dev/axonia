import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMessagePreview } from "@/features/events/api/use-session";
import type { ReleasePreviewCopy } from "@/features/events/ui/ReleasePreviewDialog";
import { ReleasePreviewDialog } from "@/features/events/ui/ReleasePreviewDialog";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/events/api/use-session", () => ({
  useMessagePreview: vi.fn(),
}));

function mockPreview(overrides: {
  data?: { subject: string; html: string };
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  vi.mocked(useMessagePreview).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    ...overrides,
  } as never);
}

const wrapUp: ReleasePreviewCopy = {
  messageType: "session_wrap_up",
  dialogTitle: "Wrap-up preview",
  iframeTitle: "Wrap-up email preview",
};

function renderDialog(isOpen: boolean) {
  return render(
    <ReleasePreviewDialog
      slug="backend-guild"
      sessionId={7}
      copy={wrapUp}
      isOpen={isOpen}
      onOpenChange={vi.fn()}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockPreview({});
});

describe("ReleasePreviewDialog - requesting the preview", () => {
  it("gates the request on the dialog's own open state", () => {
    renderDialog(false);

    expect(useMessagePreview).toHaveBeenCalledWith(
      "backend-guild",
      7,
      "session_wrap_up",
      false,
    );
  });

  it("requests the preview once open", () => {
    renderDialog(true);

    expect(useMessagePreview).toHaveBeenCalledWith(
      "backend-guild",
      7,
      "session_wrap_up",
      true,
    );
  });
});

describe("ReleasePreviewDialog - rendering the result", () => {
  it("shows a loading notice while the render is in flight", () => {
    mockPreview({ isLoading: true });
    renderDialog(true);

    expect(screen.getByText(/loading preview/i)).toBeInTheDocument();
  });

  it("surfaces a failed render through eventsErrorMessage, as an alert", () => {
    mockPreview({ isError: true, error: new ApiError(500, "x") });
    renderDialog(true);

    expect(screen.getByRole("alert")).toHaveTextContent(
      /something went wrong/i,
    );
  });

  it("renders the subject and the HTML inside a sandboxed, titled iframe", () => {
    mockPreview({
      data: {
        subject: "Platform Guild wrapped up",
        html: "<p>Thanks for joining</p>",
      },
    });
    renderDialog(true);

    expect(screen.getByText("Platform Guild wrapped up")).toBeInTheDocument();
    const iframe = screen.getByTitle("Wrap-up email preview");
    expect(iframe.tagName).toBe("IFRAME");
    expect(iframe).toHaveAttribute("srcdoc", "<p>Thanks for joining</p>");
    // An empty `sandbox` attribute is the strictest setting - present, but
    // granting no permissions back to the rendered mail HTML.
    expect(iframe).toHaveAttribute("sandbox", "");
  });
});
