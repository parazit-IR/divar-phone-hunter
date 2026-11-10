package ir.phonehunter.model;

/** Robust price distribution of one comparable group (model + storage + condition + city). */
public record PriceStats(int count, long p25, long median, long p75) {
}
