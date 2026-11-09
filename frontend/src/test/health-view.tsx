import type { RenderResult } from "@testing-library/react";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, vi } from "vitest";

import { useHealthSnapshot } from "@/features/health/api/use-health";
import type { Chapter } from "@/features/health/domain/finding-routing";
import { CHAPTER_OF } from "@/features/health/domain/finding-routing";
import type {
  Finding,
  FindingCode,
  FindingKind,
  HealthSnapshot,
} from "@/features/health/domain/health";
import { findingText } from "@/features/health/domain/health";
import { HealthView } from "@/features/health/ui/HealthView";

/** The kind only picks an icon, so one per panel keeps all three icons on the page. */
const KIND_OF: Record<Chapter, FindingKind> = {
  carried: "RISK",
  alive: "GAP",
  payoff: "STRENGTH",
};

/**
 * Every code the backend can emit, derived from the routing table rather than typed out, so a
 * new code cannot leave this list short while the exactly-once guard still reports green.
 */
export const EVERY_FINDING: Finding[] = Object.entries(CHAPTER_OF).map(
  ([code, chapter]) => ({ kind: KIND_OF[chapter], code: code as FindingCode }),
);

/** Each importer declares its own `vi.mock` of the query hook; this only feeds it. */
export function mockLoaded(data: HealthSnapshot) {
  vi.mocked(useHealthSnapshot).mockReturnValue({
    data,
    isLoading: false,
    isError: false,
    error: null,
  } as never);
}

export function mockFailure(error: unknown) {
  vi.mocked(useHealthSnapshot).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: true,
    error,
  } as never);
}

export function mockLoading() {
  vi.mocked(useHealthSnapshot).mockReturnValue({
    data: undefined,
    isLoading: true,
    isError: false,
    error: null,
  } as never);
}

export function renderHealthView(): RenderResult {
  return render(
    <MemoryRouter>
      <HealthView slug="ring" />
    </MemoryRouter>,
  );
}

/** The glance row repeats the panels' status words, so a panel is read on its own. */
export function renderPanel(id: Chapter) {
  const { container } = renderHealthView();
  return within(container.querySelector(`#${id}`) as HTMLElement);
}

/** A fact is a number over its caption, read as one line the way a screen reader reads it. */
export function factText(caption: string): string {
  const text = screen.getByText(caption).parentElement?.textContent ?? "";
  return text.replace(/\s+/g, " ").trim();
}

/**
 * The guard the whole page rests on. A `contains` assertion cannot see an over-render, and
 * counting two hand-picked sentences cannot see the eighth one, so this counts every finding the
 * snapshot carries.
 */
export function expectEveryFindingStatedOnce(snapshot: HealthSnapshot) {
  for (const finding of snapshot.diagnosis.findings) {
    expect(
      screen.getAllByText(findingText(finding.code, snapshot)),
      `finding ${finding.code}`,
    ).toHaveLength(1);
  }
}
