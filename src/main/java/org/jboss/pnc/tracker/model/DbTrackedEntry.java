/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Session;
import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;


@Entity
@Table(
        name = "tracked_entry",
        indexes = { @Index(name = "idx_timestamps", columnList = "timestamp"),
                @Index(name = "idx_store_path_effect", columnList = "repository_id,path,store_effect")},
        uniqueConstraints = @UniqueConstraint(
                name = "uq_build_repo_operation_path",
                columnNames = { "report_id", "repository_id", "store_effect", "path" }))
public class DbTrackedEntry extends PanacheEntity {

    private static final Logger logger = Logger.getLogger(DbTrackedEntry.class);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    public DbTrackingReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    public DbRepository repository;

    @Column(name = "path", nullable = false)
    public String path;

    @Column(name = "origin_url", length = 2048)
    public String originUrl;

    @Column(name = "local_url", length = 2048, nullable = false)
    public String localUrl;

    @Column(name = "store_effect", columnDefinition = "char(1)", nullable = false)
    public DbStoreEffect storeEffect;

    @Column(name = "md5", nullable = false)
    public String md5;

    @Column(name = "sha1", nullable = false)
    public String sha1;

    @Column(name = "sha256", nullable = false)
    public String sha256;

    @Column(name = "size", nullable = false)
    public Long size;

    @Column(name = "timestamp", nullable = false)
    public LocalDateTime timestamp;

    public DbTrackedEntry() {
    }

    /**
     * Highly-performant entry insert into database without selecting the tracking record first. It is performed only if
     * the tracking report is not sealed.
     *
     * @param reportId the tracking report ID
     * @return true in case of successful persist; false if a record with the trackingId does not exist or is sealed
     */
    public boolean persistIfActive() {
        return getEntityManager().createNativeQuery("""
            INSERT INTO tracked_entry
                (id, report_id, repository_id, path, origin_url, local_url, store_effect, md5, sha1, sha256, size, timestamp)
            SELECT
                nextval('tracked_entry_SEQ'), r.id, :repositoryId, :path, :originUrl, :localUrl, :storeEffect, :md5, :sha1, :sha256, :size, :timestamp
            FROM tracking_report r
            WHERE r.id = :reportId AND r.state = :reportState
            ON CONFLICT ON CONSTRAINT uq_build_repo_operation_path DO NOTHING
            """)
            .setParameter("reportId", this.report.id)
            .setParameter("reportState", DbTrackingReportState.IN_PROGRESS.getDbCode())
            .setParameter("repositoryId", this.repository.id)
            .setParameter("path", this.path)
            .setParameter("originUrl", this.originUrl)
            .setParameter("localUrl", this.localUrl)
            .setParameter("storeEffect", this.storeEffect.getDbCode())
            .setParameter("md5", this.md5)
            .setParameter("sha1", this.sha1)
            .setParameter("sha256", this.sha256)
            .setParameter("size", this.size)
            .setParameter("timestamp", this.timestamp)
            .executeUpdate() == 1; // when 1 is returned, it persisted successfully
    }

    /**
     * Efficiently persists a list of {@link DbTrackedEntry} records into the database using direct JDBC batch
     * processing.
     * <p>
     * This method bypasses the Hibernate Persistence Context to prevent memory bloat when inserting large volumes of
     * data. Duplicate records violating the {@code uq_build_repo_operation_path} constraint are silently skipped.
     * <p>
     * <b>Note:</b> Unlike {@code persistIfActive()}, this method does not check the status of the associated report. It
     * assumes the caller has already verified that the report is in a valid state (e.g., {@code IN_PROGRESS}) prior to
     * invocation.
     *
     * @param entries the list of tracked entries to persist; can be {@code null} or empty
     * @return the total count of newly inserted records (excluding skipped duplicates)
     */
    public static int persistBatch(List<DbTrackedEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        return getEntityManager().unwrap(Session.class).doReturningWork(connection -> {
            String sql = """
                INSERT INTO tracked_entry
                    (id, report_id, repository_id, path, origin_url, local_url, store_effect, md5, sha1, sha256, size, timestamp)
                VALUES
                    (nextval('tracked_entry_SEQ'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uq_build_repo_operation_path DO NOTHING
                """;

            int totalInserted = 0;
            int totalProcessed = 0;

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int batchCount = 0;
                int batchStartIndex = 0;

                for (int i = 0; i < entries.size(); i++) {
                    DbTrackedEntry entry = entries.get(i);
                    ps.setLong(1, entry.report.id);
                    ps.setLong(2, entry.repository.id);
                    ps.setString(3, entry.path);
                    ps.setString(4, entry.originUrl);
                    ps.setString(5, entry.localUrl);
                    ps.setString(6, entry.storeEffect.getDbCode());
                    ps.setString(7, entry.md5);
                    ps.setString(8, entry.sha1);
                    ps.setString(9, entry.sha256);
                    ps.setLong(10, entry.size);
                    ps.setTimestamp(11, Timestamp.valueOf(entry.timestamp));

                    ps.addBatch();
                    batchCount++;

                    // Batching by 1000 records
                    if (batchCount % 1000 == 0 || i == entries.size() - 1) {
                        int batchStart = totalProcessed + 1;
                        totalProcessed += batchCount;
                        logger.debugf(
                                "Executing insert batch %d - %d / %d records...",
                                batchStart,
                                totalProcessed,
                                entries.size());

                        int[] results = ps.executeBatch();


                        // Evaluating the rows in the batch
                        for (int j = 0; j < results.length; j++) {
                            DbTrackedEntry batchEntry = entries.get(batchStartIndex + j);
                            int status = results[j];

                            if (status == 1 || status == Statement.SUCCESS_NO_INFO) {
                                totalInserted++;
                            } else if (status == 0) {
                                logger.debugf("Skipped duplicate entry: path=%s, repositoryId=%d",
                                        batchEntry.path, batchEntry.repository.id);
                            }
                        }

                        batchStartIndex = i + 1;
                        batchCount = 0;
                    }
                }
            }
            return totalInserted;
        });
    }

    /**
     * Retrieves entries for a given report as detached entities using a stateless session. Optionally filters by
     * {@link DbStoreEffect}.
     * <p>
     * This bypasses the Hibernate Persistence Context, making it efficient for read-only access to large volumes of
     * data.
     * </p>
     * <p>
     * <b>Warning:</b> These entities are detached and cannot be used for updates or persists.
     * </p>
     *
     * @param reportId the unique identifier of the report.
     * @param effect the optional {@link DbStoreEffect} to filter by; pass {@code null} to retrieve all.
     * @return a {@link List} of detached {@link DbTrackedEntry} entities.
     */
    public static List<TrackedEntryProjection> findDetachedWithRepo(Long reportId, DbStoreEffect effect) {
        return Panache.getEntityManager()
                .unwrap(Session.class)
                .getSessionFactory()
                .openStatelessSession()
                .createQuery(
                        "SELECT new org.jboss.pnc.tracker.model.TrackedEntryProjection("
                                + "  m.project, m.name, m.packageType, e.path, e.originUrl, e.localUrl,"
                                + "  e.storeEffect, e.md5, e.sha1, e.sha256, e.size, e.timestamp) "
                                + "FROM DbTrackedEntry e JOIN e.repository m "
                                + "WHERE e.report.id = :id AND (:effect IS NULL OR e.storeEffect = :effect)",
                        TrackedEntryProjection.class)
                .setParameter("id", reportId)
                .setParameter("effect", effect) // Hibernate 6 can handle null
                .getResultList();
    }
}
