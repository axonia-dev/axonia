import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ActivityChart } from "@/features/health/ui/ActivityChart";

const SERIES = {
  talks: [0, 5, 0, 4, 0, 0, 4, 0, 0, 0, 4, 0],
  people: [2, 8, 3, 7, 1, 2, 7, 3, 2, 4, 6, 5],
  joins: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1],
};

function renderChart() {
  return render(
    <ActivityChart
      series={SERIES}
      windowId="90d"
      generatedAt="2026-11-09T12:00:00Z"
    />,
  );
}

function buckets() {
  return screen.getAllByRole("button", { name: /talks?,/ });
}

describe("ActivityChart", () => {
  it("is headed Activity, never week by week", () => {
    // when
    renderChart();

    // then
    expect(screen.getByRole("heading", { name: "Activity" })).toBeVisible();
    expect(screen.queryByText(/week by week/i)).not.toBeInTheDocument();
  });

  it("renders twelve focusable buckets", () => {
    // when
    renderChart();

    // then
    expect(buckets()).toHaveLength(12);
  });

  it("shows a bucket's date range and its three counts on focus", async () => {
    // given
    renderChart();

    // when
    await userEvent.tab();
    await userEvent.tab();
    await userEvent.tab();
    await userEvent.tab();

    // then
    expect(screen.getByText("2 Sep to 10 Sep")).toBeVisible();
    expect(screen.getByText("4 talks")).toBeVisible();
    expect(screen.getByText("7 people")).toBeVisible();
    expect(screen.getByText("0 joins")).toBeVisible();
  });

  it("shows a bucket's counts on hover too", () => {
    // given
    renderChart();

    // when
    fireEvent.mouseEnter(buckets()[10]!);

    // then
    expect(screen.getByText("1 join")).toBeVisible();
    expect(screen.getByText("6 people")).toBeVisible();
  });

  it("hides the counts again when the pointer leaves", () => {
    // given
    renderChart();
    fireEvent.mouseEnter(buckets()[10]!);

    // when
    fireEvent.mouseLeave(buckets()[10]!);

    // then
    expect(screen.queryByText("1 join")).not.toBeInTheDocument();
  });

  it("names each bucket with its range and counts for a screen reader", () => {
    // when
    renderChart();

    // then
    expect(buckets()[3]).toHaveAccessibleName(
      "2 Sep to 10 Sep: 4 talks, 7 people, 0 joins",
    );
  });

  it("summarises the window in one line", () => {
    // when
    renderChart();

    // then
    expect(
      screen.getByRole("group", {
        name: "Last 90 days: 17 talks, 2 joins, at most 8 people involved in one stretch",
      }),
    ).toBeInTheDocument();
  });

  it("labels the axis with dates", () => {
    // when
    renderChart();

    // then
    expect(screen.getByText("11 Aug")).toBeInTheDocument();
    expect(screen.getByText("9 Nov")).toBeInTheDocument();
  });

  it("colours its marks with data tones only, never a status colour", () => {
    // when
    const { container } = renderChart();

    // then
    const classes = [...container.querySelectorAll("svg *")]
      .map((mark) => mark.getAttribute("class") ?? "")
      .join(" ");
    expect(classes).toMatch(/data-1/);
    expect(classes).not.toMatch(/success|warning|danger/);
  });

  it("paints the bars above the people curve and its area", () => {
    // when
    const { container } = renderChart();

    // then
    const order = [...container.querySelectorAll("svg [data-mark]")].map(
      (mark) => mark.getAttribute("data-mark"),
    );
    expect(order.indexOf("area")).toBeLessThan(order.indexOf("people"));
    expect(order.indexOf("people")).toBeLessThan(order.indexOf("talks"));
    expect(order.lastIndexOf("talks")).toBeLessThan(order.indexOf("joins"));
  });

  it("draws joins on their own scale, never taller than a quarter of the plot", () => {
    // when
    const { container } = renderChart();

    // then
    const heights = [
      ...container.querySelectorAll('svg [data-mark="joins"]'),
    ].map((bar) => Number(bar.getAttribute("height")));
    expect(Math.max(...heights)).toBeCloseTo(150 * 0.25);
  });

  it("leaves headroom above the tallest mark", () => {
    // when
    const { container } = renderChart();

    // then
    const heights = [
      ...container.querySelectorAll('svg [data-mark="talks"]'),
    ].map((bar) => Number(bar.getAttribute("height")));
    expect(Math.max(...heights)).toBeCloseTo((5 / (8 * 1.15)) * 150);
  });

  it("sets the tip beside its bucket, clear of the bucket's own marks", () => {
    // given
    renderChart();

    // when
    fireEvent.mouseEnter(buckets()[1]!);

    // then
    const tip = document.querySelector<HTMLElement>("[data-tip]");
    expect(tip?.style.left).toBe(`${(2 / 12) * 100}%`);
  });
});
