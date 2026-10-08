import { apiFetchVoid } from "@/lib/api-client";

import type { UnsubscribeGateway } from "../domain/unsubscribe-gateway";

const PATH = "/public/notifications/unsubscribe";

export const httpUnsubscribeGateway: UnsubscribeGateway = {
  async unsubscribe(token: string | null) {
    const query = token === null ? "" : `?token=${encodeURIComponent(token)}`;
    await apiFetchVoid(`${PATH}${query}`, { method: "POST" });
  },
};
