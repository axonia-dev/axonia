import { useParams } from "react-router-dom";

import { HealthView } from "@/features/health/ui/HealthView";

export function HealthSection() {
  const { slug = "" } = useParams();
  return <HealthView slug={slug} />;
}
