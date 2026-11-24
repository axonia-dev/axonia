import { useState } from "react";

import { Pagination } from "@/components/patterns/Pagination";
import { Dialog } from "@/components/ui/Dialog";
import { useCommunity } from "@/features/community/api/use-communities";
import { useMentors } from "@/features/mentoring/api/use-mentor-listing";
import {
  MENTOR_STATUS_LABEL,
  type MentorFace,
} from "@/features/mentoring/domain/mentoring";
import { PartnerFace } from "@/features/mentoring/ui/PartnerFace";

/** A page that emptied while the dialog was open steps back to the last page that still has rows. */
function useMentorPages(slug: string) {
  const [page, setPage] = useState(0);
  const mentors = useMentors(slug, page);
  const data = mentors.data;
  if (data !== undefined && data.content.length === 0 && page > 0) {
    setPage(Math.min(page - 1, Math.max(0, data.totalPages - 1)));
  }
  return { mentors, page, setPage };
}

function MentorRow({ mentor }: Readonly<{ mentor: MentorFace }>) {
  return (
    <li className="flex items-center gap-3">
      <PartnerFace
        userId={mentor.userId}
        name={mentor.displayName}
        avatarUrl={mentor.avatarUrl}
        className="size-8 text-xs"
      />
      <span className="text-foreground min-w-0 flex-1 text-sm font-semibold wrap-anywhere">
        {mentor.displayName}
      </span>
      <span className="text-muted-foreground text-xs">
        {MENTOR_STATUS_LABEL[mentor.status]}
      </span>
    </li>
  );
}

/** Every listed mentor of the community, a page at a time. */
export function MentorsDialog({
  slug,
  isOpen,
  onOpenChange,
}: Readonly<{
  slug: string;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}>) {
  const { mentors, page, setPage } = useMentorPages(slug);
  const name = useCommunity(slug).data?.name;
  const totalPages = mentors.data?.totalPages ?? 0;
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title={name === undefined ? "Mentors" : `Mentors in ${name}`}
    >
      <div className="flex flex-col gap-3">
        {mentors.isError && (
          <p role="alert" className="text-danger text-sm">
            Could not load mentors.
          </p>
        )}
        {mentors.data?.totalElements === 0 && (
          <p className="text-muted-foreground text-sm">Nobody is listed.</p>
        )}
        <ul className="flex flex-col gap-2">
          {mentors.data?.content.map((mentor) => (
            <MentorRow key={mentor.userId} mentor={mentor} />
          ))}
        </ul>
        {totalPages > 1 && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        )}
      </div>
    </Dialog>
  );
}
