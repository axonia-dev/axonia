-- A community switches mentoring on; off by default, so no existing community changes behaviour.
ALTER TABLE public.communities ADD COLUMN mentoring_enabled boolean DEFAULT false NOT NULL;
