-- Whether somebody has ever signed in as this person. Until now user_id IS NOT NULL said so, because only a sign-in
-- set it; a directory that knows the subject a person signs in with now sets it at provisioning, so the fact gets a
-- column of its own. Filled exactly: every row holding a user_id today got it from a sign-in. last_seen_at cannot
-- answer it, because it was introduced without a backfill and is null for people who signed in before it existed.
ALTER TABLE public.identity_user ADD COLUMN signed_in boolean DEFAULT false NOT NULL;

UPDATE public.identity_user SET signed_in = true WHERE user_id IS NOT NULL;
