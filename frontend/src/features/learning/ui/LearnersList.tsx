import { Link } from "react-router-dom";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { useLearners } from "@/features/learning/api/use-learning";
import type { Learner } from "@/features/learning/domain/learning";
import { declaredOn } from "@/lib/declared-on";
import { userHue } from "@/lib/user-hue";

interface Props {
  slug: string;
  tag: string;
}

function People({ learners }: Readonly<{ learners: Learner[] }>) {
  if (learners.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        Nobody wants to learn this area.
      </p>
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {learners.map((learner) => (
        <li
          key={learner.userId}
          className="border-border bg-surface flex items-center gap-3 rounded-xl border px-3 py-2"
        >
          <MemberAvatar
            name={learner.displayName}
            hue={userHue(learner.userId)}
            subject={learner.userId}
            src={learner.avatarUrl}
            className="size-8 text-xs"
          />
          <div className="min-w-0">
            <p className="text-foreground truncate text-sm font-medium">
              {learner.displayName}
            </p>
            <p className="text-muted-foreground text-xs">
              Since {declaredOn(learner.declaredAt)}
            </p>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function LearnersList({ slug, tag }: Readonly<Props>) {
  const learners = useLearners(slug, tag);

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h3 className="font-display text-foreground min-w-0 text-sm font-semibold wrap-anywhere">
          Want to learn <em>{tag}</em>
        </h3>
        <Link
          to={`/communities/${slug}/expertise?term=${encodeURIComponent(tag)}`}
          className="text-secondary ml-auto min-w-0 text-sm font-medium wrap-anywhere hover:underline"
        >
          See who knows {tag}
        </Link>
      </div>
      {learners.isLoading && (
        <p className="text-muted-foreground text-sm">Loading…</p>
      )}
      {learners.isError && (
        <p role="alert" className="text-danger text-sm">
          Could not load the learners.
        </p>
      )}
      {learners.data !== undefined && <People learners={learners.data} />}
    </section>
  );
}
