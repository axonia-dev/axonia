import { Link } from "react-router-dom";

import { useLearningPage } from "@/features/learning/api/use-learning";
import { normalizeTag } from "@/lib/normalize-tag";

interface Props {
  slug: string;
  /** The searched term, as typed. */
  term: string;
}

/**
 * From who knows an area to who wants to learn it. The wanted list names canonical areas only, so
 * a variant spelling finds no bridge here; the Learning page resolves variants itself.
 */
export function LearnersBridge({ slug, term }: Readonly<Props>) {
  const page = useLearningPage(slug);
  const tag = normalizeTag(term);
  const wanted = page.data?.wanted.find((area) => area.tag === tag);
  if (wanted === undefined) return null;
  return (
    <Link
      to={`/communities/${slug}/learning?area=${encodeURIComponent(tag)}`}
      className="text-secondary mt-3 inline-block max-w-full text-sm font-medium wrap-anywhere hover:underline"
    >
      {`${wanted.learners} ${wanted.learners === 1 ? "wants" : "want"} to learn ${tag}`}
    </Link>
  );
}
