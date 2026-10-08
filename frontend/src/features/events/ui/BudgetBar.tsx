import { useState } from "react";

import { cn } from "@/lib/cn";

interface Props {
  /** Minutes already used - sum of section time boxes (session bar) or slot durations. */
  usedMinutes: number;
  /** The session/series time budget to fit within; null when none is set. */
  budgetMinutes: number | null;
  /** Given when the budget on this bar is the reader's to change, which turns the number into a
   *  field. Absent wherever the budget belongs to something else: a series' standing length, a
   *  section's own time box. */
  onBudgetChange?: (minutes: number | null) => void;
  className?: string;
}

/** Minutes as a budget: a blank field is no budget rather than a budget of nothing, and anything
 *  that is not a positive count of whole minutes is not an answer either. */
function minutesFrom(text: string): number | null {
  const minutes = Number(text.trim());
  if (text.trim() === "" || !Number.isFinite(minutes) || minutes <= 0) {
    return null;
  }
  return Math.round(minutes);
}

interface BudgetNumberProps {
  budgetMinutes: number | null;
  onChange: (minutes: number | null) => void;
}

/**
 * The budget number as the field it is when an organizer owns it.
 *
 * Its text is its own and seeded once. Read back from the parsed value instead, the half-typed "1"
 * of an intended "15" would be rewritten under the caret, and a field cleared to retype would
 * refill itself with the value being replaced.
 */
function BudgetNumber({
  budgetMinutes,
  onChange,
}: Readonly<BudgetNumberProps>) {
  const [text, setText] = useState(
    budgetMinutes === null ? "" : String(budgetMinutes),
  );

  return (
    <input
      type="number"
      min={1}
      aria-label="Session length in minutes"
      value={text}
      onChange={(event) => {
        setText(event.target.value);
        onChange(minutesFrom(event.target.value));
      }}
      className="border-border-strong bg-surface-sunken text-foreground focus:border-primary mx-0.5 w-9 [appearance:textfield] rounded border px-1 text-center leading-none tabular-nums outline-none [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
    />
  );
}

interface StatusLabelProps {
  usedMinutes: number;
  budgetMinutes: number | null;
  onBudgetChange?: (minutes: number | null) => void;
}

function BudgetStatusLabel({
  usedMinutes,
  budgetMinutes,
  onBudgetChange,
}: Readonly<StatusLabelProps>) {
  const over = budgetMinutes !== null && usedMinutes > budgetMinutes;

  return (
    <p className="text-muted-foreground text-xs">
      {usedMinutes} /{" "}
      {onBudgetChange === undefined ? (
        budgetMinutes
      ) : (
        <BudgetNumber budgetMinutes={budgetMinutes} onChange={onBudgetChange} />
      )}{" "}
      min
      {over && (
        <span className="text-warning">
          {" "}
          · {usedMinutes - budgetMinutes} min over
        </span>
      )}
    </p>
  );
}

/**
 * The meter itself. Over budget, the track is split into the fill (the budget's own share) and an
 * overrun tail drawn past it, rather than clamped to one solid rule: the track spans whichever is
 * larger, so the budget keeps a real position inside an overrun rather than being the whole of it.
 * Clamping the fill at 100% made every overrun draw the same rule, which is the meter saying least
 * at the moment it has most to say.
 */
function BudgetTrack({
  usedMinutes,
  budgetMinutes,
}: Readonly<{ usedMinutes: number; budgetMinutes: number }>) {
  const over = usedMinutes > budgetMinutes;
  const span = Math.max(usedMinutes, budgetMinutes);
  const fillPct = (Math.min(usedMinutes, budgetMinutes) / span) * 100;
  const overrunPct = over ? ((usedMinutes - budgetMinutes) / span) * 100 : 0;

  return (
    <div
      role="progressbar"
      aria-valuenow={usedMinutes}
      aria-valuemin={0}
      aria-valuemax={budgetMinutes}
      aria-valuetext={`${usedMinutes} of ${budgetMinutes} minutes used`}
      className="bg-surface-sunken flex h-1 w-full overflow-hidden rounded-full"
    >
      <div
        data-part="fill"
        className={cn(
          "h-full motion-safe:transition-[width] motion-reduce:transition-none",
          over ? "rounded-l-full" : "rounded-full",
          "bg-[linear-gradient(90deg,var(--primary-strong),var(--primary)_62%,var(--bar-tip))]",
        )}
        style={{ width: `${fillPct}%` }}
      />
      {over && (
        <div
          data-part="overrun"
          className="h-full rounded-r-full bg-[linear-gradient(90deg,var(--warning),var(--bar-tip-warning))] motion-safe:transition-[width] motion-reduce:transition-none"
          style={{ width: `${overrunPct}%` }}
        />
      )}
    </div>
  );
}

/**
 * The session time budget guardrail (design 0009): shows how the section time boxes add up against
 * the session budget. Going over is a *soft* signal, not a block - fragmentation guardrail, not a
 * gatekeeper.
 *
 * With no budget set the running total is surfaced on its own so the number is still visible -
 * unless the budget is this reader's to set, in which case the empty field is the point: a session
 * that never had a length is exactly the one wanting one, and there is no number on screen to turn
 * into a field.
 */
export function BudgetBar({
  usedMinutes,
  budgetMinutes,
  onBudgetChange,
  className,
}: Readonly<Props>) {
  const hasBudget = budgetMinutes !== null && budgetMinutes > 0;

  if (!hasBudget && onBudgetChange === undefined) {
    return (
      <p className={cn("text-muted-foreground text-xs", className)}>
        Total {usedMinutes} min
      </p>
    );
  }

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      {hasBudget && (
        <BudgetTrack usedMinutes={usedMinutes} budgetMinutes={budgetMinutes} />
      )}
      <BudgetStatusLabel
        usedMinutes={usedMinutes}
        budgetMinutes={budgetMinutes}
        {...(onBudgetChange === undefined ? {} : { onBudgetChange })}
      />
    </div>
  );
}
