package ir.phonehunter.store;

import ir.phonehunter.analysis.ThresholdTuner;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.MarketGroup;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.model.Region;
import ir.phonehunter.model.Registry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Context / memory of the agent: every ad it has seen, so medians are built from real history. */
@Repository
public class ListingRepository {

    public enum Change { NEW, PRICE_DROP, SEEN }

    private final JdbcClient jdbc;

    /** SchemaMigrator is injected only to guarantee the schema exists before the first query. */
    public ListingRepository(JdbcClient jdbc, SchemaMigrator migrator) {
        this.jdbc = jdbc;
    }

    public boolean isEmpty(String cityId) {
        Long n = jdbc.sql("SELECT COUNT(*) FROM listing WHERE city_id = ?").param(cityId)
                .query(Long.class).single();
        return n == 0;
    }

    public Change upsert(String token, String cityId, MarketGroup g, long price, String title,
                         boolean risky, long now) {
        Optional<Long> oldPrice = jdbc.sql("SELECT price FROM listing WHERE token = ?").param(token)
                .query(Long.class).optional();
        if (oldPrice.isEmpty()) {
            jdbc.sql("""
                    INSERT INTO listing(token, city_id, model, storage_gb, condition, registry, region, price, title,
                                        risky, first_seen, last_seen)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(token, cityId, g.spec().model(), g.spec().storageGb(), g.condition().name(),
                            g.registry().name(), g.region().name(), price, title, risky ? 1 : 0, now, now)
                    .update();
            return Change.NEW;
        }
        // registry/region learned from the description are kept unless the title now says something specific
        jdbc.sql("""
                UPDATE listing SET price = ?, title = ?, last_seen = ?, risky = MAX(risky, ?),
                       registry = CASE WHEN ? = 'UNKNOWN' THEN registry ELSE ? END,
                       region   = CASE WHEN ? = 'UNKNOWN' THEN region   ELSE ? END
                WHERE token = ?""")
                .params(price, title, now, risky ? 1 : 0,
                        g.registry().name(), g.registry().name(), g.region().name(), g.region().name(), token)
                .update();
        return price < oldPrice.get() ? Change.PRICE_DROP : Change.SEEN;
    }

    /** Stored classification (may be richer than the title, because descriptions are read for candidates). */
    public Optional<MarketGroup> storedGroup(String token) {
        return jdbc.sql("SELECT model, storage_gb, condition, registry, region FROM listing WHERE token = ?")
                .param(token)
                .query((rs, i) -> new MarketGroup(new PhoneSpec(rs.getString(1), rs.getInt(2)),
                        Condition.valueOf(rs.getString(3)), Registry.valueOf(rs.getString(4)),
                        Region.valueOf(rs.getString(5))))
                .optional();
    }

    public void updateClassification(String token, Registry registry, Region region) {
        jdbc.sql("UPDATE listing SET registry = ?, region = ? WHERE token = ?")
                .params(registry.name(), region.name(), token).update();
    }

    /** Prices of the fine group (with region). */
    public List<Long> prices(String cityId, MarketGroup g, long sinceMillis) {
        return jdbc.sql(groupSql(true))
                .params(cityId, g.spec().model(), g.spec().storageGb(), g.condition().name(),
                        g.registry() == Registry.NOT_REGISTERED ? 1 : 0, sinceMillis, g.region().name())
                .query(Long.class).list();
    }

    /** Prices of the coarse group (any region, same registry group). */
    public List<Long> coarsePrices(String cityId, MarketGroup g, long sinceMillis) {
        return jdbc.sql(groupSql(false))
                .params(cityId, g.spec().model(), g.spec().storageGb(), g.condition().name(),
                        g.registry() == Registry.NOT_REGISTERED ? 1 : 0, sinceMillis)
                .query(Long.class).list();
    }

    private static String groupSql(boolean withRegion) {
        return """
                SELECT price FROM listing
                WHERE city_id = ? AND model = ? AND storage_gb = ? AND condition = ?
                  AND (CASE WHEN registry = 'NOT_REGISTERED' THEN 1 ELSE 0 END) = ?
                  AND risky = 0 AND price IS NOT NULL AND last_seen >= ?""" + (withRegion ? " AND region = ?" : "");
    }

    /** Every clean ad first seen in the window — input for threshold tuning. */
    public List<ThresholdTuner.Observation> observations(String cityId, long sinceMillis) {
        return jdbc.sql("""
                        SELECT model, storage_gb, condition, registry, region, price, first_seen FROM listing
                        WHERE city_id = ? AND risky = 0 AND price IS NOT NULL AND first_seen >= ?""")
                .params(cityId, sinceMillis)
                .query((rs, i) -> new ThresholdTuner.Observation(
                        new MarketGroup(new PhoneSpec(rs.getString(1), rs.getInt(2)), Condition.valueOf(rs.getString(3)),
                                Registry.valueOf(rs.getString(4)), Region.valueOf(rs.getString(5))),
                        rs.getLong(6), rs.getLong(7)))
                .list();
    }

    /** Notify once per ad, and again only if the seller drops the price further. */
    public boolean shouldNotify(String token, long price) {
        Optional<Long> notifiedPrice = jdbc.sql(
                        "SELECT COALESCE(notified_price, -1) FROM listing WHERE token = ? AND notified_at IS NOT NULL")
                .param(token).query(Long.class).optional();
        return notifiedPrice.isEmpty() || price < notifiedPrice.get();
    }

    public void markNotified(String token, long price, String note, long now) {
        jdbc.sql("UPDATE listing SET notified_at = ?, notified_price = ?, deal_note = ? WHERE token = ?")
                .params(now, price, note, token).update();
    }

    public void markRisky(String token) {
        jdbc.sql("UPDATE listing SET risky = 1 WHERE token = ?").param(token).update();
    }

    public int notifiedSince(long sinceMillis) {
        Long n = jdbc.sql("SELECT COUNT(*) FROM listing WHERE notified_at >= ?").param(sinceMillis)
                .query(Long.class).single();
        return n.intValue();
    }

    public int newSince(String cityId, long sinceMillis) {
        Long n = jdbc.sql("SELECT COUNT(*) FROM listing WHERE city_id = ? AND first_seen >= ?")
                .params(cityId, sinceMillis).query(Long.class).single();
        return n.intValue();
    }

    public List<Map<String, Object>> recentDeals(int limit) {
        return jdbc.sql("""
                        SELECT token, model, storage_gb, condition, registry, region, notified_price AS price, title,
                               deal_note, notified_at
                        FROM listing WHERE notified_at IS NOT NULL ORDER BY notified_at DESC LIMIT ?""")
                .param(limit).query().listOfRows();
    }
}
