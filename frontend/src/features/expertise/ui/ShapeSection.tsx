import { Fragment, useId, useMemo } from "react";

import { useExpertiseMap } from "@/features/expertise/api/use-expertise-map";
import type { SelfView, TopicMap } from "@/features/expertise/domain/expertise";
import {
  ABSENT,
  practitionerShape,
  type ShapeGroup,
} from "@/features/expertise/domain/practitioner-shape";
import {
  communityInScope,
  type ShapeCommunity,
} from "@/features/expertise/domain/shape-community";
import {
  type DrawnGroup,
  groupNames,
  groupsFor,
} from "@/features/expertise/ui/named-area-grouping";
import { PractitionerShape } from "@/features/expertise/ui/PractitionerShape";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { MIN_VOCABULARY } from "@/features/expertise/ui/topic-neighbourhoods";
import { useLearningPage } from "@/features/learning/api/use-learning";

interface Props {
  view: SelfView;
  page: ShapeCommunity | null;
  lensId: number;
}

function shapeGroupOf(group: DrawnGroup): ShapeGroup {
  const names = groupNames(group);
  return {
    tags: group.tags,
    kind: group.kind,
    slot: group.slot,
    label: names.short,
    spoken: names.list,
    anchor: group.anchor,
  };
}

function shapeOf(
  map: TopicMap,
  view: SelfView,
  communityId: number,
  wantToLearn: string[],
) {
  return practitionerShape({
    groups: groupsFor(map.topics, map.links, map.namedAreas).map(shapeGroupOf),
    drawnTags: map.topics.map((t) => t.canonicalTag),
    belowVocabularyFloor: map.topics.length < MIN_VOCABULARY,
    optedOut: view.optedOut,
    communityId,
    areas: view.areas,
    declarations: view.declarations,
    wantToLearn,
  });
}

function Legend() {
  return (
    <div className="text-muted-foreground flex justify-center gap-4 text-xs">
      <span className="flex items-center gap-1.5">
        <svg aria-hidden viewBox="-5 -5 10 10" className="size-2.5">
          <circle
            data-legend-mark="declared"
            r={4.25}
            fill="none"
            stroke={TIER_META.OPEN_TO_QUESTIONS.colour}
            strokeWidth={1.4}
            strokeDasharray="2 1.6"
          />
        </svg>
        Open to questions
      </span>
      <span className="flex items-center gap-1.5">
        <svg aria-hidden viewBox="-5 -5 10 10" className="size-2.5">
          <path
            d="M-4 3 L0 -4 L4 3"
            fill="none"
            stroke="var(--foreground)"
            strokeWidth={1.5}
          />
        </svg>
        Want to learn
      </span>
    </div>
  );
}

function CommunityShape({
  view,
  community,
}: Readonly<{ view: SelfView; community: ShapeCommunity }>) {
  const headingId = useId();
  const map = useExpertiseMap(community.slug);
  const learning = useLearningPage(community.slug);
  const wantToLearn = useMemo(
    () => (learning.data?.mine ?? []).map((a) => a.tag),
    [learning.data],
  );
  const shape = useMemo(
    () =>
      map.data === undefined
        ? ABSENT
        : shapeOf(map.data, view, community.id, wantToLearn),
    [map.data, view, community.id, wantToLearn],
  );
  if (shape.kind === "absent") return null;
  return (
    <section
      aria-labelledby={headingId}
      className="bg-surface border-border flex flex-col gap-2 rounded-xl border p-4"
    >
      <h2
        id={headingId}
        className="font-display text-foreground text-[15px] font-semibold"
      >
        Shape in {community.name}
      </h2>
      {shape.kind === "ungrouped" ? (
        <p className="text-muted-foreground text-sm">
          The map does not group this community’s areas yet
        </p>
      ) : (
        <>
          <PractitionerShape axes={shape.axes} />
          <Legend />
          {shape.unplacedWantToLearn.length > 0 && (
            <p className="text-muted-foreground text-center text-xs">
              Want to learn, not on the map:{" "}
              {shape.unplacedWantToLearn.map((tag, index, all) => (
                <Fragment key={tag}>
                  {index > 0 && " "}
                  {/* The comma rides inside, so a tag wrapped to the full width never leaves it alone on a line. */}
                  <span className="inline-block max-w-full wrap-anywhere">
                    {index < all.length - 1 ? `${tag},` : tag}
                  </span>
                </Fragment>
              ))}
            </p>
          )}
        </>
      )}
    </section>
  );
}

/** The member's own shape in the community the page or its lens is about; nobody else's is ever drawn. */
export function ShapeSection({ view, page, lensId }: Readonly<Props>) {
  const community = communityInScope(view, { lensId, page });
  if (community === null) return null;
  return (
    <CommunityShape key={community.slug} view={view} community={community} />
  );
}
