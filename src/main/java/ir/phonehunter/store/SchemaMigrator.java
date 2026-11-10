package ir.phonehunter.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates the SQLite schema and upgrades databases created by older versions
 * (keeps the price history you already collected).
 */
@Component
public class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    public SchemaMigrator(JdbcClient jdbc) {
        jdbc.sql("""
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
                )""").update();

        List<String> columns = jdbc.sql("SELECT name FROM pragma_table_info('listing')").query(String.class).list();
        addColumnIfMissing(jdbc, columns, "registry", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
        addColumnIfMissing(jdbc, columns, "region", "TEXT NOT NULL DEFAULT 'UNKNOWN'");
        if (!columns.contains("region")) {
            jdbc.sql("UPDATE listing SET region = 'NOT_APPLICABLE' WHERE model NOT LIKE 'iPhone%'").update();
        }

        jdbc.sql("DROP INDEX IF EXISTS idx_listing_group").update();
        jdbc.sql("""
                CREATE INDEX IF NOT EXISTS idx_listing_group2
                    ON listing (city_id, model, storage_gb, condition, registry, region, last_seen)""").update();
    }

    private static void addColumnIfMissing(JdbcClient jdbc, List<String> existing, String name, String definition) {
        if (!existing.contains(name)) {
            jdbc.sql("ALTER TABLE listing ADD COLUMN " + name + " " + definition).update();
            log.info("Database upgraded: added column '{}'", name);
        }
    }
}
