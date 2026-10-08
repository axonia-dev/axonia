import type { Page } from "@/lib/page";
import type {
  MailServerChange,
  MailServerValues,
  MailServerView,
  SaveProof,
  TestOutcome,
} from "@/selfhosted/features/mail-server/domain/mail-server";

/** Driving port: every read and write of the installation's mail server. */
export interface MailServerGateway {
  read(): Promise<MailServerView>;
  /** Whether mail leaves at all, which any signed-in member may ask. */
  isConfigured(): Promise<boolean>;
  test(values: MailServerValues, recipient: string): Promise<TestOutcome>;
  save(values: MailServerValues, proof: SaveProof): Promise<MailServerView>;
  /** Clears the stored fields as they were at `baseRevision`; refused as stale otherwise. */
  clear(baseRevision: number): Promise<void>;
  changes(page: number): Promise<Page<MailServerChange>>;
}
