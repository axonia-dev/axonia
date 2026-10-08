import { Link } from "react-router-dom";

import { HeartIcon } from "@/components/ui/HeartIcon";
import { SectionTypeIcon } from "@/features/events/ui/section-type-visual";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { CONTRIBUTION_META } from "@/features/recognition/ui/contribution-meta";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";

// The love count carries the reaction visually (heart + number, as everywhere
// else); the sentence lives on as the accessible label for screen readers.
function peopleLabel(n: number): string {
  return `Appreciated by ${n} ${n === 1 ? "person" : "people"}`;
}

// The real type badge — section type for talks, artifact type for artifacts —
// falling back to the generic per-kind chip when the source type no longer resolves.
function TypeBadge({
  contribution,
}: Readonly<{ contribution: RecognizedContribution }>) {
  const { contributionType, typeId, typeLabel, typeIcon, typeAccentColor } =
    contribution;

  if (typeId !== null && typeLabel !== null) {
    const Badge =
      contributionType === "SLOT_PRESENTED" ? SectionTypeIcon : TypeIcon;
    return (
      <Badge
        typeId={typeId}
        label={typeLabel}
        icon={typeIcon}
        accentColor={typeAccentColor}
        size={16}
      />
    );
  }

  const meta = CONTRIBUTION_META[contributionType];
  const Icon = meta.icon;
  return (
    <span
      aria-hidden
      className={`flex size-8 items-center justify-center rounded-lg text-[color-mix(in_srgb,var(--foreground)_80%,transparent)] ${meta.chipClassName}`}
    >
      <Icon size={15} />
    </span>
  );
}

// Where the title links: an artifact filters the library to itself; a talk opens
// the session it happened in (or the schedule if that session is no longer known).
function sourceHref(
  contribution: RecognizedContribution,
  slug: string,
): string {
  if (contribution.contributionType === "ARTIFACT_CAPTURED") {
    return `/communities/${slug}/library?q=${encodeURIComponent(contribution.title)}`;
  }
  if (contribution.sessionId !== null) {
    return `/communities/${slug}/sessions/${contribution.sessionId}`;
  }
  return `/communities/${slug}/schedule`;
}

export function ContributionCard({
  contribution,
}: Readonly<{ contribution: RecognizedContribution }>) {
  const meta = CONTRIBUTION_META[contribution.contributionType];
  const date = relativeTime(contribution.occurredAt, useNow());
  const slug = contribution.communitySlug;
  const linkable = slug !== null && slug !== "";
  const kindLabel = contribution.typeLabel ?? meta.noun;

  return (
    <li className="border-border bg-surface flex items-start gap-3 rounded-xl border p-4 shadow-[0_1px_2px_rgba(94,23,64,0.05)]">
      <span aria-hidden className="mt-0.5 shrink-0">
        <TypeBadge contribution={contribution} />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-3">
          {linkable ? (
            <Link
              to={sourceHref(contribution, slug)}
              className="text-foreground hover:text-primary min-w-0 truncate text-sm font-medium underline-offset-2 hover:underline"
            >
              {contribution.title}
            </Link>
          ) : (
            <span className="text-foreground min-w-0 truncate text-sm font-medium">
              {contribution.title}
            </span>
          )}
          <span className="text-muted-foreground shrink-0 text-xs">{date}</span>
        </div>
        <p className="text-muted-foreground mt-0.5 text-xs first-letter:uppercase">
          {kindLabel}
        </p>
        {contribution.loveCount > 0 && (
          <p
            className="text-primary mt-2 inline-flex items-center gap-1.5 text-sm tabular-nums"
            aria-label={peopleLabel(contribution.loveCount)}
          >
            <HeartIcon filled />
            {contribution.loveCount}
          </p>
        )}
      </div>
    </li>
  );
}
