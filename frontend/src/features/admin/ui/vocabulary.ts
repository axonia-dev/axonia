/** The organization's classification catalogues, held once as pure data - no JSX,
 *  no icon or manager component imports - so a caller that only needs the paths or the
 *  gating permissions (the settings-nav pattern catalogue, the route catalogue, and their
 *  tests) is not dragged into the manager component trees `vocabulary-tabs.tsx`
 *  binds them to. `vocabulary-tabs.tsx` re-composes the full `VocabularyTab` shape from
 *  these specs, so there is still exactly one place a tab is declared. */

export const VOCABULARY_PATH = "vocabulary";

export const VOCABULARY_TAB_SPECS = [
  {
    path: "community-types",
    label: "Community types",
    permission: "community_kind:read",
  },
  {
    path: "library-types",
    label: "Library types",
    permission: "library_type:read",
  },
  {
    path: "section-types",
    label: "Section types",
    permission: "section_type:read",
  },
  {
    path: "reactions",
    label: "Reactions",
    permission: "reaction:read",
  },
] as const;

export type VocabularyTabPath = (typeof VOCABULARY_TAB_SPECS)[number]["path"];

/** The rail item is reachable by anyone holding any one of these. */
export const VOCABULARY_PERMISSIONS: ReadonlyArray<string> =
  VOCABULARY_TAB_SPECS.map((tab) => tab.permission);
