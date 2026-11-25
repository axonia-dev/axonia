import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { ANY_SESSION, NO_SESSION } from "@/features/knowledge/ui/SessionFilter";
import { ALL_TYPES } from "@/features/knowledge/ui/TypeChips";

export type Sort = "newest" | "mostAppreciated";
export const DEFAULT_SORT: Sort = "newest";

/** How long typing pauses before the search is asked, so a word is one request rather than one per letter. */
const SEARCH_DEBOUNCE_MS = 300;

/** The URL's spelling of the "No session" facet, which the backend calls `session=0`. */
const NO_SESSION_PARAM = "none";

export interface LibraryFilters {
  q: string;
  typeFilter: number;
  tag: string | null;
  session: number;
  sort: Sort;
  saved: boolean;
  /** Zero-based; the URL carries it one-based, and not at all on the first page. */
  page: number;
}

function positiveInt(value: string | null): number | undefined {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : undefined;
}

function sessionFrom(value: string | null): number {
  if (value === NO_SESSION_PARAM) {
    return NO_SESSION;
  }
  return positiveInt(value) ?? ANY_SESSION;
}

function sessionParam(session: number): string | null {
  if (session === ANY_SESSION) {
    return null;
  }
  return session === NO_SESSION ? NO_SESSION_PARAM : String(session);
}

function filtersFrom(params: URLSearchParams): LibraryFilters {
  return {
    q: params.get("q") ?? "",
    typeFilter: positiveInt(params.get("type")) ?? ALL_TYPES,
    tag: params.get("tag"),
    session: sessionFrom(params.get("session")),
    sort:
      params.get("sort") === "mostAppreciated"
        ? "mostAppreciated"
        : DEFAULT_SORT,
    saved: params.get("saved") === "1",
    page: (positiveInt(params.get("page")) ?? 1) - 1,
  };
}

type Changes = Record<string, string | null>;

/**
 * Every library filter and the page live in the URL, so a reload, the back button or a shared
 * link lands on exactly the list that was on screen. Changing a filter starts again from the
 * first page; every write replaces the entry rather than stacking one per click.
 */
export function useLibraryFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = filtersFrom(searchParams);

  function update(changes: Changes, keepPage = false) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        for (const [key, value] of Object.entries(changes)) {
          if (value === null) {
            next.delete(key);
          } else {
            next.set(key, value);
          }
        }
        if (!keepPage) {
          next.delete("page");
        }
        return next;
      },
      { replace: true },
    );
  }

  return {
    filters,
    focusedId: positiveInt(searchParams.get("focus")),
    setQuery: (q: string) => update({ q: q === "" ? null : q }),
    setType: (id: number) =>
      update({ type: id === ALL_TYPES ? null : String(id) }),
    setTag: (tag: string | null) => update({ tag }),
    setSession: (session: number) => update({ session: sessionParam(session) }),
    setSort: (sort: Sort) =>
      update({ sort: sort === DEFAULT_SORT ? null : sort }),
    toggleSaved: () => update({ saved: filters.saved ? null : "1" }),
    setPage: (page: number) =>
      update({ page: page === 0 ? null : String(page + 1) }, true),
  };
}

/**
 * The search box's own text, written to the URL once typing pauses. When the URL's query moves
 * on its own (a link, the back button) the box follows it; a query this box wrote itself is
 * already what it shows. Only a move of the URL is followed: the router applies a write in a
 * transition, so a render between this box's write and the URL catching up must not read the
 * stale URL as a move.
 */
export function useSearchText(
  urlQ: string,
  writeQuery: (q: string) => void,
): readonly [string, (value: string) => void] {
  const [text, setText] = useState(urlQ);
  const [written, setWritten] = useState(urlQ);
  const [seenUrlQ, setSeenUrlQ] = useState(urlQ);
  if (urlQ !== seenUrlQ) {
    setSeenUrlQ(urlQ);
    if (urlQ !== written) {
      setWritten(urlQ);
      setText(urlQ);
    }
  }

  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(timer.current), []);

  function change(value: string) {
    setText(value);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      const trimmed = value.trim();
      setWritten(trimmed);
      writeQuery(trimmed);
    }, SEARCH_DEBOUNCE_MS);
  }

  return [text, change] as const;
}
