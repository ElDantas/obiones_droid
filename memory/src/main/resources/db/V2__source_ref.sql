ALTER TABLE lessons ADD COLUMN IF NOT EXISTS source_ref text UNIQUE;
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS status_changed_at timestamptz;
