import type {
  RaiseActionItemInput,
  UpdateActionItemInput,
} from "@/features/events/domain/action-item";
import { actionItemSchema } from "@/features/events/domain/action-item";
import type {
  ActionItemListParams,
  EventsGateway,
} from "@/features/events/domain/events-gateway";
import { apiFetch, apiFetchVoid } from "@/lib/api-client";
import { pageSchema } from "@/lib/page";
import { buildQuery as query } from "@/lib/query";

const actionItemPageSchema = pageSchema(actionItemSchema);

export const actionItemEndpoints: Pick<
  EventsGateway,
  | "listActionItems"
  | "raiseActionItem"
  | "editActionItem"
  | "deleteActionItem"
  | "closeActionItem"
  | "dropActionItem"
  | "reopenActionItem"
> = {
  async listActionItems(slug, params: ActionItemListParams) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/action-items${query({ ...params })}`,
    );
    return actionItemPageSchema.parse(data);
  },

  async raiseActionItem(slug, input: RaiseActionItemInput) {
    const data = await apiFetch<unknown>(
      `/axonia/communities/${slug}/action-items`,
      { method: "POST", body: JSON.stringify(input) },
    );
    return actionItemSchema.parse(data);
  },

  async editActionItem(id, input: UpdateActionItemInput) {
    const data = await apiFetch<unknown>(`/axonia/action-items/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    });
    return actionItemSchema.parse(data);
  },

  async deleteActionItem(id) {
    await apiFetchVoid(`/axonia/action-items/${id}`, { method: "DELETE" });
  },

  async closeActionItem(id) {
    const data = await apiFetch<unknown>(`/axonia/action-items/${id}/close`, {
      method: "POST",
    });
    return actionItemSchema.parse(data);
  },

  async dropActionItem(id) {
    const data = await apiFetch<unknown>(`/axonia/action-items/${id}/drop`, {
      method: "POST",
    });
    return actionItemSchema.parse(data);
  },

  async reopenActionItem(id) {
    const data = await apiFetch<unknown>(`/axonia/action-items/${id}/reopen`, {
      method: "POST",
    });
    return actionItemSchema.parse(data);
  },
};
