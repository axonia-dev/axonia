import { render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import {
  creditTargetsOf,
  resetCreditTargets,
  useCreditTarget,
} from "@/features/recognition/ui/credit-targets";

afterEach(resetCreditTargets);

const slot4 = {
  contributionType: "SLOT_PRESENTED",
  contributionId: 4,
} as const;

function Target({
  userId,
  contributionId = 4,
}: Readonly<{ userId: string; contributionId?: number }>) {
  const ref = useCreditTarget(
    { contributionType: "SLOT_PRESENTED", contributionId },
    userId,
  );
  return <span ref={ref} data-testid={`${userId}-${contributionId}`} />;
}

describe("credit targets", () => {
  it("registers an attached element and drops it on unmount", () => {
    const { container, unmount } = render(<Target userId="u1" />);

    expect(creditTargetsOf(slot4)).toEqual([
      { userId: "u1", element: container.firstElementChild },
    ]);
    unmount();
    expect(creditTargetsOf(slot4)).toEqual([]);
  });

  it("holds every element of a user and keeps other contributions apart", () => {
    render(
      <>
        <Target userId="u1" />
        <Target userId="u1" />
        <Target userId="u2" contributionId={5} />
      </>,
    );

    expect(creditTargetsOf(slot4).map((t) => t.userId)).toEqual(["u1", "u1"]);
    expect(
      creditTargetsOf({
        contributionType: "SLOT_PRESENTED",
        contributionId: 5,
      }),
    ).toHaveLength(1);
    expect(
      creditTargetsOf({
        contributionType: "ARTIFACT_CAPTURED",
        contributionId: 4,
      }),
    ).toEqual([]);
  });
});
