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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;


@Entity
@Table(
        name = "tracked_entry",
        indexes = { @Index(name = "idx_timestamps", columnList = "timestamp"),
                @Index(name = "idx_store_path_effect", columnList = "store_key,path,store_effect")},
        uniqueConstraints = @UniqueConstraint(
                name = "uq_build_repo_operation_path",
                columnNames = { "tracking_id", "repository_id", "store_effect", "path" }))
public class DbTrackedEntry extends PanacheEntity {

    @Column(
            name = "tracking_id",
            nullable = false,
            columnDefinition = "VARCHAR(128) REFERENCES tracking_report(tracking_id)")
    public String trackingId;

    @Column(name = "repository_id",
            nullable = false,
            columnDefinition = "BIGINT REFERENCES repository(id)")
    public Long repositoryId;

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

    @Column(name = "access_timestamp")
    public LocalDateTime timestamp;

    public DbTrackedEntry() {
    }

    public DbTrackedEntry(
            String trackingId,
            Long repositoryId,
            String path,
            String originUrl,
            DbStoreEffect storeEffect,
            String md5,
            String sha256,
            String sha1,
            Long size,
            LocalDateTime timestamp) {
        this.trackingId = trackingId;
        this.repositoryId = repositoryId;
        this.path = path;
        this.originUrl = originUrl;
        this.storeEffect = storeEffect;
        this.md5 = md5;
        this.sha1 = sha1;
        this.sha256 = sha256;
        this.size = size;
        this.timestamp = timestamp;
    }

    /**
     * Highly-performant entry insert into database without selecting the tracking record first. It is performed only if
     * the tracking report is not sealed.
     *
     * @param trackingId the tracking report ID
     * @return true in case of successful persist; false if a record with the trackingId does not exist or is sealed
     */
    public boolean persistIfActive() {
        return getEntityManager().createNativeQuery("""
            INSERT INTO tracked_entry
                (tracking_id, repository_id, path, origin_url, store_effect, md5, sha1, sha256, size, timestamp)
            SELECT
                r.trackingId, :repositoryId, :path, :originUrl, :storeEffect, :md5, :sha1, :sha256, :size, :timestamp
            FROM tracking_report r
            WHERE r.tracking_id = :trackingId AND r.sealed = false
            ON CONFLICT ON CONSTRAINT uq_build_repo_operation_path DO NOTHING
            """)
            .setParameter("trackingId", this.trackingId)
            .setParameter("repositoryId", this.repositoryId)
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
     * @param trackingId the unique identifier of the report.
     * @param effect the optional {@link DbStoreEffect} to filter by; pass {@code null} to retrieve all.
     * @return a {@link List} of detached {@link DbTrackedEntry} entities.
     */
    public static List<TrackedEntryProjection> findDetachedWithRepo(String trackingId, DbStoreEffect effect) {
        return Panache.getEntityManager()
                .unwrap(Session.class)
                .getSessionFactory()
                .openStatelessSession()
                .createQuery(
                        "SELECT new org.jboss.pnc.tracker.model.TrackedEntryProjection("
                                + "  e.trackingId, m.project, m.name, m.packageType, e.path, e.originUrl,"
                                + " e.storeEffect, e.md5, e.sha1, e.sha256, e.size, e.timestamp" + ") "
                                + "FROM DbTrackedEntry e JOIN DbRepository m ON e.repositoryId = m.id "
                                + "WHERE e.trackingId = :id AND (:effect IS NULL OR e.storeEffect = :effect)",
                        TrackedEntryProjection.class)
                .setParameter("id", trackingId)
                .setParameter("effect", effect) // Hibernate 6 can handle null
                .getResultList();
    }
}
