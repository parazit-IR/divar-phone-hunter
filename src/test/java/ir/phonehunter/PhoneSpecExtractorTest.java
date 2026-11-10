package ir.phonehunter;

import ir.phonehunter.extract.PhoneSpecExtractor;
import ir.phonehunter.model.PhoneSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneSpecExtractorTest {

    private final PhoneSpecExtractor extractor = new PhoneSpecExtractor(List.of(
            "iPhone 1[3-5]( Mini| Plus| Pro| Pro Max)?",
            "Galaxy S2[1-5]( Plus| Ultra| FE)?",
            "Galaxy A[0-9]{2}s?"));

    private void expect(String title, String model, int storage) {
        Optional<PhoneSpec> spec = extractor.extract(title, null);
        assertTrue(spec.isPresent(), "no spec for: " + title);
        assertEquals(model, spec.get().model(), title);
        assertEquals(storage, spec.get().storageGb(), title);
    }

    private void expectNone(String title) {
        assertTrue(extractor.extract(title, null).isEmpty(), "should be empty: " + title);
    }

    @Test
    void iphones() {
        expect("آیفون ۱۳ پرو مکس ۲۵۶ گیگ", "iPhone 13 Pro Max", 256);
        expect("ایفون 13پرومکس 128", "iPhone 13 Pro Max", 128);
        expect("iPhone 14 Pro 256GB رجیستر شده", "iPhone 14 Pro", 256);
        expect("آیفون ۱۵ معمولی ۱۲۸ گیگابایت", "iPhone 15", 128);
        expect("آیفون 13 مینی 128 گیگ", "iPhone 13 Mini", 128);
        expect("آیفون ۱۴ پلاس ۵۱۲", "iPhone 14 Plus", 512);
        expect("آیفون ۱۵ پرو مکس ۱ ترا", "iPhone 15 Pro Max", 1024);
    }

    @Test
    void samsungs() {
        expect("سامسونگ S24 اولترا ۲۵۶/۱۲", "Galaxy S24 Ultra", 256);
        expect("گلکسی اس ۲۳ پلاس 256 گیگ", "Galaxy S23 Plus", 256);
        expect("Samsung Galaxy S21 FE 128GB", "Galaxy S21 FE", 128);
        expect("سامسونگ A55 حافظه ۲۵۶ رم ۸", "Galaxy A55", 256);
        expect("سامسونگ a05s 64 گیگ", "Galaxy A05s", 64);
        expect("گوشی سامسونگ A35 5G 128 گیگ رم 8 گیگ", "Galaxy A35", 128);
    }

    @Test
    void outOfScopeOrAmbiguous() {
        expectNone("آیفون ۱۲ پرو ۱۲۸");                // model not in scope
        expectNone("آیفون ۱۳ پرو ۱۲۸ و ۲۵۶ موجود است");  // two storages
        expectNone("آیفون ۱۳ پرو");                    // no storage
        expectNone("شیائومی ردمی نوت ۱۳ ۲۵۶ گیگ");      // other brand
    }
}
