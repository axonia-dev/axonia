import { useState } from "react";

import { PersonStack } from "@/components/patterns/PersonStack";
import { Button } from "@/components/ui/Button";
import type { User } from "@/features/identity/domain/user";
import {
  MENTOR_STATUS_LABEL,
  type MentorFace,
  type MentoringPage,
} from "@/features/mentoring/domain/mentoring";
import { MentorsDialog } from "@/features/mentoring/ui/MentorsDialog";

/** The listing carries a name and a photo only, so the hover card has no organisation to show. */
function userOf(face: MentorFace): User {
  return {
    id: face.userId,
    displayName: face.displayName,
    avatarUrl: face.avatarUrl,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  };
}

/** The community's other listed mentors, at a glance; nothing renders while nobody is listed. */
export function OtherMentors({
  slug,
  other,
}: Readonly<{ slug: string; other: MentoringPage["otherMentors"] }>) {
  const [seeAll, setSeeAll] = useState(false);
  if (other.total === 0) {
    return null;
  }
  return (
    <div className="border-border-strong mt-4 border-t border-dashed pt-4">
      <h4 className="text-muted-foreground mb-2 text-[11px] font-bold tracking-wider uppercase">
        Other mentors here
      </h4>
      <div className="flex items-center gap-3">
        <PersonStack
          label="Other mentors here"
          total={other.total}
          people={other.faces.map((face) => ({
            id: face.userId,
            name: face.displayName,
            user: userOf(face),
            detail: MENTOR_STATUS_LABEL[face.status],
          }))}
        />
        <Button variant="outline" size="sm" onPress={() => setSeeAll(true)}>
          See all
        </Button>
      </div>
      {seeAll && <MentorsDialog slug={slug} isOpen onOpenChange={setSeeAll} />}
    </div>
  );
}
