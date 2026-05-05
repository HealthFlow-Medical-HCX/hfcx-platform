package org.healthflow.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.experimental.UtilityClass;

import java.util.*;

import static org.healthflow.common.utils.Constants.*;

@UtilityClass
public class PayloadUtils {

    /**
     * Strips the JWE segments that would let the platform decrypt the payload
     * before persisting the request to Postgres for audit / retry. Specifically:
     * the {@code encrypted_key} segment (index 1) and the {@code ciphertext}
     * segment (index 2) are removed, leaving only the protected header, IV, and
     * authentication tag — enough to identify and route the request, not enough
     * to recover plaintext.
     *
     * <p><b>This is the zero-knowledge guarantee codified.</b> Per Decision 14
     * (see {@code docs/reviews/DECISION_14_ZERO_KNOWLEDGE_TRANSPORT.md}) the
     * gateway is a routing fabric, not a decrypting proxy. Integration Guide
     * §26.1 promises "the platform cannot decrypt"; this stripping is what
     * makes the promise true even if the Postgres replica leaks. FHIR
     * validation per §29 is the recipient's responsibility, applied after the
     * recipient decrypts with its private key.
     *
     * <p>Do not "fix" this method to keep the encrypted_key/ciphertext segments
     * without first reversing Decision 14 and updating §26.1 of the integration
     * guide. The plan §13.e originally proposed decrypt-validate-re-encrypt;
     * Decision 14 chose Option A (zero-knowledge) instead.
     */
    public static String removeSensitiveData(Map<String, Object> payload, String apiAction) throws JsonProcessingException {
        if (payload.containsKey(PAYLOAD) && !apiAction.contains(NOTIFICATION_NOTIFY)) {
            List<String> modifiedPayload = new ArrayList<>(Arrays.asList(payload.get(PAYLOAD).toString().split("\\.")));
            // Decision 14: drop encrypted_key and ciphertext to preserve zero-knowledge.
            modifiedPayload.remove(1);
            modifiedPayload.remove(2);
            String[] payloadValues = modifiedPayload.toArray(new String[modifiedPayload.size()]);
            StringBuilder sb = new StringBuilder();
            for (String value : payloadValues) {
                sb.append(value).append(".");
            }
            return sb.deleteCharAt(sb.length() - 1).toString();
        } else {
            return JSONUtils.serialize(removeParticipantDetails(payload));
        }
    }

    public static Map<String,Object> removeParticipantDetails(Map<String,Object> payload){
        Map<String,Object> map = new HashMap<>(payload);
        if (map.containsKey(SENDERDETAILS) && map.containsKey(RECIPIENTDETAILS)) {
            map.remove(SENDERDETAILS);
            map.remove(RECIPIENTDETAILS);
        }
        return map;
    }
}
