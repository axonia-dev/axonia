import type {
  HeldArea,
  Learner,
  LearningPage,
} from "@/features/learning/domain/learning";

export interface LearningGateway {
  page(slug: string): Promise<LearningPage>;
  learners(slug: string, tag: string): Promise<Learner[]>;
  add(slug: string, tag: string): Promise<HeldArea>;
  remove(slug: string, tag: string): Promise<void>;
}
