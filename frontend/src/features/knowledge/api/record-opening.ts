import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";

const gateway = httpKnowledgeGateway;

/**
 * Counts one opening, and never lets that get in the way. No mutation, no toast, no error state:
 * the count must not delay or interrupt somebody opening the thing they came for, so a failure is
 * silence. The design accepts losing a number over costing a reader a moment.
 */
export function recordOpening(artifactId: number): void {
  void gateway.recordOpening(artifactId).catch(() => undefined);
}
