import { Link } from "react-router-dom";

import { buttonClass } from "@/components/ui/Button";
import { plural } from "@/lib/plural";

/**
 * A dormant community gets exactly one next move, never a list of options: two calls to action
 * read as no call to action at all.
 */
export function QuietCommunityCard({
  slug,
  joins,
}: Readonly<{ slug: string; joins: number }>) {
  return (
    <article className="border-border-strong bg-surface rounded-md border border-dashed p-5">
      <h3 className="text-foreground text-sm font-semibold">
        This community is quiet
      </h3>
      <p className="text-muted-foreground mt-2 text-sm">
        {plural(joins, "member")} joined and nothing has been scheduled since.
      </p>
      <p className="text-muted-foreground mt-1 text-sm">
        A dormant community is not a bad number, it is a moment where one
        session restarts the rhythm.
      </p>
      <Link
        to={`/communities/${slug}/schedule`}
        className={buttonClass("primary", "md", "mt-4")}
      >
        Schedule a session
      </Link>
    </article>
  );
}
