import { z } from "zod";

export const mentoringSettingsSchema = z.object({ enabled: z.boolean() });

export type MentoringSettings = z.infer<typeof mentoringSettingsSchema>;
