package eg.gov.healthflow.hfcx.sdk.validators;

import java.util.regex.Pattern;

/**
 * Egyptian Phone Number Validator
 * Validates Egyptian mobile and landline numbers
 * 
 * Mobile format: +20 1XX XXX XXXX (10 digits after country code)
 * Landline format: +20 X XXXX XXXX
 */
public class EgyptianPhoneValidator {
    
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^(\\+20|0020|20)?1[0-2|5]\\d{8}$");
    private static final Pattern LANDLINE_PATTERN = Pattern.compile("^(\\+20|0020|20)?[2-9]\\d{7,8}$");
    
    /**
     * Validates if the phone number is a valid Egyptian mobile number
     * @param phone Phone number to validate
     * @return true if valid mobile number
     */
    public static boolean isValidMobile(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String normalized = phone.replaceAll("[\\s-()]", "");
        return MOBILE_PATTERN.matcher(normalized).matches();
    }
    
    /**
     * Validates if the phone number is a valid Egyptian landline number
     * @param phone Phone number to validate
     * @return true if valid landline number
     */
    public static boolean isValidLandline(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String normalized = phone.replaceAll("[\\s-()]", "");
        return LANDLINE_PATTERN.matcher(normalized).matches();
    }
    
    /**
     * Validates if the phone number is valid (mobile or landline)
     * @param phone Phone number to validate
     * @return true if valid phone number
     */
    public static boolean isValid(String phone) {
        return isValidMobile(phone) || isValidLandline(phone);
    }
    
    /**
     * Normalizes Egyptian phone number to international format (+20...)
     * @param phone Phone number to normalize
     * @return Normalized phone number or null if invalid
     */
    public static String normalize(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        
        String cleaned = phone.replaceAll("[\\s-()]", "");
        
        // Already in international format
        if (cleaned.startsWith("+20")) {
            return cleaned;
        }
        
        // Remove country code variations
        if (cleaned.startsWith("0020")) {
            return "+" + cleaned.substring(2);
        }
        if (cleaned.startsWith("20")) {
            return "+" + cleaned;
        }
        
        // Local format (starts with 0 or 1)
        if (cleaned.startsWith("0") && cleaned.length() == 11) {
            return "+20" + cleaned.substring(1);
        }
        if (cleaned.startsWith("1") && cleaned.length() == 10) {
            return "+20" + cleaned;
        }
        
        return null;
    }
    
    /**
     * Gets the operator name from mobile number
     * @param phone Mobile phone number
     * @return Operator name or "Unknown"
     */
    public static String getOperator(String phone) {
        String normalized = normalize(phone);
        if (normalized == null || !isValidMobile(phone)) {
            return "Unknown";
        }
        
        // Extract the first 2 digits after +20
        String prefix = normalized.substring(3, 5);
        
        switch (prefix) {
            case "10":
                return "Vodafone/Orange";
            case "11":
                return "Vodafone/Etisalat/Orange/WE";
            case "12":
                return "Vodafone/Orange";
            case "14":
                return "Etisalat";
            case "15":
                return "WE";
            default:
                return "Unknown";
        }
    }
}
