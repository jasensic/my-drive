CREATE TABLE IF NOT EXISTS device_excluded_files (
    device_id UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES files (id) ON DELETE CASCADE,
    PRIMARY KEY (device_id, file_id)
);

CREATE INDEX IF NOT EXISTS device_excluded_files_device_idx ON device_excluded_files (device_id);
