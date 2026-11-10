package ir.phonehunter.model;

/** One ad row as it comes from the Divar search list, before any interpretation. */
public record RawListing(
        String token,
        String title,
        String priceText,
        String conditionText,
        String subtitle
) {
    public String url() {
        return "https://divar.ir/v/" + token;
    }
}
