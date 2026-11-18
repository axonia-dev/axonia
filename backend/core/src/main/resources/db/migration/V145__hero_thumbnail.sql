ALTER TABLE public.roundup_thumbnail RENAME TO hero_thumbnail;

ALTER TABLE public.hero_thumbnail RENAME CONSTRAINT uq_roundup_thumbnail TO uq_hero_thumbnail;
