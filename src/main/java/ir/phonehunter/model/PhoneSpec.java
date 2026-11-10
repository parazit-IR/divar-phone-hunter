package ir.phonehunter.model;

/**
 * Normalized phone identity. Two ads are comparable only when model, storage and condition match.
 *
 * @param model     canonical name, e.g. "iPhone 13 Pro Max" or "Galaxy S24 Ultra"
 * @param storageGb 64, 128, 256, 512 or 1024
 */
public record PhoneSpec(String model, int storageGb) {

    public String label() {
        return model + " " + (storageGb >= 1024 ? (storageGb / 1024) + "TB" : storageGb + "GB");
    }
}
