import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { FEEDBACK_WORDS } from "@/features/events/domain/feedback-words";
import { FeedbackRow } from "@/features/events/ui/FeedbackRow";
import { riseMotes } from "@/features/events/ui/rising-motes";
import {
  prefersReducedMotion,
  restoreMotion,
  stubAnimate,
} from "@/test/motion";

vi.mock("@/features/events/ui/rising-motes", () => ({ riseMotes: vi.fn() }));

const mockRiseMotes = vi.mocked(riseMotes);

function Harness() {
  const [value, setValue] = useState<number | null>(null);
  return (
    <div data-motes-host>
      <FeedbackRow
        question="enjoyed"
        label="Enjoyed it"
        ends={["Not really", "Loved it"]}
        value={value}
        onChange={setValue}
      />
    </div>
  );
}

function radios() {
  return within(
    screen.getByRole("radiogroup", { name: "Enjoyed it" }),
  ).getAllByRole("radio");
}

function spokenWord(container: HTMLElement) {
  return container.querySelector("[aria-live='polite']");
}

function shownWord(container: HTMLElement) {
  return container.querySelector("[data-word]");
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  prefersReducedMotion(false);
});

afterEach(() => {
  restoreMotion();
});

describe("FeedbackRow", () => {
  it("names each of the ten radios by its word", () => {
    // when
    render(<Harness />);

    // then
    expect(radios().map((radio) => radio.getAttribute("value"))).toEqual(
      Array.from({ length: 10 }, (_, index) => String(index + 1)),
    );
    radios().forEach((radio, index) => {
      expect(radio).toHaveAccessibleName(FEEDBACK_WORDS.enjoyed[index + 1]);
    });
  });

  it("selects the first answer on arrow right from empty and announces its word", async () => {
    // given
    const { container } = render(<Harness />);
    await user.tab();

    // when
    await user.keyboard("{ArrowRight}");

    // then
    expect(radios()[0]).toBeChecked();
    expect(radios()[0]).toHaveFocus();
    expect(spokenWord(container)).toHaveTextContent("Not for me");
  });

  it("steps the answer with the arrow keys and holds at the ends", async () => {
    // given
    render(<Harness />);
    await user.tab();

    // when
    await user.keyboard("{ArrowUp}{ArrowRight}{ArrowRight}{ArrowLeft}");

    // then
    expect(radios()[1]).toBeChecked();

    // when
    await user.keyboard("{ArrowDown}{ArrowDown}{ArrowDown}");

    // then
    expect(radios()[0]).toBeChecked();
  });

  it("selects the ninth answer on a click and reads it as Loved it", async () => {
    // given
    const { container } = render(<Harness />);

    // when
    await user.click(radios()[8]!);

    // then
    expect(radios()[8]).toBeChecked();
    expect(spokenWord(container)).toHaveTextContent("Loved it");
  });

  it("fades the end labels once answered", async () => {
    // given
    render(<Harness />);
    const ends = screen.getByText("Not really").parentElement;
    expect(ends).not.toHaveClass("opacity-0");

    // when
    await user.click(radios()[2]!);

    // then
    expect(ends).toHaveClass("opacity-0");
  });

  it("runs no cascade, wiggle, flip or mote under reduced motion", async () => {
    // given
    prefersReducedMotion(true);
    const animate = stubAnimate();
    const { container } = render(<Harness />);

    // when
    await user.click(radios()[9]!);
    await new Promise((resolve) => setTimeout(resolve, 300));

    // then
    expect(radios()[9]).toBeChecked();
    expect(mockRiseMotes).not.toHaveBeenCalled();
    expect(container.querySelector("[data-cascade]")).toBeNull();
    expect(animate).not.toHaveBeenCalled();
  });

  it("cascades the bars and releases motes on a high answer", async () => {
    // given
    stubAnimate();
    const { container } = render(<Harness />);

    // when
    await user.click(radios()[7]!);

    // then
    expect(container.querySelectorAll("[data-cascade]")).toHaveLength(8);
    await waitFor(() => {
      expect(mockRiseMotes).toHaveBeenCalledWith(
        container.firstElementChild,
        expect.any(Number),
        expect.any(Number),
        5,
      );
    });
  });

  it("releases no motes below eight", async () => {
    // given
    stubAnimate();
    render(<Harness />);

    // when
    await user.click(radios()[6]!);
    await new Promise((resolve) => setTimeout(resolve, 300));

    // then
    expect(mockRiseMotes).not.toHaveBeenCalled();
  });

  it("lights the icon from five and glows it from nine", async () => {
    // given
    const { container } = render(<Harness />);
    const icon = container.querySelector("svg")?.parentElement?.parentElement;
    expect(icon).toHaveClass("text-muted-foreground");

    // when
    await user.click(radios()[4]!);

    // then
    expect(icon).toHaveClass("text-data-1");
    expect(icon?.className).not.toContain("drop-shadow");

    // when
    await user.click(radios()[8]!);

    // then
    expect(icon?.className).toContain("drop-shadow");
  });

  it("previews a press and drag across the row, then commits on release", () => {
    // given
    const vibrate = vi.fn();
    Object.defineProperty(navigator, "vibrate", {
      configurable: true,
      value: vibrate,
    });
    const { container } = render(<Harness />);
    const row = screen.getByRole("presentation");
    vi.spyOn(row, "getBoundingClientRect").mockReturnValue({
      left: 0,
      width: 200,
    } as DOMRect);
    row.setPointerCapture = vi.fn();
    const tones = () =>
      [...container.querySelectorAll("[data-bar]")].map((bar) =>
        bar.getAttribute("data-tone"),
      );

    // when
    fireEvent.pointerDown(radios()[1]!, { clientX: 30, pointerId: 1 });
    fireEvent.pointerMove(row, { clientX: 170, pointerId: 1 });

    // then
    expect(row.setPointerCapture).toHaveBeenCalledWith(1);
    expect(vibrate).toHaveBeenCalledWith(6);
    expect(shownWord(container)).toHaveTextContent("Loved it");
    expect(spokenWord(container)).toBeEmptyDOMElement();
    expect(tones().filter((tone) => tone === "peek")).toHaveLength(9);
    expect(radios().some((radio) => (radio as HTMLInputElement).checked)).toBe(
      false,
    );

    // when
    fireEvent.pointerUp(row, { clientX: 170, pointerId: 1 });

    // then
    expect(radios()[8]).toBeChecked();
    expect(spokenWord(container)).toHaveTextContent("Loved it");
    expect(tones().filter((tone) => tone === "on")).toHaveLength(9);
    Reflect.deleteProperty(navigator, "vibrate");
  });

  it("drops a hover preview when the pointer leaves the row", () => {
    // given
    const { container } = render(<Harness />);
    const row = screen.getByRole("presentation");
    vi.spyOn(row, "getBoundingClientRect").mockReturnValue({
      left: 0,
      width: 200,
    } as DOMRect);
    fireEvent.pointerMove(row, { clientX: 90 });
    expect(shownWord(container)).toHaveTextContent("Fine");
    expect(spokenWord(container)).toBeEmptyDOMElement();

    // when
    fireEvent.pointerLeave(row);

    // then
    expect(shownWord(container)).toBeEmptyDOMElement();
    expect(radios().some((radio) => (radio as HTMLInputElement).checked)).toBe(
      false,
    );
  });

  it("keeps announcing the committed word while a hover previews another", async () => {
    // given
    const { container } = render(<Harness />);
    await user.click(radios()[8]!);
    const row = screen.getByRole("presentation");
    vi.spyOn(row, "getBoundingClientRect").mockReturnValue({
      left: 0,
      width: 200,
    } as DOMRect);

    // when
    fireEvent.pointerMove(row, { clientX: 90 });

    // then
    expect(shownWord(container)).toHaveTextContent("Fine");
    expect(spokenWord(container)).toHaveTextContent("Loved it");
  });
});
