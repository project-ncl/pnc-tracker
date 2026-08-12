/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Session;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    public DbTrackingReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    public DbRepository repository;

    @Column(name = "path")
    public String path;

    @Column(name = "origin_url")
    public String originUrl;

    @Column(name = "store_effect", columnDefinition = "char(1)")
    public DbStoreEffect storeEffect;

    @Column(name = "md5")
    public String md5;

    @Column(name = "sha1")
    public String sha1;

    @Column(name = "sha256")
    public String sha256;

    @Column(name = "size")
    public Long size;

    @Column(name = "timestamp")
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
                (report_id, repository_id, path, origin_url, store_effect, md5, sha1, sha256, size, timestamp)
            SELECT
                r.reportId, :repositoryId, :path, :originUrl, :storeEffect, :md5, :sha1, :sha256, :size, :timestamp
            FROM tracking_report r
            WHERE r.id = :reportId AND r.state = :reportState
            ON CONFLICT ON CONSTRAINT uq_build_repo_operation_path DO NOTHING
            """)
            .setParameter("reportId", this.report.id)
            .setParameter("reportState", DbTrackingReportState.IN_PROGRESS.getDbCode())
            .setParameter("repositoryId", this.repository.id)
            .setParameter("path", this.path)
            .setParameter("originUrl", this.originUrl)
            .setParameter("storeEffect", this.storeEffect)
            .setParameter("md5", this.md5)
            .setParameter("sha1", this.sha1)
            .setParameter("sha256", this.sha256)
            .setParameter("size", this.size)
            .setParameter("timestamp", this.timestamp)
            .executeUpdate() == 1; // when 1 is returned, it persisted successfully
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
                                + "  m.project, m.name, m.packageType, e.path, e.originUrl,"
                                + " e.storeEffect, e.md5, e.sha1, e.sha256, e.size, e.timestamp" + ") "
                                + "FROM DbTrackedEntry e JOIN DbRepository m ON e.repositoryId = m.id "
                                + "WHERE e.reportId = :id AND (:effect IS NULL OR e.storeEffect = :effect)",
                        TrackedEntryProjection.class)
                .setParameter("id", reportId)
                .setParameter("effect", effect) // Hibernate 6 can handle null
                .getResultList();
    }
}
