package ir.phonehunter.store;

import ir.phonehunter.model.Condition;
import ir.phonehunter.model.PhoneSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Context / memory of the agent: every ad it has seen, so medians are built from real history. */
@Repository
public class ListingRepository {

    public enum Change { NEW, PRICE_DROP, SEEN }

    public record GroupRow(String model, int storageGb, String condition, long count) {
    }

    private final JdbcClient jdbc;

    public ListingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEmpty(String cityId) {
        Long n = jdbc.sql("SELECT COUNT(*) FROM listing WHERE city_id = ?").param(cityId)
                .query(Long.class).single();
        return n == 0;
    }

    public Change upsert(String token, String cityId, PhoneSpec spec, Condition condition, long price,
                         String title, boolean risky, long now) {
        Optional<Long> oldPrice = jdbc.sql("SELECT price FROM listing WHERE token = ?").param(token)
                .query(Long.class).optional();
        if (oldPrice.isEmpty()) {
            jdbc.sql("""
                    INSERT INTO listing(token, city_id, model, storage_gb, condition, price, title, risky, first_seen, last_seen)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(token, cityId, spec.model(), spec.storageGb(), condition.name(), price, title,
                            risky ? 1 : 0, now, now)
                    .update();
            return Change.NEW;
        }
        jdbc.sql("UPDATE listing SET price = ?, title = ?, last_seen = ?, risky = MAX(risky, ?) WHERE token = ?")
                .params(price, title, now, risky ? 1 : 0, token)
                .update();
        return price < oldPrice.get() ? Change.PRICE_DROP : Change.SEEN;
    }

    public List<Long> prices(String cityId, PhoneSpec spec, Condition condition, long sinceMillis) {
        return jdbc.sql("""
                        SELECT price FROM listing
                        WHERE city_id = ? AND model = ? AND storage_gb = ? AND condition = ?
                          AND risky = 0 AND price IS NOT NULL AND last_seen >= ?""")
                .params(cityId, spec.model(), spec.storageGb(), condition.name(), sinceMillis)
                .query(Long.class).list();
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

    public List<GroupRow> groups(String cityId, long sinceMillis) {
        return jdbc.sql("""
                        SELECT model, storage_gb, condition, COUNT(*) AS n FROM listing
                        WHERE city_id = ? AND risky = 0 AND price IS NOT NULL AND last_seen >= ?
                        GROUP BY model, storage_gb, condition ORDER BY model, storage_gb, condition""")
                .params(cityId, sinceMillis)
                .query((rs, i) -> new GroupRow(rs.getString("model"), rs.getInt("storage_gb"),
                        rs.getString("condition"), rs.getLong("n")))
                .list();
    }

    public List<Map<String, Object>> recentDeals(int limit) {
        return jdbc.sql("""
                        SELECT token, model, storage_gb, condition, notified_price AS price, title, deal_note, notified_at
                        FROM listing WHERE notified_at IS NOT NULL ORDER BY notified_at DESC LIMIT ?""")
                .param(limit).query().listOfRows();
    }
}
