CREATE SCHEMA IF NOT EXISTS staging;

CREATE TABLE IF NOT EXISTS staging.unit (
    identifier text PRIMARY KEY,
    parent_identifier text,
    acronym text
);

CREATE TABLE IF NOT EXISTS staging.unit_name (
    identifier text NOT NULL,
    language text NOT NULL,
    text text NOT NULL,
    PRIMARY KEY (identifier, language)
);

CREATE TABLE IF NOT EXISTS staging.unit_part (
    parent_identifier text NOT NULL,
    child_identifier text NOT NULL,
    PRIMARY KEY (parent_identifier, child_identifier)
);
