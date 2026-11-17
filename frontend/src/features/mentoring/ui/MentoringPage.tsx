import { useState } from "react";
import { useParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { useMe } from "@/features/identity/api/use-me";
import { useMentoringPage } from "@/features/mentoring/api/use-mentoring";
import type { MentoringPage as MentoringPageData } from "@/features/mentoring/domain/mentoring";
import {
  MENTORING_IS_OFF,
  OWN_SIGN_IN_REFUSAL,
} from "@/features/mentoring/mentoring-errors";
import {
  AvailabilityDoors,
  type DoorKind,
} from "@/features/mentoring/ui/AvailabilityDoors";
import { DirectoryList } from "@/features/mentoring/ui/DirectoryList";
import { GuidedStart, PEOPLE_ID } from "@/features/mentoring/ui/GuidedStart";
import { ApiError } from "@/lib/api-client";

const NOT_A_MEMBER = "Mentoring is open to members of this community.";

function members(count: number, one: string, many: string): string {
  return count === 1 ? `1 member ${one}.` : `${count} members ${many}.`;
}

function Directory({
  slug,
  page,
}: Readonly<{ slug: string; page: MentoringPageData }>) {
  const selfId = useMe().data?.id;
  const { mine } = page;
  const shared = { slug, selfId, archived: page.archived };
  const others = (people: MentoringPageData["mentors"]) =>
    people.filter((person) => person.userId !== selfId).length;
  const [landOn, setLandOn] = useState<DoorKind | undefined>(undefined);
  const guided =
    page.guidedStart && !mine.takingMentees && !mine.lookingForMentor;
  return (
    <>
      {guided ? (
        <GuidedStart slug={slug} page={page} onSwitchedOn={setLandOn} />
      ) : (
        <AvailabilityDoors slug={slug} page={page} focusDoor={landOn} />
      )}
      <div id={PEOPLE_ID} className="mt-2 flex scroll-mt-20 flex-col gap-6">
        <DirectoryList
          {...shared}
          title="Mentors"
          empty={
            mine.takingMentees
              ? "No other mentor is taking mentees right now."
              : "No mentor is taking mentees right now."
          }
          people={page.mentors}
          sharedAreas={page.wantsToGrowIn}
          closed={
            mine.lookingForMentor
              ? null
              : {
                  reason: "Mentors show here once Looking for a mentor is on.",
                  count: members(
                    others(page.mentors),
                    "takes mentees",
                    "take mentees",
                  ),
                }
          }
          proposerRole="MENTEE"
        />
        <DirectoryList
          {...shared}
          title="Mentees"
          empty={
            mine.lookingForMentor
              ? "Nobody else is looking for a mentor right now."
              : "Nobody is looking for a mentor right now."
          }
          people={page.mentees}
          sharedAreas={page.canMentorOn}
          closed={
            mine.takingMentees
              ? null
              : {
                  reason: "Mentees show here once Taking mentees is on.",
                  count: members(
                    others(page.mentees),
                    "looks for a mentor",
                    "look for a mentor",
                  ),
                }
          }
          proposerRole="MENTOR"
        />
      </div>
    </>
  );
}

function refusalLine(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined;
  if (error.status === 403) return NOT_A_MEMBER;
  return error.code === "MENTORING_OFF" ? MENTORING_IS_OFF : undefined;
}

function Body({ slug }: Readonly<{ slug: string }>) {
  const page = useMentoringPage(slug);
  if (page.isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (page.data === undefined) {
    const refusal = refusalLine(page.error);
    return refusal === undefined ? (
      <p role="alert" className="text-danger text-sm">
        Could not load mentoring.
      </p>
    ) : (
      <p className="text-muted-foreground text-sm">{refusal}</p>
    );
  }
  return <Directory slug={slug} page={page.data} />;
}

/** The member's two doors, or a first visit's guided start, then who is taking mentees and who is looking for a mentor. */
export function MentoringPage() {
  const { slug = "" } = useParams();
  const delegated = useIsDelegated();
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-foreground font-display text-xl font-semibold">
        Mentoring
      </h1>
      {delegated ? (
        <p className="text-muted-foreground text-sm">{OWN_SIGN_IN_REFUSAL}</p>
      ) : (
        <Body slug={slug} />
      )}
    </section>
  );
}
