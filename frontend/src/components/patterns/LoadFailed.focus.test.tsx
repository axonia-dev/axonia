import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useRef } from "react";
import { describe, expect, it, vi } from "vitest";

import { LoadFailed } from "@/components/patterns/LoadFailed";
import {
  focusIfDropped,
  useAnsweredAfterFailing,
} from "@/lib/use-answered-after-failing";

const READ = { isFetching: false, refetch: vi.fn() };

/** A field in place of the failure, as a form draws one once the read answers. */
function Recovered() {
  const field = useRef<HTMLButtonElement>(null);
  useEffect(() => focusIfDropped(field.current), []);
  return <button ref={field}>Kind</button>;
}

function Region({ failed }: Readonly<{ failed: boolean }>) {
  return (
    <div>
      <input aria-label="Name" />
      {failed ? (
        <LoadFailed inline message="Could not load." query={READ} />
      ) : (
        <Recovered />
      )}
    </div>
  );
}

/** A field that takes the focus only once its own read answers after failing, as Select does. */
function Kind({ takesFocus }: Readonly<{ takesFocus: boolean }>) {
  const field = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (takesFocus) focusIfDropped(field.current);
  }, [takesFocus]);
  return <button ref={field}>Kind</button>;
}

function KindRegion({ failed }: Readonly<{ failed: boolean }>) {
  const recovered = useAnsweredAfterFailing(failed);
  return (
    <div>
      {failed ? (
        <LoadFailed inline message="Could not load the kinds." query={READ} />
      ) : (
        <Kind takesFocus={recovered} />
      )}
    </div>
  );
}

/** A page whose own read failed first, with a field inside whose read may fail later. */
function Page({
  failed,
  kindsFailed,
}: Readonly<{ failed: boolean; kindsFailed: boolean }>) {
  return (
    <div>
      {failed ? (
        <LoadFailed inline message="Could not load the page." query={READ} />
      ) : (
        <KindRegion failed={kindsFailed} />
      )}
    </div>
  );
}

/** Lets the task that rendered last end, as time passes between two reads in the app. */
async function aMomentLater(): Promise<void> {
  await act(() => new Promise((resolve) => setTimeout(resolve, 0)));
}

describe("the focus a failure message holds as it goes", () => {
  it("passes to the field that replaces it", async () => {
    // given
    const user = userEvent.setup();
    const { rerender } = render(<Region failed />);
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // when
    rerender(<Region failed={false} />);

    // then
    expect(screen.getByRole("button", { name: "Kind" })).toHaveFocus();
  });

  it("is not taken when the message did not hold it", () => {
    // given
    const { rerender } = render(<Region failed />);

    // when
    rerender(<Region failed={false} />);

    // then
    expect(screen.getByRole("button", { name: "Kind" })).not.toHaveFocus();
    expect(document.body).toHaveFocus();
  });

  it("is not taken when another field holds it", async () => {
    // given
    const user = userEvent.setup();
    const { rerender } = render(<Region failed />);
    await user.click(screen.getByRole("textbox", { name: "Name" }));

    // when
    rerender(<Region failed={false} />);

    // then
    expect(screen.getByRole("textbox", { name: "Name" })).toHaveFocus();
  });

  it("is not taken later by a field whose own read fails and answers on its own", async () => {
    // given
    const user = userEvent.setup();
    const { rerender } = render(<Page failed kindsFailed={false} />);
    await user.click(screen.getByRole("button", { name: "Try again" }));
    rerender(<Page failed={false} kindsFailed={false} />);
    await aMomentLater();
    rerender(<Page failed={false} kindsFailed />);

    // when
    rerender(<Page failed={false} kindsFailed={false} />);

    // then
    expect(screen.getByRole("button", { name: "Kind" })).not.toHaveFocus();
    expect(document.body).toHaveFocus();
  });
});
