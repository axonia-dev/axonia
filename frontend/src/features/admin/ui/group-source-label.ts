import type { GroupSource } from "@/features/admin/domain/admin-user";

export const SOURCE_LABEL: Record<GroupSource, string> = {
  SCIM: "SCIM directory",
  GRAPH: "Microsoft Graph",
  NONE: "Direct entry",
};
