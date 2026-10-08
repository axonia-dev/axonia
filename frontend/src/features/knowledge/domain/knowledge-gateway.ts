import type { Artifact } from "@/features/knowledge/domain/artifact";
import type { ArtifactSession } from "@/features/knowledge/domain/artifact-session";
import type { ArtifactType } from "@/features/knowledge/domain/artifact-type";
import type { LibraryStats } from "@/features/knowledge/domain/library-stats";
import type {
  Attachment,
  AttachResourceInput,
  DetachResourceInput,
  SessionResource,
} from "@/features/knowledge/domain/session-resource";
import type { Page } from "@/lib/page";

export interface ArtifactSearchParams {
  page?: number;
  size?: number;
  q?: string;
  type?: number;
  tag?: string;
  /**
   * Session facet. A positive id keeps artifacts from that session (provenance
   * or attachment); the sentinel `0` keeps only standalone (session-less)
   * artifacts; omit it for no session filtering.
   */
  session?: number;
  /** ISO instant, inclusive lower bound on createdAt. */
  from?: string;
  /** Result ordering. `newest` (default) or `mostAppreciated` (by love count). */
  sort?: "newest" | "mostAppreciated";
}

export interface ArtifactTypeInput {
  label: string;
  icon: string | null;
  accentColor: string | null;
}

export interface ProvenanceInput {
  sessionId: number;
  segmentId?: number;
  label?: string;
}

export interface CaptureArtifactInput {
  title: string;
  typeId: number;
  url: string;
  description?: string;
  tags?: string[];
  provenance?: ProvenanceInput;
  /**
   * Whose contribution this is, when it is not the caller's. A member reference the backend
   * resolves against the community's roster; a bare user id is honoured exactly. Omitted means
   * the caller, which is right for a live contribution and wrong for a backfilled one: the
   * recording of a talk Alice gave is Alice's contribution, not the organizer's who filed it.
   * Supplying it requires being an organizer, so a caller who is not one must omit it.
   */
  contributor?: string;
}

export interface EditArtifactInput {
  title: string;
  typeId: number;
  url: string;
  description?: string;
  tags?: string[];
}

export interface KnowledgeGateway {
  search(slug: string, params: ArtifactSearchParams): Promise<Page<Artifact>>;
  capture(slug: string, input: CaptureArtifactInput): Promise<Artifact>;
  listTypes(): Promise<ArtifactType[]>;
  createType(input: ArtifactTypeInput): Promise<ArtifactType>;
  updateType(id: number, input: ArtifactTypeInput): Promise<ArtifactType>;
  deleteType(id: number, reassignToId?: number): Promise<void>;
  listSessions(slug: string): Promise<ArtifactSession[]>;
  stats(slug: string): Promise<LibraryStats>;
  sessionResources(slug: string, sessionId: number): Promise<SessionResource[]>;
  attachResource(
    slug: string,
    sessionId: number,
    input: AttachResourceInput,
  ): Promise<Attachment>;
  detachResource(
    slug: string,
    sessionId: number,
    input: DetachResourceInput,
  ): Promise<void>;
  edit(id: number, input: EditArtifactInput): Promise<Artifact>;
  /** Credits the artifact to another member. A user id; allowed only where the community lets the caller credit. */
  changeContributor(id: number, contributor: string): Promise<Artifact>;
  remove(id: number): Promise<void>;
  /** Counts one opening of an artifact. Resolves either way; callers do not read the result. */
  recordOpening(artifactId: number): Promise<void>;
}
