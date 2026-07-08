/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.service;

import org.jboss.pnc.tracker.exception.ReportDataConflictException;
import org.jboss.pnc.tracker.exception.ReportInvalidStateException;
import org.jboss.pnc.tracker.exception.ReportNotFoundException;
import org.jboss.pnc.tracker.model.DbTrackedEntry;
import org.jboss.pnc.tracker.model.DbTrackingReport;
import org.jboss.pnc.tracker.model.DbStoreEffect;
import org.jboss.pnc.tracker.model.TrackedEntryProjection;
import org.jboss.pnc.tracker.model.DbTrackingReportState;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Service responsible for orchestrating tracking reports and their associated entries.
 * <p>
 * This service acts as the core business logic layer for the tracking functionality.
 * Following a decoupled architectural pattern, it manages the relationship between
 * {@code DbTrackingReport} and {@code DbTrackedEntry} dynamically using tracking identifiers
 * rather than hardcoded bi-directional JPA object relationships. This prevents common
 * ORM pitfalls such as {@code LazyInitializationException} and enhances overall performance.
 * </p>
 *
 * @see DbTrackingReport
 * @see DbTrackedEntry
 */
@ApplicationScoped
public class ReportService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    RepositoryCache repositoryCache;

    /**
     * Retrieves a tracking report by its unique key.
     * <p>
     * This method acts as the primary internal getter for business logic. If the report
     * is not found in the database, it throws a domain-specific {@link ReportNotFoundException}.
     * This exception is subsequently intercepted by the REST layer's exception mappers
     * to return a standard HTTP 404 Not Found status to the client.
     * </p>
     *
     * @param key the unique tracking key of the report to retrieve
     * @return the matching {@link DbTrackingReport} instance
     * @throws ReportNotFoundException if no tracking report exists with the specified key
     */
    public DbTrackingReport getReport(String trackingId) {
        DbTrackingReport trackingReport = DbTrackingReport.findByKey(trackingId);
        if (trackingReport == null) {
            throw new ReportNotFoundException("Tracking report with ID %s was not found.", trackingId);
        }
        return trackingReport;
    }

    /**
     * Retrieves entries for a specified tracking report, optionally filtered by {@link DbStoreEffect}.
     * <p>
     * This method verifies the existence of the report before fetching the entries. If the report does not exist, a
     * {@link ReportNotFoundException} is thrown. It also checks the report status and only allows reading entries in a
     * sealed report. In case of any other status a {@link ReportInvalidStateException} is thrown.
     * </p>
     *
     * @param trackingId the unique identifier of the report.
     * @param effect the optional {@link DbStoreEffect} to filter by; pass {@code null} to retrieve all entries.
     * @return a {@link List} of {@link TrackedEntryProjection} entities associated with the report.
     * @throws ReportNotFoundException if no report is found for the given {@code trackingId}.
     * @throws ReportInvalidStateException if the report state is not {@link DbTrackingReportState#SEALED}
     */
    public List<TrackedEntryProjection> findEntries(String trackingId, DbStoreEffect effect) {
        // 1. Check if the report exists to ensure 404 behaviour if missing
        DbTrackingReport report = getReport(trackingId);

        // Enforce state-based access control
        if (report.state != DbTrackingReportState.SEALED) {
            throw new ReportInvalidStateException(
                    "Cannot read entries for report: %s because its state is: %s.",
                    trackingId,
                    report.state);
        }
        // 2. Fetch the data using the optimized stateless approach
        return DbTrackedEntry.findDetachedWithRepo(trackingId, effect);
    }

    /**
     * Seals an existing tracking report to prevent any further entries from being added.
     * <p>
     * This operation is idempotent. If the report is already in the {@link DbTrackingReportState#SEALED}
     * state, the method logs a debug message and returns the report immediately without
     * modifying the database. Otherwise, it transitions the report's state to {@code SEALED}
     * and persists the changes.
     * </p>
     *
     * @param trackingId the unique identifier of the tracking report to seal
     * @return the sealed {@link DbTrackingReport} instance
     * @throws ReportNotFoundException if no tracking report exists with the specified key (thrown via {@link #getReport(String)})
     */
    public DbTrackingReport sealReport(String trackingId) {
        DbTrackingReport trackingReport = getReport(trackingId);

        if (trackingReport.state == DbTrackingReportState.SEALED) {
            logger.debug("Tracking report: {} already sealed! Returning sealed report.", trackingId);
            return trackingReport;
        }
        if (trackingReport.state == DbTrackingReportState.CORRUPTED) {
            throw new ReportDataConflictException(
                    "Tracking report: {} is CORRUPTED, so it cannot be sealed!", trackingId);
        }
        doSealReport(trackingReport);

        return trackingReport;
    }

    @Transactional
    public void doSealReport(DbTrackingReport trackingReport) {
        logger.debug("Sealing report for: {}", trackingReport.trackingId);
        trackingReport.state = DbTrackingReportState.SEALED;
        DbTrackingReport.persist(trackingReport);
    }

    /**
     * Records a tracking entry (either an upload or a download) for the specified tracking report.
     * <p>
     * It executes a fast-path insertion within the active transaction and, if unsuccessful,
     * initiates a deep validation path to diagnose the underlying report state.
     * </p>
     *
     * @param entry the transient tracking entry to persist
     * @param project project in Artifactory
     * @param repoName repository name in Artifactory
     * @throws ReportNotFoundException if the report does not exist
     * @throws ReportInvalidStateException if the report is sealed or corrupted
     */
    @Transactional
    public void trackEntry(DbTrackedEntry entry, String project, String repoName) {
        Long repoId = repositoryCache.getOrCreateRepositoryId(project, repoName);
        entry.repositoryId = repoId;

        // ultra-fast conditional persist
        boolean success = entry.persistIfActive();

        // if it fails, we analyse the reason (slow path)
        if (!success) {
            validateReportStatus(entry);
        }
    }

    /**
     * Validates the report status and throws domain-specific service exceptions.
     */
    private void validateReportStatus(DbTrackedEntry entry) {
        DbTrackingReport report = getReport(entry.trackingId);
        if (report == null) {
            throw new ReportNotFoundException("Tracking report not found: %s", entry.trackingId);
        }

        if (report.state == DbTrackingReportState.SEALED) {
            throw new ReportInvalidStateException("Tracking report %s is sealed.", entry.trackingId);
        }

        if (report.state == DbTrackingReportState.CORRUPTED) {
            throw new ReportInvalidStateException("Tracking report %s is corrupted.", entry.trackingId);
        }

        if (report.state == DbTrackingReportState.IN_PROGRESS) {
            logger.debug(
                    "Entry for path {} in repository {} already exists in report {}. Skipping duplicate.",
                    entry.path,
                    entry.repositoryId,
                    entry.trackingId);
        }
    }

    /**
     * Initialises a new tracking report.
     * <p>
     * If a report with the given ID does not exist, a new one is created in {@link DbTrackingReportState#IN_PROGRESS}.
     * If it exists and is in {@link DbTrackingReportState#IN_PROGRESS} with no entries, the operation is skipped.
     * If it exists but contains entries, or is in a terminal state (SEALED, CORRUPTED), a conflict exception is thrown.
     * </p>
     *
     * @param trackingId the unique identifier of the report to initialise.
     * @throws ReportDataConflictException if the report exists with entries or is in an invalid state.
     */
    @Transactional
    public void initReport(String trackingId) {
        DbTrackingReport existingReport = DbTrackingReport.findByKey(trackingId);

        if (existingReport == null) {
            DbTrackingReport newReport = new DbTrackingReport();
            newReport.trackingId = trackingId;
            newReport.state = DbTrackingReportState.IN_PROGRESS;
            newReport.persist();
            logger.info("New tracking report {} initialized.", trackingId);
        } else {
            // Handle existing report logic
            if (existingReport.state != DbTrackingReportState.IN_PROGRESS) {
                throw new ReportDataConflictException("Report %s is in terminal state: %s",
                        trackingId, existingReport.state);
            }

            if (DbTrackingReport.hasEntries(trackingId)) {
                throw new ReportDataConflictException("Report %s is already active and contains entries.",
                        trackingId);
            }

            logger.debug("Report {} already exists and is empty. Skipping initialization.", trackingId);
        }
    }

    /**
     * Retrieves a list of tracking report identifiers, optionally filtered by state.
     * <p>
     * If the provided {@code state} is {@code null}, all tracking identifiers in the system are returned. Otherwise,
     * only identifiers for reports matching the specified state are retrieved.
     * </p>
     *
     * @param state the {@link DbTrackingReportState} to filter by, or {@code null} to retrieve all available identifiers.
     * @return a {@link List} of tracking identifier strings.
     */
    public List<String> getTrackingIds(DbTrackingReportState state) {
        if (state == null) {
            return DbTrackingReport.findAllKeys();
        } else {
            return DbTrackingReport.findKeysByType(state);
        }
    }

    /**
     * Deletes a tracking report and all its associated entries.
     * <p>
     * This operation is idempotent: if the report does not exist, the method completes
     * successfully without performing any action, effectively fulfilling the intent
     * of ensuring the report is cleared.
     * </p>
     *
     * @param trackingId the unique identifier of the report to clear
     */
    @Transactional
    public void clearReport(String trackingId) {
        DbTrackingReport report = getReport(trackingId);

        if (report == null) {
            logger.debug("Attempted to clear non-existent tracking report: %s. Skipping.", trackingId);
        } else {
            report.delete();
            logger.info("Report %s and all its entries have been cleared.", trackingId);
        }
    }

}
