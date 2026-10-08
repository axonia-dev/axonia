import { useState } from "react";

import {
  ALL_COMMUNITIES,
  type AreaFilters,
  type AreaSort,
} from "@/features/expertise/domain/area-filter";

/**
 * The My expertise toolbar state. When the toolbar goes away (no areas and no declarations left)
 * the filters reset, so a lens nobody can see any more cannot keep hiding the page.
 */
export function useAreaFilters(toolbarShown: boolean) {
  const [query, setQuery] = useState("");
  const [communityId, setCommunityId] = useState(ALL_COMMUNITIES);
  const [sort, setSort] = useState<AreaSort>("depth");
  const [hasTalks, setHasTalks] = useState(false);
  const [hasArtifacts, setHasArtifacts] = useState(false);
  const [hiddenOnly, setHiddenOnly] = useState(false);
  const [wasShown, setWasShown] = useState(toolbarShown);

  function clear() {
    setQuery("");
    setCommunityId(ALL_COMMUNITIES);
    setHasTalks(false);
    setHasArtifacts(false);
    setHiddenOnly(false);
  }

  if (toolbarShown !== wasShown) {
    setWasShown(toolbarShown);
    if (!toolbarShown) clear();
  }

  const filters: AreaFilters = {
    query,
    communityId,
    hasTalks,
    hasArtifacts,
    hiddenOnly,
  };

  return {
    filters,
    sort,
    key: `${query}|${communityId}|${sort}|${hasTalks}|${hasArtifacts}|${hiddenOnly}`,
    setQuery,
    setCommunityId,
    setSort,
    toggleHasTalks: () => setHasTalks((v) => !v),
    toggleHasArtifacts: () => setHasArtifacts((v) => !v),
    toggleHiddenOnly: () => setHiddenOnly((v) => !v),
    clear,
  };
}
