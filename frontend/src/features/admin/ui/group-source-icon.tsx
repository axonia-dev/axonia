import { Cloud, Database, Pencil } from "lucide-react";
import type { ReactNode } from "react";

import type { GroupSource } from "@/features/admin/domain/admin-user";

export const SOURCE_ICON: Record<GroupSource, ReactNode> = {
  SCIM: <Database size={14} aria-hidden />,
  GRAPH: <Cloud size={14} aria-hidden />,
  NONE: <Pencil size={14} aria-hidden />,
};
