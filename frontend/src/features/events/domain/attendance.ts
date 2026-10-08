import { z } from "zod";

export const attendanceSchema = z.object({
  id: z.number(),
  sessionId: z.number(),
  userId: z.string(),
  createdAt: z.string(),
});

export type Attendance = z.infer<typeof attendanceSchema>;
