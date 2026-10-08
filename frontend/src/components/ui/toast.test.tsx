import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  notify,
  notifyError,
  toastQueue,
  ToastViewport,
} from "@/components/ui/toast";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

afterEach(() => {
  // The queue is a module singleton; drain it so tests don't bleed into each other.
  for (const toast of [...toastQueue.visibleToasts])
    toastQueue.close(toast.key);
});

describe("toast", () => {
  it("queues the failure notice that notifyError raised", () => {
    // when
    notifyError("Couldn't save the cadence policy.");

    // then
    expect(toastQueue.visibleToasts).toHaveLength(1);
    expect(toastQueue.visibleToasts[0]?.content).toEqual({
      title: "Couldn't save the cadence policy.",
      tone: "error",
    });
  });

  it("renders a queued notice in the viewport and lets the user dismiss it", async () => {
    // given the app-wide region is mounted
    render(<ToastViewport />);

    // when a failure is raised
    act(() => {
      notifyError("Couldn't save the management policy.");
    });

    // then it shows, and the dismiss button clears it
    expect(
      await screen.findByText("Couldn't save the management policy."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /dismiss/i }));
    await waitFor(() =>
      expect(
        screen.queryByText("Couldn't save the management policy."),
      ).toBeNull(),
    );
  });
});

it("renders an action button that runs the handler and dismisses the toast", async () => {
  // given
  render(<ToastViewport />);
  const onPress = vi.fn();

  // when
  act(() => {
    notify("Merged ok → yes", { label: "Undo", onPress });
  });
  expect(await screen.findByText("Merged ok → yes")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Undo" }));

  // then
  expect(onPress).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(screen.queryByText("Merged ok → yes")).toBeNull());
});

it("sits clear of the phone tab bar, and in the corner from the md breakpoint up", async () => {
  // given the shells' bottom clearance on a phone, where a tab bar of about 65 px is fixed to the
  // bottom edge below md
  render(<ToastViewport />);

  // when
  act(() => {
    notify("Saved");
  });

  // then
  const region = (await screen.findByText("Saved")).closest("[role=region]");
  expect(region).toHaveClass("bottom-24", "md:bottom-4");
  expect(region).not.toHaveClass("bottom-4");
});
