/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.service;

import org.jboss.pnc.tracker.exception.ReportNotFoundException;
import org.jboss.pnc.tracker.model.DbTrackingReport;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReportCache {

    /**
     * Retrieves the internal database primary key for a given business tracking ID.
     * <p>
     * Results are cached in memory using the {@code report-id-idx} cache to eliminate
     * redundant database lookups during high-volume tracking operations.
     * </p>
     *
     * @param trackingId the unique business identifier of the tracking report
     * @return the internal database primary key ({@link Long}) of the report
     * @throws ReportNotFoundException if no tracking report exists for the specified {@code trackingId}
     */
    @CacheResult(cacheName = "report-id-idx")
    public Long getReportId(String trackingId) {
        Long reportId = DbTrackingReport.getEntityManager()
                .createQuery("SELECT r.id FROM DbTrackingReport r WHERE r.trackingId = :trackingId", Long.class)
                .setParameter("trackingId", trackingId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (reportId == null) {
            throw new ReportNotFoundException("Tracking report with tracking ID %s was not found.", trackingId);
        }

        return reportId;
    }

    /**
     * Evicts the cached report ID associated with the specified tracking ID.
     * <p>
     * Invalidates the entry in the {@code report-id-idx} cache to ensure subsequent
     * lookups retrieve the updated report state directly from the database.
     *
     * @param trackingId the unique tracking identifier of the report to be evicted from the cache
     */
    @SuppressWarnings("unused")
    @CacheInvalidate(cacheName = "report-id-idx")
    public void evictReportId(String trackingId) {
        // Handled by Quarkus @CacheInvalidate interceptor
    }
}
