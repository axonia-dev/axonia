import type {
  MessageType,
  NotificationPreference,
} from "./notification-preference";

/** Port for the member's own notification preferences. Implemented by the HTTP adapter. */
export interface NotificationPreferenceGateway {
  getMine(): Promise<NotificationPreference[]>;
  setMine(type: MessageType, enabled: boolean): Promise<void>;
}
