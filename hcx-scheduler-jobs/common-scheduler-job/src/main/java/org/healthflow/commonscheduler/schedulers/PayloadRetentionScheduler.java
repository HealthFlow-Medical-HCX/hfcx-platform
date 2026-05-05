package org.healthflow.commonscheduler.schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * P0-8 — Postgres payload-row retention enforcement, per the Egyptian PDPL
 * (Law 151/2020) and Integration Guide §28.5.
 *
 * <p>Two-stage retention: rows older than the retention window are first
 * <b>soft-deleted</b> (status flipped to {@code PURGED}, audit-traceable),
 * then <b>hard-deleted</b> after a configurable grace period (the soft-delete
 * window is the integrator's chance to file a hold-notice for legal /
 * regulatory reasons before the row leaves the database forever).
 *
 * <p>The retention window itself is a <b>policy decision</b> that must come
 * from a human stakeholder. It is exposed here as a required environment
 * variable {@code POSTGRES_RETENTION_DAYS} with no default — startup fails
 * loudly if it is unset rather than silently picking a number. The grace
 * period {@code POSTGRES_RETENTION_GRACE_DAYS} is similarly required.
 *
 * <p>Per the plan §15, a typical value for medical-claims data is 7 years
 * (2557 days), but this is a HealthFlow legal / compliance call — see
 * {@code compliance/DPIA-HCX-Egypt-v1.md}, the §6 "Retention" TODO block.
 *
 * <p>The scheduler emits a structured log line at the end of every run with
 * counts of soft-deleted and hard-deleted rows so observability can alert if
 * the numbers diverge from baseline (e.g. zero soft-deletes for several days
 * may indicate the scheduler is misconfigured).
 */
@Component
public class PayloadRetentionScheduler extends BaseScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PayloadRetentionScheduler.class);
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    private static final String STATUS_PURGED = "PURGED";

    @Value("${postgres.tablename}")
    private String payloadTable;

    /**
     * Retention window in days. The row's {@code lastupdatedon} timestamp must be
     * older than this window before the row becomes eligible for soft-delete.
     * Required — there is no sensible default for medical data retention.
     */
    @Value("${POSTGRES_RETENTION_DAYS}")
    private int retentionDays;

    /**
     * Grace period in days after soft-delete before the row is physically removed.
     * Required.
     */
    @Value("${POSTGRES_RETENTION_GRACE_DAYS}")
    private int gracePeriodDays;

    @PostConstruct
    public void validateConfig() {
        // Defensive: even though @Value is required, surface a clear error if the
        // value is invalid.
        if (retentionDays <= 0) {
            throw new IllegalStateException(
                    "POSTGRES_RETENTION_DAYS must be > 0 (got " + retentionDays
                            + "). Refusing to start; see compliance/DPIA-HCX-Egypt-v1.md §6.");
        }
        if (gracePeriodDays < 0) {
            throw new IllegalStateException(
                    "POSTGRES_RETENTION_GRACE_DAYS must be >= 0 (got " + gracePeriodDays + ").");
        }
        logger.info("PayloadRetentionScheduler configured: retentionDays={} gracePeriodDays={}",
                retentionDays, gracePeriodDays);
    }

    /**
     * Runs daily by default. Soft-deletes first, then hard-deletes anything that
     * has aged past the grace window.
     */
    @Scheduled(fixedDelayString = "${fixedDelay.in.milliseconds.payloadRetention:86400000}")
    public void process() {
        long now = System.currentTimeMillis();
        long retentionThreshold = now - retentionDays * ONE_DAY_MS;
        long graceThreshold = now - (retentionDays + gracePeriodDays) * ONE_DAY_MS;

        int softDeleted = softDelete(retentionThreshold, now);
        int hardDeleted = hardDelete(graceThreshold);

        logger.info("Payload retention pass complete: soft_deleted={} hard_deleted={} retentionDays={} graceDays={}",
                softDeleted, hardDeleted, retentionDays, gracePeriodDays);
    }

    /**
     * Marks payload rows older than the retention threshold as PURGED. Rows that
     * are already PURGED are left untouched; that's how the grace window is
     * preserved (the lastupdatedon of a PURGED row is the soft-delete time).
     *
     * @return the affected-row count from {@link IDatabaseService#executeUpdate}
     */
    private int softDelete(long retentionThreshold, long now) {
        String sql = "UPDATE " + payloadTable
                + " SET status = ?, lastupdatedon = ? "
                + " WHERE status <> ? AND lastupdatedon < ?";
        try {
            return postgreSQLClient.executeUpdate(sql, STATUS_PURGED, now, STATUS_PURGED, retentionThreshold);
        } catch (Exception e) {
            logger.error("Payload retention soft-delete failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Physically deletes rows that have been PURGED for longer than the grace
     * period. The {@code lastupdatedon} on a PURGED row is the soft-delete
     * timestamp set by the previous pass.
     */
    private int hardDelete(long graceThreshold) {
        String sql = "DELETE FROM " + payloadTable
                + " WHERE status = ? AND lastupdatedon < ?";
        try {
            return postgreSQLClient.executeUpdate(sql, STATUS_PURGED, graceThreshold);
        } catch (Exception e) {
            logger.error("Payload retention hard-delete failed: {}", e.getMessage(), e);
            return 0;
        }
    }
}
