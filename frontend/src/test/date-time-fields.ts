import { screen } from "@testing-library/react";
import type userEvent from "@testing-library/user-event";

type User = ReturnType<typeof userEvent.setup>;

/**
 * Fills a `DateTimePicker`'s segments the way an organizer does: a digit at a time, letting each
 * filled segment hand over to the next. Shared by every form that mounts the control, because a
 * near-copy per test file is how two of them come to disagree about what "typed 18:00" means.
 *
 * The hour goes in on the twelve-hour clock the field renders under the suite's locale, so
 * `18:00` is 06:00 followed by the PM half.
 */
export async function typeDateTime(user: User, date: string, time: string) {
  const [year, month, day] = date.split("-");
  await user.click(screen.getByRole("spinbutton", { name: /month/i }));
  await user.keyboard(`${month}${day}${year}`);
  const [hours, minutes] = time.split(":");
  await user.click(screen.getByRole("spinbutton", { name: /hour/i }));
  await user.keyboard(String(Number(hours) % 12 || 12).padStart(2, "0"));
  await user.keyboard(`${minutes}`);
  await user.keyboard(Number(hours) < 12 ? "a" : "p");
}
