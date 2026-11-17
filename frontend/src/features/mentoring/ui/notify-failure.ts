import { notifyError } from "@/components/ui/toast";
import { mentoringErrorMessage } from "@/features/mentoring/mentoring-errors";

export function onFailure(error: unknown): void {
  notifyError(mentoringErrorMessage(error));
}
