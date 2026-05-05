package org.healthflow.commonscheduler.schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.healthflow.common.service.RegistryService;
import org.healthflow.common.utils.Constants;
import org.healthflow.common.utils.NotificationUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P0-2.f — emits proactive notifications when participant certificates are
 * approaching expiry. The existing {@link ParticipantValidationScheduler}
 * already handles certs that have ALREADY expired; this scheduler adds the
 * 30 / 14 / 7 / 1-day warnings the integration guide §28.3 promises.
 *
 * <p>Strategy: four narrow queries, one per warning band. A band is a
 * 24-hour window so each scheduler run touches each cert in at most one
 * band, and a daily run yields exactly four notifications per cert as it
 * approaches expiry (30 / 14 / 7 / 1 days out). The narrow bands also
 * avoid spamming integrators with the same warning multiple days in a row.
 *
 * <p>Reads from the registry (the same {@link RegistryService} the rest of
 * the platform uses); emits notifications via the existing notification
 * Kafka topic — the notification-job downstream picks them up and dispatches.
 */
@Component
public class CertExpiryWarningScheduler extends BaseScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CertExpiryWarningScheduler.class);
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    /** Warning bands in days. Each band fires once per cert as it approaches expiry. */
    private static final int[] WARNING_DAYS = {30, 14, 7, 1};

    @Autowired
    private RegistryService registryService;

    @Value("${topicCode.encryptionCertExpiringSoon:notif-encryption-cert-expiring-soon}")
    private String encryptionTopicCode;

    @Value("${topicCode.signingCertExpiringSoon:notif-signing-cert-expiring-soon}")
    private String signingTopicCode;

    @Value("${kafka.topic.notification}")
    private String notifyTopic;

    @Value("${hcx.participantCode}")
    private String hcxParticipantCode;

    @Value("${hcx.privateKey}")
    private String hcxPrivateKey;

    @Value("${notification.expiry}")
    private int notificationExpiry;

    /**
     * Runs daily (24h after the previous run). The fixed-delay schedule
     * guarantees idempotent within-band behaviour: each cert is matched by
     * at most one band per scheduler run.
     */
    @Scheduled(fixedDelayString = "${fixedDelay.in.milliseconds.certExpiryWarning:86400000}")
    public void process() throws Exception {
        logger.info("Cert-expiry warning scheduler started");
        for (int days : WARNING_DAYS) {
            warnForBand("encryption_cert_expiry", encryptionTopicCode, days);
            warnForBand("signing_cert_path_expiry", signingTopicCode, days);
        }
        logger.info("Cert-expiry warning scheduler finished");
    }

    /**
     * Query the registry for participants whose given expiry field falls into
     * the (now + (days-1)*24h, now + days*24h] window, then emit one
     * notification listing all matches.
     */
    private void warnForBand(String expiryField, String topicCode, int days) {
        long now = System.currentTimeMillis();
        long lower = now + (days - 1) * ONE_DAY_MS;
        long upper = now + days * ONE_DAY_MS;

        String filter = "{ \"filters\": { \"" + expiryField + "\": { \">\": " + lower
                + ", \"<=\": " + upper + " } } }";

        List<Map<String, Object>> participants;
        try {
            participants = registryService.getDetails(filter);
        } catch (Exception e) {
            logger.warn("Cert-expiry scheduler — registry query failed for {} band {} days: {}",
                    expiryField, days, e.getMessage());
            return;
        }
        if (participants == null || participants.isEmpty()) {
            return;
        }

        List<String> codes = participants.stream()
                .map(p -> String.valueOf(p.get(Constants.PARTICIPANT_CODE)))
                .collect(Collectors.toList());

        logger.info("Cert-expiry warning: {} participants have {} expiring in {} days: {}",
                codes.size(), expiryField, days, codes);

        try {
            Map<String, Object> notification = NotificationUtils.getNotification(topicCode);
            String template = notification == null
                    ? expiryField + " expires in " + days + " days"
                    : String.valueOf(notification.get(Constants.MESSAGE));
            String message = template
                    .replace("{days}", String.valueOf(days))
                    .replace("{field}", expiryField);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MILLISECOND, notificationExpiry);

            String notifyEvent = eventGenerator.createNotifyEvent(
                    topicCode,
                    hcxParticipantCode,
                    Constants.PARTICIPANT_CODE,
                    new ArrayList<>(codes),
                    cal.getTime().toInstant().toEpochMilli(),
                    message,
                    hcxPrivateKey);
            kafkaClient.send(notifyTopic, hcxParticipantCode, notifyEvent);
        } catch (Exception e) {
            logger.error("Cert-expiry scheduler — failed to emit notification for {} band {}: {}",
                    expiryField, days, e.getMessage(), e);
        }
    }
}
