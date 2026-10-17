-- Change checksum from CHAR(64) to VARCHAR(64).
ALTER TABLE documents
    ALTER COLUMN checksum TYPE VARCHAR(64);
