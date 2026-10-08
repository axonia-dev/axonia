import type {
  RoundupPreview,
  RoundupSettings,
} from "@/features/admin/domain/roundup-settings";

// Outbound PORT for the quarterly roundup an administrator governs: whether it goes out
// at all, and what it will look like when it does. All three operations are refused for a
// Community Hub, which has no provisioned directory a roundup could be addressed to.
export interface RoundupGateway {
  getSettings(): Promise<RoundupSettings>;
  setEnabled(enabled: boolean): Promise<RoundupSettings>;
  getPreview(): Promise<RoundupPreview>;
}
