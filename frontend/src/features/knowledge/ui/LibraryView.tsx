import { Bookmark } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect } from "react";

import { FilterToggleChip } from "@/components/patterns/FilterToggleChip";
import { Pagination } from "@/components/patterns/Pagination";
import { SearchField } from "@/components/ui/SearchField";
import { Select } from "@/components/ui/Select";
import { SavedHelpfulPrompt } from "@/features/bookmarks/ui/SavedHelpfulPrompt";
import { useLibraryPage } from "@/features/knowledge/api/use-artifacts";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import type { ArtifactSearchParams } from "@/features/knowledge/domain/knowledge-gateway";
import { ArtifactList } from "@/features/knowledge/ui/ArtifactList";
import {
  DEFAULT_SORT,
  type LibraryFilters,
  type Sort,
  useLibraryFilters,
  useSearchText,
} from "@/features/knowledge/ui/library-filters";
import { LibraryHeader } from "@/features/knowledge/ui/LibraryHeader";
import { SessionContextStrip } from "@/features/knowledge/ui/SessionContextStrip";
import {
  ANY_SESSION,
  NO_SESSION,
  SessionFilter,
} from "@/features/knowledge/ui/SessionFilter";
import { ALL_TYPES, TypeChips } from "@/features/knowledge/ui/TypeChips";

const PAGE_SIZE = 20;

const SORT_OPTIONS: ReadonlyArray<{ id: Sort; label: string }> = [
  { id: "newest", label: "Newest" },
  { id: "mostAppreciated", label: "Most loved" },
];

const SAVED_LIST = {
  empty: {
    title: "Nothing saved here yet",
    description: "Bookmarked artifacts from this library appear here.",
  },
  renderArtifactFootnote: (artifact: Artifact) => (
    <SavedHelpfulPrompt artifactId={artifact.id} />
  ),
};

function buildSearchParams(filters: LibraryFilters): ArtifactSearchParams {
  const { q, typeFilter, tag, session, sort, saved, page } = filters;
  return {
    page,
    size: PAGE_SIZE,
    ...(q.trim() !== "" ? { q: q.trim() } : {}),
    ...(typeFilter !== ALL_TYPES ? { type: typeFilter } : {}),
    ...(tag !== null ? { tag } : {}),
    ...(session !== ANY_SESSION ? { session } : {}),
    ...(sort !== DEFAULT_SORT ? { sort } : {}),
    ...(saved ? { saved: true as const } : {}),
  };
}

function narrowsResults(params: ArtifactSearchParams): boolean {
  return [params.q, params.type, params.tag, params.session].some(
    (filter) => filter !== undefined,
  );
}

function ActiveTag({
  tag,
  onClear,
}: Readonly<{ tag: string; onClear: () => void }>) {
  return (
    <button
      type="button"
      onClick={onClear}
      className="border-primary text-primary-ink inline-flex items-center gap-1 self-start rounded-full border px-2 py-0.5 text-xs"
    >
      #{tag} ✕
    </button>
  );
}

interface ArtifactRenderers {
  renderArtifactActions?: (artifact: Artifact) => ReactNode;
  renderArtifactReactions?: (artifact: Artifact) => ReactNode;
  renderArtifactManageActions?: (artifact: Artifact) => ReactNode;
}

/** `exactOptionalPropertyTypes` refuses an explicit `undefined`, so an absent renderer has to
 *  be dropped rather than forwarded. Module level because three conditional spreads at the
 *  call site push `LibraryView` past the complexity ceiling on their own. */
function definedRenderers(renderers: ArtifactRenderers): ArtifactRenderers {
  return Object.fromEntries(
    Object.entries(renderers).filter(([, render]) => render !== undefined),
  );
}

/** An unsave can empty the last page; fall back to the last page that still has rows. A placeholder
 *  is the previous answer, so its page count says nothing about the page being asked for. */
function useKeepPageInRange(
  page: number,
  answer: {
    data: { totalPages: number } | undefined;
    isPlaceholderData: boolean;
  },
  setPage: (page: number) => void,
) {
  const { data, isPlaceholderData } = answer;
  const lastPage =
    data && !isPlaceholderData ? Math.max(0, data.totalPages - 1) : undefined;
  useEffect(() => {
    if (lastPage !== undefined && page > lastPage) {
      setPage(lastPage);
    }
  }, [page, lastPage, setPage]);
}

export function LibraryView({
  slug,
  ...renderers
}: Readonly<{ slug: string } & ArtifactRenderers>) {
  const library = useLibraryFilters();
  const { filters, focusedId } = library;
  const { typeFilter, tag, session, sort, saved, page } = filters;
  const [q, setQ] = useSearchText(filters.q, library.setQuery);

  const params = buildSearchParams(filters);
  const artifacts = useLibraryPage(slug, params);
  const data = artifacts.data;
  const totalPages = data?.totalPages ?? 0;
  useKeepPageInRange(page, artifacts, library.setPage);

  return (
    <section className="flex flex-col gap-6">
      <LibraryHeader slug={slug} />

      <div className="border-border bg-surface-sunken flex flex-col gap-3 rounded-lg border p-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <SearchField
            label="Search the library"
            value={q}
            onChange={setQ}
            placeholder="Search talks, RFCs, notes…"
            className="flex-1"
          />
          <SessionFilter
            slug={slug}
            value={session}
            onChange={library.setSession}
          />
          <Select
            label="Sort"
            hideLabel
            value={sort}
            options={SORT_OPTIONS}
            onChange={library.setSort}
          />
          <div>
            <FilterToggleChip
              active={saved}
              onToggle={library.toggleSaved}
              icon={<Bookmark size={14} aria-hidden />}
            >
              Saved
            </FilterToggleChip>
          </div>
        </div>

        <TypeChips selected={typeFilter} onSelect={library.setType} />
      </div>

      {tag !== null && (
        <ActiveTag tag={tag} onClear={() => library.setTag(null)} />
      )}

      {session > NO_SESSION && (
        <SessionContextStrip slug={slug} sessionId={session} />
      )}

      <ArtifactList
        slug={slug}
        artifacts={data?.content ?? []}
        isLoading={artifacts.isLoading}
        isRefreshing={artifacts.isPlaceholderData}
        isError={artifacts.isError}
        isFiltered={narrowsResults(params)}
        onTagClick={library.setTag}
        focusedId={focusedId}
        {...definedRenderers(renderers)}
        {...(saved ? SAVED_LIST : {})}
      />

      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={library.setPage}
        />
      )}
    </section>
  );
}
