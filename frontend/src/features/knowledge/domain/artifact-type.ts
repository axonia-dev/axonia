import { z } from "zod";

export const artifactTypeSchema = z.object({
  id: z.number(),
  label: z.string(),
  icon: z.string().nullable(),
  accentColor: z.string().nullable(),
  artifactCount: z.number(),
});

export type ArtifactType = z.infer<typeof artifactTypeSchema>;
