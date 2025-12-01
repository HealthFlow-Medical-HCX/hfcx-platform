package org.healthflow.hcx.enums;

/**
 * Egyptian Governorates Enumeration
 * Contains all 27 governorates of Egypt
 */
public enum EgyptianGovernorate {
    CAIRO("Cairo", "القاهرة", "11XXX", 1),
    GIZA("Giza", "الجيزة", "12XXX", 21),
    ALEXANDRIA("Alexandria", "الإسكندرية", "21XXX", 2),
    QALYUBIA("Qalyubia", "القليوبية", "13XXX", 14),
    PORT_SAID("Port Said", "بورسعيد", "42XXX", 3),
    SUEZ("Suez", "السويس", "43XXX", 4),
    ISMAILIA("Ismailia", "الإسماعيلية", "41XXX", 19),
    DAKAHLIA("Dakahlia", "الدقهلية", "35XXX", 12),
    SHARQIA("Sharqia", "الشرقية", "44XXX", 13),
    GHARBIA("Gharbia", "الغربية", "31XXX", 16),
    MONUFIA("Monufia", "المنوفية", "32XXX", 17),
    BEHEIRA("Beheira", "البحيرة", "22XXX", 18),
    KAFR_EL_SHEIKH("Kafr El Sheikh", "كفر الشيخ", "33XXX", 15),
    DAMIETTA("Damietta", "دمياط", "34XXX", 11),
    FAYOUM("Fayoum", "الفيوم", "63XXX", 23),
    BENI_SUEF("Beni Suef", "بني سويف", "62XXX", 22),
    MINYA("Minya", "المنيا", "61XXX", 24),
    ASYUT("Asyut", "أسيوط", "71XXX", 25),
    SOHAG("Sohag", "سوهاج", "82XXX", 26),
    QENA("Qena", "قنا", "83XXX", 27),
    ASWAN("Aswan", "أسوان", "81XXX", 28),
    LUXOR("Luxor", "الأقصر", "85XXX", 29),
    RED_SEA("Red Sea", "البحر الأحمر", "84XXX", 31),
    NEW_VALLEY("New Valley", "الوادي الجديد", "92XXX", 32),
    MATROUH("Matrouh", "مطروح", "51XXX", 33),
    NORTH_SINAI("North Sinai", "شمال سيناء", "45XXX", 34),
    SOUTH_SINAI("South Sinai", "جنوب سيناء", "46XXX", 35);
    
    private final String englishName;
    private final String arabicName;
    private final String postalCodePattern;
    private final int nationalIdCode;
    
    EgyptianGovernorate(String englishName, String arabicName, String postalCodePattern, int nationalIdCode) {
        this.englishName = englishName;
        this.arabicName = arabicName;
        this.postalCodePattern = postalCodePattern;
        this.nationalIdCode = nationalIdCode;
    }
    
    public String getEnglishName() {
        return englishName;
    }
    
    public String getArabicName() {
        return arabicName;
    }
    
    public String getPostalCodePattern() {
        return postalCodePattern;
    }
    
    public int getNationalIdCode() {
        return nationalIdCode;
    }
    
    public static EgyptianGovernorate fromNationalIdCode(int code) {
        for (EgyptianGovernorate gov : values()) {
            if (gov.nationalIdCode == code) {
                return gov;
            }
        }
        throw new IllegalArgumentException("Invalid governorate code: " + code);
    }
    
    public static EgyptianGovernorate fromEnglishName(String name) {
        for (EgyptianGovernorate gov : values()) {
            if (gov.englishName.equalsIgnoreCase(name)) {
                return gov;
            }
        }
        throw new IllegalArgumentException("Invalid governorate name: " + name);
    }
}
