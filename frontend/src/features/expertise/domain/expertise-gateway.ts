import type {
  AreaEvidencePage,
  DeclaredArea,
  ExpertiseArea,
  MapContributions,
  MergeSuggestion,
  Practitioner,
  SelfView,
  SourceType,
  TagUsage,
  TopicMap,
} from "@/features/expertise/domain/expertise";
import type { NamedArea } from "@/features/expertise/domain/named-area";

export interface ExpertiseSearchParams {
  term: string;
  /** Narrows the result to people with a signal in this community. */
  demonstratedOnly: boolean;
}

export interface DismissInput {
  sourceType: SourceType;
  sourceId: number;
  tag: string;
}

export interface ExpertiseGateway {
  search(slug: string, params: ExpertiseSearchParams): Promise<Practitioner[]>;
  areas(slug: string, search?: string): Promise<ExpertiseArea[]>;
  map(slug: string): Promise<TopicMap>;
  /** Every visible contribution the community can show on its map, capped and enriched. */
  mapContributions(slug: string): Promise<MapContributions>;
  suggestions(slug: string, tags: string[]): Promise<Practitioner[]>;
  selfView(): Promise<SelfView>;
  /** The same self view read from one community's signals and declarations alone. */
  selfViewIn(slug: string): Promise<SelfView>;
  areaEvidence(
    tag: string,
    offset: number,
    limit: number,
  ): Promise<AreaEvidencePage>;
  areaEvidenceIn(
    slug: string,
    tag: string,
    offset: number,
    limit: number,
  ): Promise<AreaEvidencePage>;
  setOptOut(optedOut: boolean): Promise<void>;
  hideTag(tag: string): Promise<void>;
  unhideTag(tag: string): Promise<void>;
  dismiss(input: DismissInput): Promise<void>;
  declare(slug: string, tag: string): Promise<DeclaredArea>;
  removeDeclaration(slug: string, tag: string): Promise<void>;
  curationSuggestions(slug: string): Promise<MergeSuggestion[]>;
  /** Every name the community produced, folded onto its canonical, with no privacy filter
   *  on the list itself: a name whose carriers are all hidden still comes back, counting 0. */
  curationVocabulary(slug: string): Promise<ExpertiseArea[]>;
  merge(slug: string, variant: string, canonical: string): Promise<void>;
  mergeBatch(
    slug: string,
    variants: string[],
    canonical: string,
  ): Promise<void>;
  split(slug: string, variant: string): Promise<void>;
  dismissSuggestion(slug: string, tagA: string, tagB: string): Promise<void>;
  restoreSuggestion(slug: string, tagA: string, tagB: string): Promise<void>;
  tagUsage(slug: string, tag: string): Promise<TagUsage>;
  /** Rewrites the tag on every artifact, topic and talk slot of the community. */
  renameTag(slug: string, from: string, to: string): Promise<void>;
  /** Strips the tag from every artifact, topic and talk slot of the community. */
  deleteTag(slug: string, tag: string): Promise<void>;
  namedAreas(slug: string): Promise<NamedArea[]>;
  createNamedArea(slug: string, name: string): Promise<NamedArea>;
  renameNamedArea(slug: string, id: number, name: string): Promise<void>;
  deleteNamedArea(slug: string, id: number): Promise<void>;
  /** Moves the tag out of any other named area that held it. */
  placeInNamedArea(slug: string, id: number, tag: string): Promise<void>;
  removeFromNamedArea(slug: string, tag: string): Promise<void>;
}
