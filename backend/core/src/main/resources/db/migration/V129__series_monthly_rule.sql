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
