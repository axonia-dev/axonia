import { useState } from "react";

import { currentUserId } from "@/auth/current-user";
import { AnimatedCount } from "@/components/ui/AnimatedCount";
import { Button } from "@/components/ui/Button";
import { HeartIcon } from "@/components/ui/HeartIcon";
import {
  useLove,
  useLoveSummary,
  useUnlove,
} from "@/features/recognition/api/use-recognition";
import type { ContributionKey } from "@/features/recognition/domain/recognition";
import { recognitionErrorMessage } from "@/features/recognition/recognition-errors";
import { AnimatedLoveButton } from "@/features/recognition/ui/AnimatedLoveButton";
import { cn } from "@/lib/cn";

/** `inline` = heart + count in a row (default); `stacked` = compact vertical
 * control for a fixed-width rail, with an icon-only first-love prompt. */
export type LoveLayout = "inline" | "stacked";

// The stacked rail button is a fixed 40×40 square so it lines up with the save
// button beside it — a quiet icon at rest, the same footprint once filled.
const STACKED_BTN = "size-10 flex-col gap-0.5 rounded-md p-0 text-[11px]";

function layoutClasses(layout: LoveLayout) {
  const stacked = layout === "stacked";
  return {
    stacked,
    orient: stacked ? "flex-col gap-0.5" : "gap-1.5",
    btn: stacked ? STACKED_BTN : "gap-1.5",
    wrap: stacked ? "items-center" : "items-start",
  };
}

const lovesLabel = (n: number) => (n === 1 ? "love" : "loves");

/** The control mutes itself only when the viewer is the sole known contributor;
 * a co-presenter may still love the shared slot. */
function isSoleContributor(contributorUserIds: string[] | undefined): boolean {
  return (
    contributorUserIds !== undefined &&
    contributorUserIds.length === 1 &&
    contributorUserIds[0] === currentUserId()
  );
}

/** Your own contribution is read-only — you can't love it. */
function OwnContribution({
  count,
  orient,
  stacked,
}: Readonly<{ count: number; orient: string; stacked: boolean }>) {
  const box = stacked ? "size-10 justify-center text-[11px]" : "text-sm";
  if (count === 0) {
    if (!stacked) {
      return null;
    }
    return (
      <span
        aria-hidden
        className={cn(
          "text-muted-foreground flex items-center opacity-50",
          box,
          orient,
        )}
      >
        <HeartIcon />
      </span>
    );
  }
  return (
    <span
      className={cn(
        "text-muted-foreground flex items-center tabular-nums",
        box,
        orient,
      )}
      aria-label={`${count} ${lovesLabel(count)}`}
    >
      <HeartIcon filled />
      <AnimatedCount value={count} />
    </span>
  );
}

const POP =
  "motion-safe:animate-[heart-pop_280ms_var(--ease-standard,ease-out)]";

type Burster = { key: number; bx: string; by: string; delay: number };

const BURST_ANGLES = [0, 45, 90, 135, 180, 225, 270, 315];

// ── Main export ───────────────────────────────────────────────────────────────

export function LoveAction({
  contribution,
  contributorUserIds,
  layout = "inline",
}: Readonly<{
  contribution: ContributionKey;
  /** Current contributors; the control mutes itself when the viewer is the sole one. */
  contributorUserIds?: string[];
  layout?: LoveLayout;
}>) {
  const summary = useLoveSummary(contribution);
  const love = useLove(contribution);
  const unlove = useUnlove(contribution);

  const count = summary.data?.count ?? 0;
  const loved = summary.data?.viewerHasLoved ?? false;

  const { stacked, orient, btn, wrap } = layoutClasses(layout);

  const [bursters, setBursters] = useState<Burster[]>([]);

  function spawnBurst() {
    setBursters(
      BURST_ANGLES.map((deg, i) => {
        const rad = (deg * Math.PI) / 180;
        // eslint-disable-next-line sonarjs/pseudo-random
        const dist = 48 + Math.random() * 20;
        return {
          key: i,
          bx: `${Math.cos(rad) * dist}px`,
          by: `${Math.sin(rad) * dist}px`,
          // eslint-disable-next-line sonarjs/pseudo-random
          delay: Math.floor(Math.random() * 80),
        };
      }),
    );
  }

  function removeBurster(key: number) {
    setBursters((prev) => prev.filter((b) => b.key !== key));
  }

  if (isSoleContributor(contributorUserIds)) {
    return <OwnContribution count={count} orient={orient} stacked={stacked} />;
  }

  function control() {
    if (loved) {
      return (
        <Button
          variant="ghost"
          size="sm"
          aria-label={`Unlove, ${count} ${lovesLabel(count)}`}
          className={cn("text-primary", btn)}
          isDisabled={unlove.isPending}
          onPress={() => unlove.mutate()}
        >
          <HeartIcon key="loved" filled className={POP} />
          <AnimatedCount value={count} />
        </Button>
      );
    }
    if (count === 0) {
      return (
        <AnimatedLoveButton
          btn={btn}
          {...(stacked ? { ariaLabel: "Love it!" } : {})}
          isPending={love.isPending}
          onPress={() => {
            spawnBurst();
            love.mutate();
          }}
        >
          <HeartIcon />
          {stacked ? null : "Love it!"}
        </AnimatedLoveButton>
      );
    }
    return (
      <AnimatedLoveButton
        btn={btn}
        ariaLabel={`Love, ${count} ${lovesLabel(count)}`}
        isPending={love.isPending}
        onPress={() => {
          spawnBurst();
          love.mutate();
        }}
      >
        <HeartIcon key="unloved" className={POP} />
        <AnimatedCount value={count} />
      </AnimatedLoveButton>
    );
  }

  return (
    <div className={cn("flex flex-col gap-2", wrap)}>
      <div className="relative">
        {control()}
        {bursters.map((b) => (
          <span
            key={b.key}
            aria-hidden
            className="text-primary pointer-events-none absolute"
            style={
              {
                left: "50%",
                top: "50%",
                marginLeft: "-6px",
                marginTop: "-6px",
                fontSize: "13px",
                "--bx": b.bx,
                "--by": b.by,
                animation: `burst-fly 600ms ease-out ${b.delay}ms forwards`,
              } as React.CSSProperties
            }
            onAnimationEnd={() => removeBurster(b.key)}
          >
            ♥
          </span>
        ))}
      </div>
      {love.isError && (
        <p role="alert" className="text-danger text-sm">
          {recognitionErrorMessage(love.error)}
        </p>
      )}
    </div>
  );
}
