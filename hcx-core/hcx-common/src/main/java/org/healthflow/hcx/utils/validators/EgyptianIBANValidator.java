package org.healthflow.hcx.utils.validators;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Egyptian IBAN Validator
 * Validates Egyptian International Bank Account Numbers
 * 
 * Format: EG{2 check digits}{4 bank code}{17 account number}
 * Length: 29 characters
 * Example: EG380019000500000000263180002
 */
public class EgyptianIBANValidator {
    
    private static final Pattern IBAN_PATTERN = Pattern.compile("^EG\\d{27}$");
    
    /**
     * Validates Egyptian IBAN
     * @param iban IBAN to validate
     * @return true if valid
     */
    public static boolean isValid(String iban) {
        if (iban == null || iban.trim().isEmpty()) {
            return false;
        }
        
        String cleaned = iban.replaceAll("\\s", "").toUpperCase();
        
        if (!IBAN_PATTERN.matcher(cleaned).matches()) {
            return false;
        }
        
        return validateIBANCheckDigit(cleaned);
    }
    
    /**
     * Validates IBAN check digit using mod-97 algorithm
     */
    private static boolean validateIBANCheckDigit(String iban) {
        // Move first 4 characters to end
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        
        // Replace letters with numbers (A=10, B=11, ..., Z=35)
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numeric.append(c);
            } else {
                numeric.append((int) c - 55);
            }
        }
        
        // Calculate mod 97
        BigInteger ibanNumber = new BigInteger(numeric.toString());
        return ibanNumber.mod(BigInteger.valueOf(97)).intValue() == 1;
    }
    
    /**
     * Formats IBAN with spaces for readability
     * @param iban IBAN to format
     * @return Formatted IBAN
     */
    public static String format(String iban) {
        if (!isValid(iban)) {
            throw new IllegalArgumentException("Invalid IBAN");
        }
        
        String cleaned = iban.replaceAll("\\s", "");
        StringBuilder formatted = new StringBuilder();
        
        for (int i = 0; i < cleaned.length(); i += 4) {
            if (i > 0) formatted.append(" ");
            formatted.append(cleaned.substring(i, Math.min(i + 4, cleaned.length())));
        }
        
        return formatted.toString();
    }
    
    /**
     * Extracts bank code from IBAN
     * @param iban IBAN
     * @return Bank code (4 digits)
     */
    public static String getBankCode(String iban) {
        if (!isValid(iban)) {
            throw new IllegalArgumentException("Invalid IBAN");
        }
        return iban.substring(4, 8);
    }
}
