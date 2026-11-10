CREATE TABLE IF NOT EXISTS listing (
    token          TEXT PRIMARY KEY,
    city_id        TEXT    NOT NULL,
    model          TEXT    NOT NULL,
    storage_gb     INTEGER NOT NULL,
    condition      TEXT    NOT NULL,
    price          INTEGER,
    title          TEXT,
    risky          INTEGER NOT NULL DEFAULT 0,
    first_seen     INTEGER NOT NULL,
    last_seen      INTEGER NOT NULL,
    notified_at    INTEGER,
    notified_price INTEGER,
    deal_note      TEXT
);

CREATE INDEX IF NOT EXISTS idx_listing_group
    ON listing (city_id, model, storage_gb, condition, last_seen);
