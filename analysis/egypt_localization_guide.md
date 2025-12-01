# Egypt Localization Guide - HCX Platform

## Executive Summary

This document provides comprehensive guidance for localizing the HCX platform for the Egyptian healthcare context, including geographic data, validation patterns, and cultural considerations.

## 1. Phone Number Localization

### Current Indian Format
- **Mobile**: 10 digits starting with 6-9 (e.g., 9493347239)
- **Landline**: Area code + number (e.g., 040-387658992)
- **No country code** in test data

### Egyptian Format Requirements

#### Mobile Numbers
- **Format**: +20 followed by 10 digits
- **Pattern**: `^(\+20|0020|20)?1[0-2|5]\d{8}$`
- **Examples**:
  - +20 100 123 4567 (Vodafone)
  - +20 101 234 5678 (Etisalat)
  - +20 106 345 6789 (Orange)
  - +20 111 456 7890 (WE)
  - +20 120 567 8901 (Vodafone)

#### Landline Numbers
- **Cairo**: +20 2 XXXX XXXX (8 digits after area code)
- **Alexandria**: +20 3 XXX XXXX (7 digits after area code)
- **Other cities**: +20 XX XXX XXXX

#### Operator Prefixes
- **Vodafone**: 10, 11, 12
- **Etisalat**: 11, 14
- **Orange**: 10, 11, 12
- **WE (Telecom Egypt)**: 11, 15

### Implementation

#### Java Validation Pattern
```java
public class EgyptianPhoneValidator {
    private static final String MOBILE_REGEX = "^(\\+20|0020|20)?1[0-2|5]\\d{8}$";
    private static final String LANDLINE_REGEX = "^(\\+20|0020|20)?[2-9]\\d{7,8}$";
    
    public static boolean isValidMobile(String phone) {
        return phone != null && phone.replaceAll("\\s+", "").matches(MOBILE_REGEX);
    }
    
    public static boolean isValidLandline(String phone) {
        return phone != null && phone.replaceAll("\\s+", "").matches(LANDLINE_REGEX);
    }
    
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        // Remove spaces and dashes
        phone = phone.replaceAll("[\\s-]", "");
        // Add country code if missing
        if (phone.startsWith("1") && phone.length() == 10) {
            return "+20" + phone;
        }
        if (phone.startsWith("0") && phone.length() == 11) {
            return "+20" + phone.substring(1);
        }
        if (!phone.startsWith("+")) {
            return "+" + phone;
        }
        return phone;
    }
}
```

### Test Data Updates

#### Before (Indian)
```json
{
  "primary_mobile": "9493347239",
  "phone": ["040-387658992"]
}
```

#### After (Egyptian)
```json
{
  "primary_mobile": "+20 100 123 4567",
  "phone": ["+20 2 2345 6789"]
}
```

## 2. Geographic Data Localization

### Egyptian Governorates (27 Total)

#### Major Governorates
1. **Cairo (القاهرة)** - Capital
   - Postal Code Range: 11XXX
   - Districts: Nasr City, Heliopolis, Maadi, Zamalek, Downtown

2. **Giza (الجيزة)**
   - Postal Code Range: 12XXX
   - Districts: Dokki, Mohandessin, 6th of October City, Sheikh Zayed

3. **Alexandria (الإسكندرية)**
   - Postal Code Range: 21XXX
   - Districts: Montaza, Sidi Gaber, Smouha, Agami

4. **Qalyubia (القليوبية)**
   - Postal Code Range: 13XXX
   - Major Cities: Shubra El Kheima, Qalyub, Banha

5. **Port Said (بورسعيد)**
   - Postal Code Range: 42XXX

6. **Suez (السويس)**
   - Postal Code Range: 43XXX

7. **Ismailia (الإسماعيلية)**
   - Postal Code Range: 41XXX

8. **Dakahlia (الدقهلية)**
   - Postal Code Range: 35XXX
   - Major Cities: Mansoura, Mit Ghamr, Talkha

9. **Sharqia (الشرقية)**
   - Postal Code Range: 44XXX
   - Major Cities: Zagazig, 10th of Ramadan City

10. **Gharbia (الغربية)**
    - Postal Code Range: 31XXX
    - Major Cities: Tanta, Mahalla El Kubra

#### Additional Governorates
11. Monufia (المنوفية) - 32XXX
12. Beheira (البحيرة) - 22XXX
13. Kafr El Sheikh (كفر الشيخ) - 33XXX
14. Damietta (دمياط) - 34XXX
15. Fayoum (الفيوم) - 63XXX
16. Beni Suef (بني سويف) - 62XXX
17. Minya (المنيا) - 61XXX
18. Asyut (أسيوط) - 71XXX
19. Sohag (سوهاج) - 82XXX
20. Qena (قنا) - 83XXX
21. Aswan (أسوان) - 81XXX
22. Luxor (الأقصر) - 85XXX
23. Red Sea (البحر الأحمر) - 84XXX
24. New Valley (الوادي الجديد) - 92XXX
25. Matrouh (مطروح) - 51XXX
26. North Sinai (شمال سيناء) - 45XXX
27. South Sinai (جنوب سيناء) - 46XXX

### Address Structure for Egypt

#### FHIR Address Format
```json
{
  "line": ["Building 10", "Street 15", "District Name"],
  "city": "Cairo",
  "district": "Nasr City",
  "state": "Cairo Governorate",
  "postalCode": "11371",
  "country": "EGYPT"
}
```

#### Database Schema
```sql
CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    building_number VARCHAR(50),
    street_name VARCHAR(200),
    district VARCHAR(100),
    city VARCHAR(100),
    governorate VARCHAR(100) NOT NULL,
    postal_code VARCHAR(5) NOT NULL,
    country VARCHAR(50) DEFAULT 'EGYPT',
    landmark VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_postal_code CHECK (postal_code ~ '^[0-9]{5}$'),
    CONSTRAINT valid_governorate CHECK (governorate IN (
        'Cairo', 'Giza', 'Alexandria', 'Qalyubia', 'Port Said', 
        'Suez', 'Ismailia', 'Dakahlia', 'Sharqia', 'Gharbia',
        'Monufia', 'Beheira', 'Kafr El Sheikh', 'Damietta', 'Fayoum',
        'Beni Suef', 'Minya', 'Asyut', 'Sohag', 'Qena', 'Aswan',
        'Luxor', 'Red Sea', 'New Valley', 'Matrouh', 'North Sinai', 'South Sinai'
    ))
);
```

### Test Data Updates

#### Before (Indian)
```json
{
  "address": {
    "plot": "5-4-199",
    "street": "road no 12",
    "landmark": "Jawaharlal Nehru Road",
    "village": "Nampally",
    "district": "Hyderabad",
    "state": "Telangana",
    "pincode": "500805",
    "locality": "Nampally"
  }
}
```

#### After (Egyptian)
```json
{
  "address": {
    "building": "Building 15",
    "street": "Street 10",
    "landmark": "Near Cairo Festival City",
    "district": "Nasr City",
    "city": "Cairo",
    "governorate": "Cairo",
    "postal_code": "11371",
    "country": "EGYPT"
  }
}
```

## 3. National ID Localization

### Egyptian National ID Format

#### Structure
- **Length**: 14 digits
- **Format**: `XYYYYMMDDGGPPPC`
  - **X**: Century (2 for 1900s, 3 for 2000s)
  - **YYYY**: Year of birth (4 digits)
  - **MM**: Month of birth (01-12)
  - **DD**: Day of birth (01-31)
  - **GG**: Governorate code (01-35)
  - **PPP**: Sequence number (001-999)
  - **C**: Check digit

#### Examples
- `29001011234567` - Born in 1990, January 01, Cairo
- `30105152345678` - Born in 2010, May 15, Alexandria

#### Validation Rules
1. Must be exactly 14 digits
2. First digit must be 2 or 3
3. Month must be 01-12
4. Day must be 01-31 (validate against month)
5. Governorate code must be valid (01-35)
6. Check digit validation using Luhn algorithm

### Implementation

#### Java Validator
```java
public class EgyptianNationalIDValidator {
    private static final String ID_REGEX = "^[2-3]\\d{13}$";
    
    public static boolean isValid(String nationalId) {
        if (nationalId == null || !nationalId.matches(ID_REGEX)) {
            return false;
        }
        
        // Extract components
        int century = Integer.parseInt(nationalId.substring(0, 1));
        int year = Integer.parseInt(nationalId.substring(1, 5));
        int month = Integer.parseInt(nationalId.substring(5, 7));
        int day = Integer.parseInt(nationalId.substring(7, 9));
        int governorate = Integer.parseInt(nationalId.substring(9, 11));
        
        // Validate date components
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;
        
        // Validate governorate code
        if (governorate < 1 || governorate > 35) return false;
        
        // Validate check digit (Luhn algorithm)
        return validateCheckDigit(nationalId);
    }
    
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
    
    public static Map<String, Object> extractInfo(String nationalId) {
        if (!isValid(nationalId)) {
            throw new IllegalArgumentException("Invalid National ID");
        }
        
        Map<String, Object> info = new HashMap<>();
        int century = Integer.parseInt(nationalId.substring(0, 1));
        int year = Integer.parseInt(nationalId.substring(1, 5));
        int month = Integer.parseInt(nationalId.substring(5, 7));
        int day = Integer.parseInt(nationalId.substring(7, 9));
        int governorate = Integer.parseInt(nationalId.substring(9, 11));
        
        info.put("birthDate", String.format("%04d-%02d-%02d", year, month, day));
        info.put("governorateCode", governorate);
        info.put("gender", getGender(nationalId));
        
        return info;
    }
    
    private static String getGender(String nationalId) {
        int sequenceNumber = Integer.parseInt(nationalId.substring(11, 14));
        return (sequenceNumber % 2 == 0) ? "Female" : "Male";
    }
}
```

### FHIR Identifier System
```json
{
  "identifier": [
    {
      "use": "official",
      "type": {
        "coding": [{
          "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
          "code": "NI",
          "display": "National unique individual identifier"
        }]
      },
      "system": "https://mohp.gov.eg/national-id",
      "value": "29001011234567"
    }
  ]
}
```

## 4. Healthcare Provider Identifiers

### Egyptian Medical Syndicate License

#### Format
- **Pattern**: `EMS-XXXX-YYYY-ZZZZ`
  - **EMS**: Egyptian Medical Syndicate prefix
  - **XXXX**: Specialty code
  - **YYYY**: Year of registration
  - **ZZZZ**: Sequential number

#### Examples
- `EMS-0101-2020-0123` - General Practitioner
- `EMS-0205-2019-0456` - Cardiologist
- `EMS-0310-2021-0789` - Surgeon

### Facility Registration

#### Healthcare Facility Registry (HFR) Code
- **Format**: `HFR-EG-XXXXX`
  - **HFR**: Healthcare Facility Registry
  - **EG**: Egypt country code
  - **XXXXX**: Sequential 5-digit number

#### Examples
- `HFR-EG-00001` - Cairo University Hospital
- `HFR-EG-00123` - Alexandria Medical Center
- `HFR-EG-01234` - Private Clinic

### FHIR Organization Identifier
```json
{
  "identifier": [
    {
      "system": "https://mohp.gov.eg/facilities",
      "value": "HFR-EG-00001"
    },
    {
      "system": "https://medical-syndicate.gov.eg/licenses",
      "value": "EMS-0101-2020-0123"
    }
  ]
}
```

## 5. Insurance System Identifiers

### FRA Insurance Company Codes

#### Format
- **Pattern**: `EG-INS-XXX`
  - **EG**: Egypt
  - **INS**: Insurance
  - **XXX**: 3-digit company code

#### Major Insurance Companies
- `EG-INS-001` - Misr Insurance Company
- `EG-INS-002` - Al Ahly Insurance Company
- `EG-INS-003` - Delta Insurance Company
- `EG-INS-004` - Royal Insurance Company
- `EG-INS-005` - Suez Canal Insurance Company

### Policy Number Format

#### Structure
- **Pattern**: `POL-{COMPANY}-{YEAR}-{SEQUENCE}`
- **Example**: `POL-EG-INS-001-2024-123456`

### FHIR Coverage Identifier
```json
{
  "identifier": [
    {
      "system": "https://fra.gov.eg/insurance-policies",
      "value": "POL-EG-INS-001-2024-123456"
    }
  ],
  "payor": [{
    "reference": "Organization/EG-INS-001",
    "display": "Misr Insurance Company"
  }]
}
```

## 6. Currency and Financial Data

### Egyptian Pound (EGP)

#### Currency Code
- **ISO 4217**: EGP
- **Symbol**: E£ or ج.م (Arabic)
- **Subdivisions**: 100 piastres (قرش)

#### Amount Formatting
```json
{
  "amount": {
    "value": 1500.00,
    "currency": "EGP"
  }
}
```

#### Decimal Precision
- Use 2 decimal places for EGP amounts
- Example: 1500.50 EGP

### Bank Account Validation

#### IBAN Format for Egypt
- **Pattern**: `EG{2digits}{4digits}{17digits}`
- **Length**: 29 characters
- **Example**: `EG380019000500000000263180002`

#### Validation
```java
public class EgyptianIBANValidator {
    private static final String IBAN_REGEX = "^EG\\d{27}$";
    
    public static boolean isValid(String iban) {
        if (iban == null || !iban.matches(IBAN_REGEX)) {
            return false;
        }
        // Implement IBAN check digit validation
        return validateIBANCheckDigit(iban);
    }
}
```

## 7. Date and Time Localization

### Date Format
- **Standard**: DD/MM/YYYY (Egyptian convention)
- **ISO 8601**: YYYY-MM-DD (for APIs)

### Time Zone
- **Egypt Standard Time (EST)**: UTC+2
- **No Daylight Saving Time** (as of 2023)

### Calendar
- **Primary**: Gregorian calendar
- **Religious**: Islamic (Hijri) calendar for religious holidays

### Implementation
```java
public class EgyptianDateTimeFormatter {
    private static final ZoneId EGYPT_ZONE = ZoneId.of("Africa/Cairo");
    
    public static String formatDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(formatter);
    }
    
    public static ZonedDateTime now() {
        return ZonedDateTime.now(EGYPT_ZONE);
    }
}
```

## 8. Language and Text Direction

### Official Language
- **Primary**: Arabic (العربية)
- **Secondary**: English (for technical/medical terms)

### Text Direction
- **Arabic**: Right-to-Left (RTL)
- **English**: Left-to-Right (LTR)
- **Numbers**: Left-to-Right in both languages

### UI Considerations
```css
/* RTL Support */
[dir="rtl"] {
  direction: rtl;
  text-align: right;
}

/* Arabic Font Stack */
body[lang="ar"] {
  font-family: 'Cairo', 'Tajawal', 'IBM Plex Sans Arabic', sans-serif;
}
```

## 9. Validation Summary Table

| Data Type | Format | Example | Validation Pattern |
|-----------|--------|---------|-------------------|
| Mobile Phone | +20 1XX XXX XXXX | +20 100 123 4567 | `^\+201[0-2\|5]\d{8}$` |
| Landline | +20 X XXXX XXXX | +20 2 2345 6789 | `^\+20[2-9]\d{7,8}$` |
| National ID | 14 digits | 29001011234567 | `^[2-3]\d{13}$` |
| Postal Code | 5 digits | 11371 | `^\d{5}$` |
| IBAN | EG + 27 digits | EG380019000500000000263180002 | `^EG\d{27}$` |
| Facility Code | HFR-EG-XXXXX | HFR-EG-00001 | `^HFR-EG-\d{5}$` |
| Insurance Code | EG-INS-XXX | EG-INS-001 | `^EG-INS-\d{3}$` |
| Medical License | EMS-XXXX-YYYY-ZZZZ | EMS-0101-2020-0123 | `^EMS-\d{4}-\d{4}-\d{4}$` |

## 10. Implementation Checklist

### Phase 1: Data Model Updates
- [ ] Update address schema for Egyptian governorates
- [ ] Add Egyptian phone number validation
- [ ] Implement National ID validation
- [ ] Update currency fields to EGP
- [ ] Add IBAN validation for Egyptian banks

### Phase 2: Test Data Updates
- [ ] Replace all Indian phone numbers with Egyptian format
- [ ] Update all addresses with Egyptian governorates
- [ ] Generate sample Egyptian National IDs
- [ ] Update insurance company references
- [ ] Update facility identifiers

### Phase 3: Validation Logic
- [ ] Implement phone number validators
- [ ] Implement National ID validator
- [ ] Implement IBAN validator
- [ ] Implement postal code validator
- [ ] Add governorate validation

### Phase 4: UI/UX Updates
- [ ] Add RTL support for Arabic
- [ ] Update date/time formatting
- [ ] Add Arabic translations
- [ ] Update form placeholders with Egyptian examples
- [ ] Add Egyptian governorate dropdown

### Phase 5: Integration
- [ ] Update FHIR profiles with Egyptian identifiers
- [ ] Configure Egyptian regulatory body endpoints
- [ ] Update insurance provider registry
- [ ] Configure Egyptian healthcare facility registry
- [ ] Test end-to-end with Egyptian data

## 11. Testing Strategy

### Unit Tests
```java
@Test
public void testEgyptianMobileValidation() {
    assertTrue(EgyptianPhoneValidator.isValidMobile("+20 100 123 4567"));
    assertTrue(EgyptianPhoneValidator.isValidMobile("01001234567"));
    assertFalse(EgyptianPhoneValidator.isValidMobile("9493347239")); // Indian
}

@Test
public void testNationalIDValidation() {
    assertTrue(EgyptianNationalIDValidator.isValid("29001011234567"));
    assertFalse(EgyptianNationalIDValidator.isValid("12345678901234"));
}

@Test
public void testGovernorateValidation() {
    assertTrue(AddressValidator.isValidGovernorate("Cairo"));
    assertTrue(AddressValidator.isValidGovernorate("Alexandria"));
    assertFalse(AddressValidator.isValidGovernorate("Telangana")); // Indian
}
```

### Integration Tests
- Test complete patient registration with Egyptian data
- Test insurance claim with Egyptian insurance company
- Test facility lookup with Egyptian HFR codes
- Test address validation with Egyptian governorates

### Data Migration Tests
- Verify all Indian references removed
- Verify all Egyptian data properly formatted
- Verify no broken references after migration
- Verify FHIR profiles validate against Egyptian specifications

## 12. Documentation Updates

### API Documentation
- Update all examples with Egyptian data
- Document Egyptian identifier formats
- Provide Egyptian validation rules
- Include Egyptian regulatory requirements

### User Guides
- Create Egyptian-specific user guides
- Translate key documentation to Arabic
- Provide Egyptian healthcare system context
- Include Egyptian regulatory compliance information

## 13. Regulatory Compliance

### Egyptian Healthcare Regulations
- **Data Protection**: Egyptian Data Protection Law (No. 151 of 2020)
- **Healthcare**: Ministry of Health and Population regulations
- **Insurance**: Financial Regulatory Authority (FRA) requirements
- **Privacy**: Patient confidentiality requirements

### Compliance Checklist
- [ ] Ensure data residency in Egypt
- [ ] Implement Egyptian data protection standards
- [ ] Comply with FRA insurance regulations
- [ ] Follow MoHP healthcare facility standards
- [ ] Implement patient consent requirements

## Conclusion

This localization guide provides comprehensive coverage of all Egyptian-specific requirements for the HCX platform. Implementation should follow the phased approach outlined, with thorough testing at each stage to ensure accuracy and compliance with Egyptian regulations.
