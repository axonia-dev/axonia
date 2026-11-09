-- Which day a monthly series lands on: the same day number, or the same weekday at the same position ("the second
-- Wednesday"). Every monthly series so far kept its day number, so that is what the existing rows say. Only a monthly
-- series carries a rule.
ALTER TABLE public.event_series ADD COLUMN cadence_monthly_by character varying(20);

UPDATE public.event_series SET cadence_monthly_by = 'DAY_OF_MONTH' WHERE cadence_unit = 'MONTH';

ALTER TABLE public.event_series
    ADD CONSTRAINT event_series_cadence_monthly_by_check
        CHECK (cadence_monthly_by IN ('DAY_OF_MONTH', 'WEEKDAY_OF_MONTH')),
    ADD CONSTRAINT event_series_cadence_monthly_by_only_monthly
        CHECK ((cadence_unit = 'MONTH') = (cadence_monthly_by IS NOT NULL));

-- Occurrences are now measured from the start date rather than stepped from the previous one. Stepping clamped a
-- series started on the 29th to 31st (or on 29 February) into a short month and kept it there, so the sessions written
-- since sit on anchors the cadence no longer computes, and each would sit beside a projected duplicate. Each moves to
-- the anchor of its own month, which is the start date plus the whole months between them, clamped by Postgres.
WITH reanchored AS (
    SELECT s.id,
           (e.starts_on + make_interval(months =>
               ((extract(YEAR FROM s.occurrence_date) - extract(YEAR FROM e.starts_on)) * 12
                   + extract(MONTH FROM s.occurrence_date) - extract(MONTH FROM e.starts_on))::int))::date AS anchor
    FROM public.community_session s
    JOIN public.event_series e ON e.id = s.series_id
    WHERE e.cadence_unit IN ('MONTH', 'YEAR')
      AND extract(DAY FROM e.starts_on) > 28
      AND s.occurrence_date > e.starts_on
)
UPDATE public.community_session s
SET occurrence_date = r.anchor
FROM reanchored r
WHERE s.id = r.id
  AND s.occurrence_date <> r.anchor
  AND NOT EXISTS (SELECT 1 FROM public.community_session taken
                  WHERE taken.organization_id = s.organization_id
                    AND taken.series_id = s.series_id
                    AND taken.occurrence_date = r.anchor);
