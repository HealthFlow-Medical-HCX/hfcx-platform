package org.healthflow.hcx.services;

import kong.unirest.HttpResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.healthflow.common.dto.OnboardRequest;
import org.healthflow.common.dto.OnboardResponse;
import org.healthflow.common.dto.ParticipantResponse;
import org.healthflow.common.dto.Response;
import org.healthflow.common.exception.ClientException;
import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.common.exception.OTPVerificationException;
import org.healthflow.common.utils.HttpUtils;
import org.healthflow.common.utils.JSONUtils;
import org.healthflow.common.utils.JWTUtils;
import org.healthflow.hcx.controllers.BaseController;
import org.healthflow.hcx.utils.validators.EgyptianFieldValidator;
import org.healthflow.postgresql.IDatabaseService;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

import static org.healthflow.common.response.ResponseMessage.*;
import static org.healthflow.common.utils.Constants.*;

@Service
public class ParticipantService extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);

    @Value("${email.otpSub}")
    private String otpSub;

    @Value("${email.otpMsg}")
    private String otpMsg;

    @Value("${email.successIdentitySub}")
    private String successIdentitySub;

    @Value("${email.successIdentityMsg}")
    private String successIdentityMsg;

    @Value("${email.onboardingSuccessSub}")
    private String onboardingSuccessSub;

    @Value("${email.onboardingSuccessMsg}")
    private String onboardingSuccessMsg;

    @Value("${hcx-api.basePath}")
    private String hcxAPIBasePath;

    @Value("${postgres.onboardingOtpTable}")
    private String onboardingOtpTable;

    @Value("${postgres.onboardingTable}")
    private String onboardingTable;

    @Value("${otp.expiry}")
    private int otpExpiry;

    @Value("${otp.maxAttempt}")
    private int otpMaxAttempt;

    @Value("${otp.maxRegenerate}")
    private int maxRegenerate;

    @Value("${env}")
    private String env;

    @Value("${registry.hcxCode}")
    private String hcxCode;
    @Value("${jwt-token.privateKey}")
    private String privatekey;
    @Value("${jwt-token.expiryTime}")
    private Long expiryTime;
    @Autowired
    private SMSService smsService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private IDatabaseService postgreSQLClient;

    @Autowired
    private JWTUtils jwtUtils;

    public ResponseEntity<Object> verify(HttpHeaders header, ArrayList<Map<String, Object>> body) throws Exception {
        logger.info("Participant verification :: " + body);
        OnboardRequest request = new OnboardRequest(body);
        Map<String, Object> output = new HashMap<>();
        updateIdentityVerificationStatus(request.getPrimaryEmail(), request.getApplicantCode(), request.getVerifierCode(), PENDING);
        createParticipantAndSendOTP(header, request, output);
        return getSuccessResponse(new Response(output));
    }

    private void updateStatus(String email, String status) throws Exception {
        String query = "UPDATE " + onboardingTable + " SET status = ?, updatedOn = ? WHERE applicant_email = ?";
        postgreSQLClient.execute(query, status, System.currentTimeMillis(), email);
    }

    private void updateIdentityVerificationStatus(String email, String applicantCode, String verifierCode, String status) throws Exception {
        String query = "INSERT INTO " + onboardingTable
                + " (applicant_email,applicant_code,verifier_code,status,createdOn,updatedOn)"
                + " VALUES (?,?,?,?,?,?) ON CONFLICT (applicant_email) DO NOTHING;";
        long now = System.currentTimeMillis();
        postgreSQLClient.execute(query, email, applicantCode, verifierCode, status, now, now);
    }

    private void createParticipantAndSendOTP(HttpHeaders headers, OnboardRequest request, Map<String, Object> output) throws Exception {
        Map<String, Object> participant = request.getParticipant();
        // P0-4b — fail fast on Egyptian field validation before any registry round-trip
        // (hcx-apis re-validates as the security boundary; this is the convenience check).
        EgyptianFieldValidator.validate(participant);
        participant.put(ENDPOINT_URL, "http://testurl/v0.7");
        participant.put(ENCRYPTION_CERT, "https://raw.githubusercontent.com/Swasth-Digital-Health-Foundation/hcx-platform/sprint-27/hcx-apis/src/test/resources/examples/x509-self-signed-certificate.pem");
        participant.put(REGISTRY_STATUS, CREATED);
        if (((ArrayList<String>) participant.get(ROLES)).contains(PAYOR))
            participant.put(SCHEME_CODE, "default");
        String identityVerified = PENDING;
        if (ONBOARD_FOR_PROVIDER.contains(request.getType())) {
            identityVerified = identityVerify(headers, getApplicantBody(request));
        }
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put(AUTHORIZATION, Objects.requireNonNull(headers.get(AUTHORIZATION)).get(0));
        HttpResponse<String> createResponse = HttpUtils.post(hcxAPIBasePath + VERSION_PREFIX + PARTICIPANT_CREATE, JSONUtils.serialize(participant), headersMap);
        ParticipantResponse pcptResponse = JSONUtils.deserialize(createResponse.getBody(), ParticipantResponse.class);
        if (createResponse.getStatus() != 200) {
            throw new ClientException(pcptResponse.getError().getCode() == null ? ErrorCodes.ERR_INVALID_PARTICIPANT_DETAILS : pcptResponse.getError().getCode(), pcptResponse.getError().getMessage());
        }
        String participantCode = (String) JSONUtils.deserialize(createResponse.getBody(), Map.class).get(PARTICIPANT_CODE);
        participant.put(PARTICIPANT_CODE, participantCode);
        String query = "INSERT INTO " + onboardingOtpTable
                + " (participant_code,primary_email,primary_mobile,email_otp,phone_otp,createdOn,"
                + "updatedOn,expiry,phone_otp_verified,email_otp_verified,status,attempt_count)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        long now = System.currentTimeMillis();
        postgreSQLClient.execute(query,
                participantCode,
                participant.get(PRIMARY_EMAIL),
                participant.get(PRIMARY_MOBILE),
                "", "",
                now, now, now,
                false, false,
                PENDING,
                0);
        sendOTP(participant);
        output.put(PARTICIPANT_CODE, participantCode);
        output.put(IDENTITY_VERIFICATION, identityVerified);
        logger.info("OTP has been sent successfully :: participant code : " + participantCode + " :: primary email : " + participant.get(PRIMARY_EMAIL));
    }

    // TODO: change request body to pojo
    private Map<String,Object> getApplicantBody(OnboardRequest request){
        Map<String,Object> body = new HashMap<>();
        body.put(APPLICANT_CODE, request.getApplicantCode());
        body.put(VERIFIER_CODE, request.getVerifierCode());
        body.put(EMAIL, request.getPrimaryEmail());
        body.put(MOBILE, request.getPrimaryMobile());
        body.put(APPLICANT_NAME, request.getParticipantName());
        body.put(ADDITIONALVERIFICATION, request.getAdditionalVerification());
        body.put(ROLE, PROVIDER);
        return body;
    }

    public ResponseEntity<Object> sendOTP(Map<String, Object> requestBody) throws Exception {
        String primaryEmail = (String) requestBody.get(PRIMARY_EMAIL);
        String query = "SELECT regenerate_count, last_regenerate_date FROM " + onboardingOtpTable
                + " WHERE primary_email = ?";
        ResultSet result = postgreSQLClient.executeQuery(query, primaryEmail);
        LocalDate lastRegenerateDate = null;
        int regenerateCount = 0;
        LocalDate currentDate = LocalDate.now();
        if (result.next()) {
            regenerateCount = result.getInt("regenerate_count");
            lastRegenerateDate = result.getObject("last_regenerate_date", LocalDate.class);
        }
        if (!currentDate.equals(lastRegenerateDate)) {
            regenerateCount = 0;
        }
        if (regenerateCount >= maxRegenerate) {
            throw new ClientException(ErrorCodes.ERR_MAXIMUM_OTP_REGENERATE, MAXIMUM_OTP_REGENERATE);
        }
        String phoneOtp = new DecimalFormat("000000").format(new Random().nextInt(999999));
        smsService.sendOTP((String) requestBody.get(PRIMARY_MOBILE), phoneOtp);
        String emailOtp = new DecimalFormat("000000").format(new Random().nextInt(999999));
        sendEmailOTP(primaryEmail, (String) requestBody.get(PARTICIPANT_NAME), (String) requestBody.get(PARTICIPANT_CODE), emailOtp);
        String query1 = "UPDATE " + onboardingOtpTable
                + " SET phone_otp = ?, email_otp = ?, updatedOn = ?, expiry = ?,"
                + " regenerate_count = ?, last_regenerate_date = ? WHERE primary_email = ?";
        long now1 = System.currentTimeMillis();
        postgreSQLClient.execute(query1,
                phoneOtp,
                emailOtp,
                now1,
                now1 + otpExpiry,
                regenerateCount + 1,
                currentDate,
                requestBody.get(PRIMARY_EMAIL));
        return getSuccessResponse(new Response());
    }

    private void sendEmailOTP(String email, String participantName, String participantCode, String emailOtp) {
        String emailMsg = otpMsg;
        emailMsg = emailMsg.replace("USER_NAME", StringUtils.capitalize(participantName))
                .replace("PARTICIPANT_CODE", participantCode)
                .replace("RANDOM_CODE", " " + emailOtp);
        emailService.sendMail(email, otpSub, emailMsg);
    }

    public String verifyOTP(Map<String, Object> requestBody) throws Exception {
        String participantCode = (String) requestBody.get(PARTICIPANT_CODE);
        ResultSet resultSet = null;
        boolean emailOtpVerified = false;
        boolean phoneOtpVerified = false;
        int attemptCount = 0;
        String status = FAILED;
        List<Map<String, Object>> otpVerificationList = (List<Map<String, Object>>) requestBody.get(OTPVERIFICATION);
        try {
            String selectQuery = "SELECT * FROM " + onboardingOtpTable + " WHERE participant_code = ?";
            resultSet = postgreSQLClient.executeQuery(selectQuery, participantCode);
            if (resultSet.next()) {
                attemptCount = resultSet.getInt(ATTEMPT_COUNT);
                if (resultSet.getString("status").equals(SUCCESSFUL)) {
                    status = SUCCESSFUL;
                    throw new ClientException(ErrorCodes.ERR_INVALID_OTP, OTP_ALREADY_VERIFIED);
                }
                if (resultSet.getLong(EXPIRY) > System.currentTimeMillis()) {
                    if (attemptCount < otpMaxAttempt) {
                        for (Map<String, Object> otpVerification : otpVerificationList) {
                            if (otpVerification.get(CHANNEL).equals(EMAIL)) {
                                emailOtpVerified = verifyOTP(resultSet, otpVerification, EMAIL_OTP);
                            }
                            if (otpVerification.get(CHANNEL).equals(PHONE)) {
                                phoneOtpVerified = verifyOTP(resultSet, otpVerification, PHONE_OTP);
                            }
                        }
                    } else {
                        throw new ClientException(ErrorCodes.ERR_INVALID_OTP, OTP_RETRY_LIMIT);
                    }
                } else {
                    throw new ClientException(ErrorCodes.ERR_INVALID_OTP, OTP_EXPIRED);
                }
            } else {
                throw new ClientException(ErrorCodes.ERR_INVALID_OTP, OTP_RECORD_NOT_EXIST);
            }
            updateOtpStatus(true, true, attemptCount, SUCCESSFUL, participantCode);
            logger.info("Communication details verification is successful :: participant_code  : " + participantCode);
            return ACCEPTED;
        } catch (Exception e) {
            updateOtpStatus(emailOtpVerified, phoneOtpVerified, attemptCount, status, participantCode);
            throw new OTPVerificationException(e.getMessage());
        } finally {
            if (resultSet != null) resultSet.close();
        }
    }

    private void updateOtpStatus(boolean emailOtpVerified, boolean phoneOtpVerified, int attemptCount, String status, String email) throws Exception {
        String updateOtpQuery = "UPDATE " + onboardingOtpTable
                + " SET email_otp_verified = ?, phone_otp_verified = ?, status = ?,"
                + " updatedOn = ?, attempt_count = ? WHERE participant_code = ?";
        postgreSQLClient.execute(updateOtpQuery,
                emailOtpVerified,
                phoneOtpVerified,
                status,
                System.currentTimeMillis(),
                attemptCount + 1,
                email);
    }

    private Map<String, Object> getParticipant(String key, String value) throws Exception {
        HttpResponse<String> searchResponse = HttpUtils.post(hcxAPIBasePath + VERSION_PREFIX + PARTICIPANT_SEARCH, "{ \"filters\": { \"" + key + "\": { \"eq\": \" " + value + "\" } } }", new HashMap<>());
        ParticipantResponse participantResponse = JSONUtils.deserialize(searchResponse.getBody(), ParticipantResponse.class);
        if (participantResponse.getParticipants().isEmpty())
            throw new ClientException(ErrorCodes.ERR_INVALID_PARTICIPANT_CODE, INVALID_PARTICIPANT_CODE);
        return (Map<String, Object>) participantResponse.getParticipants().get(0);
    }

    public ResponseEntity<Object> onboardUpdate(Map<String, Object> requestBody) throws Exception {
        logger.info("Onboard update: " + requestBody);
        boolean emailOtpVerified = false;
        boolean phoneOtpVerified = false;
        String identityStatus = REJECTED;
        String jwtToken = (String) requestBody.get(JWT_TOKEN);
        Map<String, Object> payload = JSONUtils.decodeBase64String(jwtToken.split("\\.")[1], Map.class);
        Map<String, Object> participant = (Map<String, Object>) requestBody.get(PARTICIPANT);
        String email = (String) payload.get("email");
        participant.put(REGISTRY_STATUS, ACTIVE);
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put(AUTHORIZATION, "Bearer " + jwtToken);

        String otpQuery = "SELECT * FROM " + onboardingOtpTable + " WHERE primary_email = ?";
        ResultSet resultSet = postgreSQLClient.executeQuery(otpQuery, email);
        if (resultSet.next()) {
            emailOtpVerified = resultSet.getBoolean(EMAIL_OTP_VERIFIED);
            phoneOtpVerified = resultSet.getBoolean(PHONE_OTP_VERIFIED);
        }

        String onboardingQuery = "SELECT * FROM " + onboardingTable + " WHERE applicant_email = ?";
        ResultSet resultSet1 = postgreSQLClient.executeQuery(onboardingQuery, email);
        if (resultSet1.next()) {
            identityStatus = resultSet1.getString("status");
        }

        if (emailOtpVerified && phoneOtpVerified && StringUtils.equalsIgnoreCase(identityStatus, ACCEPTED)) {
            HttpResponse<String> response = HttpUtils.post(hcxAPIBasePath + VERSION_PREFIX + PARTICIPANT_UPDATE, JSONUtils.serialize(participant), headersMap);
            if (response.getStatus() == 200) {
                logger.info("Participant details are updated successfully :: participant code : " + participant.get(PARTICIPANT_CODE));
                emailService.sendMail(email, onboardingSuccessSub, onboardingSuccessMsg.replace("USER_NAME", StringUtils.capitalize((String) participant.get(PARTICIPANT_NAME))));
                return getSuccessResponse(new Response(PARTICIPANT_CODE, participant.get(PARTICIPANT_CODE)));
            } else return new ResponseEntity<>(response.getBody(), HttpStatus.valueOf(response.getStatus()));
        } else {
            logger.info("Participant details are not updated, due to failed identity verification :: participant code : " + participant.get(PARTICIPANT_CODE));
            throw new ClientException(ErrorCodes.ERR_UPDATE_PARTICIPANT_DETAILS, "Identity verification failed");
        }
    }

    public ResponseEntity<Object> identityVerify(Map<String, Object> requestBody) throws Exception {
        String applicantEmail = (String) requestBody.get(PRIMARY_EMAIL);
        String status = (String) requestBody.get(REGISTRY_STATUS);
        if (!ALLOWED_ONBOARD_STATUS.contains(status))
            throw new ClientException(ErrorCodes.ERR_INVALID_ONBOARD_STATUS, "Invalid onboard status, allowed values are: " + ALLOWED_ONBOARD_STATUS);
        //Update status for the user
        String query = "UPDATE " + onboardingTable
                + " SET status = ?, updatedOn = ? WHERE applicant_email = ?";
        postgreSQLClient.execute(query, status, System.currentTimeMillis(), applicantEmail);
        if (status.equals(ACCEPTED)) {
            emailService.sendMail(applicantEmail, successIdentitySub, successIdentityMsg);
            return getSuccessResponse(new Response());
        } else {
            throw new ClientException(ErrorCodes.ERR_INVALID_IDENTITY, "Identity verification has failed");
        }
    }

    public ResponseEntity<Object> getInfo(HttpHeaders header, Map<String, Object> requestBody) {
        try {
            String verifierCode;
            Map<String, Object> verifierDetails;
            if (requestBody.containsKey(VERIFICATION_TOKEN)) {
                String token = (String) requestBody.get(VERIFICATION_TOKEN);
                Map<String, Object> jwtPayload = JSONUtils.decodeBase64String(token.split("\\.")[1], Map.class);
                verifierCode = (String) jwtPayload.get(ISS);
                verifierDetails = getParticipant(PARTICIPANT_CODE, verifierCode);
                if (!token.isEmpty() && !jwtUtils.isValidSignature(token, (String) verifierDetails.get(SIGNING_CERT_PATH)))
                    throw new ClientException(ErrorCodes.ERR_INVALID_JWT, "Invalid JWT token signature");
            } else {
                verifierCode = (String) requestBody.getOrDefault(VERIFIER_CODE, "");
                verifierDetails = getParticipant(PARTICIPANT_CODE, verifierCode);
            }
            HttpResponse<String> response = HttpUtils.post(verifierDetails.get(ENDPOINT_URL) + APPLICANT_GET_INFO, JSONUtils.serialize(requestBody),headers(verifierCode));
            return new ResponseEntity<>(response.getBody(), HttpStatus.valueOf(response.getStatus()));
        } catch (Exception e){
            return exceptionHandler(new Response(), e);
        }
    }

    public ResponseEntity<Object> applicantVerify(HttpHeaders header, Map<String, Object> requestBody) throws Exception {
        try {
            OnboardResponse response = new OnboardResponse((String) requestBody.get(PARTICIPANT_CODE), (String) requestBody.get(VERIFIER_CODE));
            String result;
            if (requestBody.containsKey(OTPVERIFICATION)) {
                result = verifyOTP(requestBody);
            } else {
                result = identityVerify(header, requestBody);
            }
            response.setResult(result);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch(Exception e) {
            return exceptionHandler(new Response(), e);
        }
    }

    private String identityVerify(HttpHeaders header, Map<String, Object> requestBody) throws Exception {
        String verifierCode = (String) requestBody.get(VERIFIER_CODE);
        Map<String, Object> verifierDetails = getParticipant(PARTICIPANT_CODE, verifierCode);
        String result;
        HttpResponse<String> httpResp = HttpUtils.post(verifierDetails.get(ENDPOINT_URL) + APPLICANT_VERIFY, JSONUtils.serialize(requestBody),headers(verifierCode));
        if (httpResp.getStatus() == 200) {
            Map<String,Object> payorResp = JSONUtils.deserialize(httpResp.getBody(), Map.class);
            result = (String) payorResp.get(RESULT);
            updateStatus((String) requestBody.get(EMAIL), result);
        } else {
            Response errResp = JSONUtils.deserialize(httpResp.getBody(), Response.class);
            throw new ClientException(errResp.getError().getCode(), errResp.getError().getMessage());
        }

        return result;
    }

    private boolean verifyOTP(ResultSet resultSet, Map<String, Object> otpVerification, String key) throws Exception {
        if (resultSet.getString(key).equals(otpVerification.get(OTP))) {
            return true;
        } else {
            throw new ClientException(StringUtils.capitalize(key.replace("_", " "))  +  " is invalid, please try again!");
        }
    }

    private Map<String,String> headers(String verifierCode) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Map<String,String> headers = new HashMap<>();
        headers.put(AUTHORIZATION,"Bearer "+ jwtUtils.generateAuthToken(privatekey,verifierCode,hcxCode,expiryTime));
        return headers;
    }
}