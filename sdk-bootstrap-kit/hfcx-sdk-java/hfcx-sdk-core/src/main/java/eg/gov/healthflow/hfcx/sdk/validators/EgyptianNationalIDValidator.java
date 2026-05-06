package eg.gov.healthflow.hfcx.sdk.validators;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Egyptian National ID Validator
 * Validates 14-digit Egyptian National ID numbers
 * 
 * Format: XYYYYMMDDGGPPPC
 * X: Century (2 for 1900s, 3 for 2000s)
 * YYYY: Year of birth
 * MM: Month (01-12)
 * DD: Day (01-31)
 * GG: Governorate code (01-35)
 * PPP: Sequence number
 * C: Check digit (Luhn algorithm)
 */
public class EgyptianNationalIDValidator {
    
    private static final Pattern ID_PATTERN = Pattern.compile("^[2-3]\\d{13}$");
    
    private static final Map<Integer, String> GOVERNORATE_CODES = new HashMap<>();
    
    static {
        GOVERNORATE_CODES.put(1, "Cairo");
        GOVERNORATE_CODES.put(2, "Alexandria");
        GOVERNORATE_CODES.put(3, "Port Said");
        GOVERNORATE_CODES.put(4, "Suez");
        GOVERNORATE_CODES.put(11, "Damietta");
        GOVERNORATE_CODES.put(12, "Dakahlia");
        GOVERNORATE_CODES.put(13, "Sharqia");
        GOVERNORATE_CODES.put(14, "Qalyubia");
        GOVERNORATE_CODES.put(15, "Kafr El Sheikh");
        GOVERNORATE_CODES.put(16, "Gharbia");
        GOVERNORATE_CODES.put(17, "Monufia");
        GOVERNORATE_CODES.put(18, "Beheira");
        GOVERNORATE_CODES.put(19, "Ismailia");
        GOVERNORATE_CODES.put(21, "Giza");
        GOVERNORATE_CODES.put(22, "Beni Suef");
        GOVERNORATE_CODES.put(23, "Fayoum");
        GOVERNORATE_CODES.put(24, "Minya");
        GOVERNORATE_CODES.put(25, "Asyut");
        GOVERNORATE_CODES.put(26, "Sohag");
        GOVERNORATE_CODES.put(27, "Qena");
        GOVERNORATE_CODES.put(28, "Aswan");
        GOVERNORATE_CODES.put(29, "Luxor");
        GOVERNORATE_CODES.put(31, "Red Sea");
        GOVERNORATE_CODES.put(32, "New Valley");
        GOVERNORATE_CODES.put(33, "Matrouh");
        GOVERNORATE_CODES.put(34, "North Sinai");
        GOVERNORATE_CODES.put(35, "South Sinai");
    }
    
    /**
     * Validates Egyptian National ID
     * @param nationalId National ID to validate
     * @return true if valid
     */
    public static boolean isValid(String nationalId) {
        if (nationalId == null || !ID_PATTERN.matcher(nationalId).matches()) {
            return false;
        }
        
        try {
            // Validate date components
            int century = Integer.parseInt(nationalId.substring(0, 1));
            int year = Integer.parseInt(nationalId.substring(1, 5));
            int month = Integer.parseInt(nationalId.substring(5, 7));
            int day = Integer.parseInt(nationalId.substring(7, 9));
            int governorate = Integer.parseInt(nationalId.substring(9, 11));
            
            // Validate month and day
            if (month < 1 || month > 12) return false;
            if (day < 1 || day > 31) return false;
            
            // Validate governorate code
            if (!GOVERNORATE_CODES.containsKey(governorate)) return false;
            
            // Validate date
            try {
                LocalDate.of(year, month, day);
            } catch (Exception e) {
                return false;
            }
            
            // Validate check digit using Luhn algorithm
            return validateCheckDigit(nationalId);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validates check digit using Luhn algorithm
     */
    private static boolean validateCheckDigit(String id) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = Character.getNumericValue(id.charAt(i));
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == Character.getNumericValue(id.charAt(13));
    }
    
    /**
     * Extracts information from National ID
     * @param nationalId National ID
     * @return Map containing extracted information
     */
    public static Map<String, Object> extractInfo(String nationalId) {
        if (!isValid(nationalId)) {
            throw new IllegalArgumentException("Invalid National ID");
        }
        
        Map<String, Object> info = new HashMap<>();
        
        int year = Integer.parseInt(nationalId.substring(1, 5));
        int month = Integer.parseInt(nationalId.substring(5, 7));
        int day = Integer.parseInt(nationalId.substring(7, 9));
        int governorate = Integer.parseInt(nationalId.substring(9, 11));
        int sequence = Integer.parseInt(nationalId.substring(11, 14));
        
        LocalDate birthDate = LocalDate.of(year, month, day);
        
        info.put("birthDate", birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
        info.put("governorateCode", governorate);
        info.put("governorate", GOVERNORATE_CODES.get(governorate));
        info.put("gender", (sequence % 2 == 0) ? "Female" : "Male");
        info.put("age", LocalDate.now().getYear() - year);
        
        return info;
    }
}
