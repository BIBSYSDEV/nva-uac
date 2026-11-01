CREATE TABLE unit (
    identifier varchar(39) PRIMARY KEY,
    acronym text,
    parent_identifier varchar(39) REFERENCES unit (identifier)
);

CREATE INDEX unit_parent_identifier ON unit (parent_identifier);

CREATE TABLE unit_label (
    unit_identifier varchar(39) NOT NULL REFERENCES unit (identifier) ON DELETE CASCADE,
    language varchar(35) NOT NULL,
    text text NOT NULL,
    PRIMARY KEY (unit_identifier, language)
);

-- The checks below mirror the invariants of the value objects in core. The identifier's
-- segments carry no meaning (ADR 0003): the hierarchy is parent_identifier and nothing else.
-- They use PostgreSQL functions the jOOQ code generator cannot simulate, so it skips them.
-- [jooq ignore start]
ALTER TABLE unit
    ADD CONSTRAINT unit_identifier_form
        CHECK (identifier ~ '^(0|[1-9][0-9]{0,8})(\.(0|[1-9][0-9]{0,8})){3}$'),
    ADD CONSTRAINT unit_acronym_not_blank
        CHECK (acronym IS NULL OR btrim(acronym) <> ''),
    ADD CONSTRAINT unit_is_not_its_own_parent
        CHECK (parent_identifier IS NULL OR parent_identifier <> identifier);

ALTER TABLE unit_label
    ADD CONSTRAINT unit_label_language_form
        CHECK (language ~ '^[a-z]{2,3}(-[a-z0-9]{2,8})*$'),
    ADD CONSTRAINT unit_label_text_not_blank
        CHECK (btrim(text) <> '');
-- [jooq ignore stop]
