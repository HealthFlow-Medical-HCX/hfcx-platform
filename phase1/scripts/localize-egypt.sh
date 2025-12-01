#!/bin/bash
#
# HCX Egypt Localization Script
# Implement Egyptian phone numbers, National IDs, governorates, and other locale-specific changes
#
# Usage: ./localize-egypt.sh [--dry-run] [--repo-path /path/to/repo]
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default values
DRY_RUN=false
REPO_PATH="/home/ubuntu/hcx-egypt"
LOG_FILE="/home/ubuntu/hcx-egypt/phase1/logs/localization-$(date +%Y%m%d_%H%M%S).log"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --repo-path)
      REPO_PATH="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Create log directory
mkdir -p "$(dirname "$LOG_FILE")"
mkdir -p "${REPO_PATH}/phase1/generated"

# Logging functions
log() {
  echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

log_warning() {
  echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
}

# Generate Egyptian phone validator
generate_phone_validator() {
  log "Generating Egyptian phone number validator"
  
  local output_file="${REPO_PATH}/phase1/generated/EgyptianPhoneValidator.java"
  
  cat > "$output_file" << 'EOF'
package org.healthflow.hcx.utils.validators;

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
EOF

  log_success "Generated: $output_file"
}

# Generate Egyptian National ID validator
generate_national_id_validator() {
  log "Generating Egyptian National ID validator"
  
  local output_file="${REPO_PATH}/phase1/generated/EgyptianNationalIDValidator.java"
  
  cat > "$output_file" << 'EOF'
package org.healthflow.hcx.utils.validators;

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
EOF

  log_success "Generated: $output_file"
}

# Generate Egyptian governorates enum
generate_governorates_enum() {
  log "Generating Egyptian governorates enum"
  
  local output_file="${REPO_PATH}/phase1/generated/EgyptianGovernorate.java"
  
  cat > "$output_file" << 'EOF'
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
EOF

  log_success "Generated: $output_file"
}

# Generate IBAN validator
generate_iban_validator() {
  log "Generating Egyptian IBAN validator"
  
  local output_file="${REPO_PATH}/phase1/generated/EgyptianIBANValidator.java"
  
  cat > "$output_file" << 'EOF'
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
EOF

  log_success "Generated: $output_file"
}

# Update test data with Egyptian examples
update_test_data() {
  log "Updating test data with Egyptian examples"
  
  if [ "$DRY_RUN" = true ]; then
    log_warning "[DRY RUN] Would update test data files"
    return
  fi
  
  # Find and update JSON test files
  find "$REPO_PATH" -type f -name "*test*.json" -o -name "*sample*.json" | while read -r file; do
    # Update phone numbers
    sed -i 's/"9493347239"/"+20 100 123 4567"/g' "$file"
    sed -i 's/"040-387658992"/"+20 2 1234 5678"/g' "$file"
    
    # Update addresses
    sed -i 's/"Hyderabad"/"Cairo"/g' "$file"
    sed -i 's/"Telangana"/"Cairo"/g' "$file"
    sed -i 's/"500805"/"11371"/g' "$file"
    
    log "  Updated: $file"
  done
  
  log_success "Test data updated"
}

# Generate localization report
generate_report() {
  local report_file="${REPO_PATH}/phase1/docs/localization-report-$(date +%Y%m%d_%H%M%S).md"
  
  log "Generating localization report: $report_file"
  
  cat > "$report_file" << EOF
# HCX Egypt Localization Report

**Date**: $(date '+%Y-%m-%d %H:%M:%S')
**Mode**: $([ "$DRY_RUN" = true ] && echo "DRY RUN" || echo "LIVE")

## Summary

This report summarizes the localization of the HCX platform for Egypt.

## Generated Files

1. **EgyptianPhoneValidator.java** - Phone number validation
2. **EgyptianNationalIDValidator.java** - National ID validation
3. **EgyptianGovernorate.java** - Governorates enumeration
4. **EgyptianIBANValidator.java** - IBAN validation

## Integration Instructions

### 1. Copy Generated Files

\`\`\`bash
cp phase1/generated/*.java hfcx-platform/src/main/java/org/healthflow/hcx/utils/validators/
cp phase1/generated/EgyptianGovernorate.java hfcx-platform/src/main/java/org/healthflow/hcx/enums/
\`\`\`

### 2. Update Dependencies

Add to \`pom.xml\`:

\`\`\`xml
<dependency>
    <groupId>javax.validation</groupId>
    <artifactId>validation-api</artifactId>
    <version>2.0.1.Final</version>
</dependency>
\`\`\`

### 3. Create Custom Validators

Use the generated validators in your entity classes:

\`\`\`java
@Entity
public class Participant {
    @Column(name = "primary_mobile")
    @ValidEgyptianPhone
    private String primaryMobile;
    
    @Column(name = "national_id")
    @ValidEgyptianNationalID
    private String nationalId;
}
\`\`\`

### 4. Update Test Data

All test data files have been updated with Egyptian examples.

## Next Steps

1. Run unit tests to verify validators
2. Update FHIR profiles with Egyptian identifiers
3. Update UI forms with Egyptian address fields
4. Add Arabic language support

## Detailed Log

See full log: $LOG_FILE

---

**Generated by**: HCX Egypt Localization Script
EOF

  log_success "Report generated: $report_file"
}

# Main execution
main() {
  log "========================================="
  log "HCX Egypt Localization Script"
  log "========================================="
  log "Mode: $([ "$DRY_RUN" = true ] && echo "DRY RUN" || echo "LIVE")"
  log "Repository: $REPO_PATH"
  log "========================================="
  
  generate_phone_validator
  generate_national_id_validator
  generate_governorates_enum
  generate_iban_validator
  update_test_data
  generate_report
  
  log "========================================="
  log_success "Localization completed!"
  log "========================================="
  log "Generated files are in: ${REPO_PATH}/phase1/generated/"
  log "See report for integration instructions"
}

# Run main function
main
