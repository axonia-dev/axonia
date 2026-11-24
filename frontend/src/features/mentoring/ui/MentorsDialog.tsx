import { useState } from "react";

import { Pagination } from "@/components/patterns/Pagination";
import { Dialog } from "@/components/ui/Dialog";
import { useCommunity } from "@/features/community/api/use-communities";
import { useMentors } from "@/features/mentoring/api/use-mentor-listing";
import { MENTOR_STATUS_LABEL } from "@/features/mentoring/domain/mentoring";
import { PartnerFace } from "@/features/mentoring/ui/PartnerFace";

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
  const [page, setPage] = useState(0);
  const mentors = useMentors(slug, page);
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
        <ul className="flex flex-col gap-2">
          {mentors.data?.content.map((mentor) => (
            <li key={mentor.userId} className="flex items-center gap-3">
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
