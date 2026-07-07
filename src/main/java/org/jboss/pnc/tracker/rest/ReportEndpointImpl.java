/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.rest;

import org.jboss.pnc.api.dto.RepositoryId;
import org.jboss.pnc.api.tracker.dto.PackageType;
import org.jboss.pnc.api.tracker.dto.TrackDownloadRequest;
import org.jboss.pnc.api.tracker.dto.TrackUploadRequest;
import org.jboss.pnc.api.tracker.dto.TrackedArtifact;
import org.jboss.pnc.api.tracker.dto.TrackedEntry;
import org.jboss.pnc.api.tracker.dto.TrackingReport;
import org.jboss.pnc.api.tracker.rest.ReportEndpoint;
import org.jboss.pnc.tracker.model.DbPackageType;
import org.jboss.pnc.tracker.model.DbTrackedEntry;
import org.jboss.pnc.tracker.model.DbTrackingReport;
import org.jboss.pnc.tracker.model.StoreEffect;
import org.jboss.pnc.tracker.model.TrackedEntryProjection;
import org.jboss.pnc.tracker.model.TrackingReportState;
import org.jboss.pnc.tracker.service.ReportService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Tag(name = "Tracking Report Access", description = "Manages tracking reports.")
@ApplicationScoped
public class ReportEndpointImpl implements ReportEndpoint {

    @Inject
    ReportService reportService;

    @Override
    public List<String> getAllIds(String strState) {
        TrackingReportState state = getRequiredState(strState);
        List<String> ids = reportService.getTrackingIds(state);
        return ids;
    }

    private TrackingReportState getRequiredState(String strState) {
        TrackingReportState state = null;

        if (TrackingReportState.IN_PROGRESS.name().equalsIgnoreCase(strState)) {
            state = TrackingReportState.IN_PROGRESS;
        }
        if (TrackingReportState.SEALED.name().equalsIgnoreCase(strState)) {
            state = TrackingReportState.SEALED;
        }
        if (TrackingReportState.CORRUPTED.name().equalsIgnoreCase(strState)) {
            state = TrackingReportState.CORRUPTED;
        }

        if (state == null && !"all".equalsIgnoreCase(strState)) {
            throw new IllegalArgumentException("Unknown report state " + strState);
        }

        return state;
    }

    @Override
    public void initReport(final String trackingId) {
        reportService.initReport(trackingId);
    }

    @Override
    public void trackUpload(String trackingId, TrackUploadRequest request) {
        DbTrackedEntry entry = mapToEntity(trackingId, request);
        entry.storeEffect = StoreEffect.UPLOAD;

        RepositoryId repoId = request.getRepoId();
        reportService.trackEntry(entry, repoId.getProject(), repoId.getName());
    }

    @Override
    public void trackDownload(String trackingId, TrackDownloadRequest request) {
        DbTrackedEntry entry = mapToEntity(trackingId, request);
        entry.originUrl = request.getOriginUrl();
        entry.storeEffect = StoreEffect.DOWNLOAD;

        RepositoryId repoId = request.getRepoId();
        reportService.trackEntry(entry, repoId.getProject(), repoId.getName());
    }

    /**
     * Converts a tracked artifact to the DB entity. The tracking report link is NOT set neither are the
     * storeEffect-specific fields as part of this.
     *
     * @param request the request
     * @return converted entity without the parent
     */
    private DbTrackedEntry mapToEntity(String trackingId, TrackedArtifact request) {
        DbTrackedEntry entry = new DbTrackedEntry();
        entry.trackingId = trackingId;
        entry.path = request.getPath();
        entry.md5 = request.getMd5();
        entry.sha1 = request.getSha1();
        entry.sha256 = request.getSha256();
        entry.size = request.getSize();
        entry.timestamp = LocalDateTime.now();
        return entry;
    }

    @Override
    public void sealReport(String trackingId) {
        reportService.sealReport(trackingId);
    }

    @Override
    public TrackingReport getReport(String trackingId) {
        DbTrackingReport report = reportService.getReport(trackingId);

        // State check
        if (report.state != TrackingReportState.SEALED) {
            throw new WebApplicationException("Report is not sealed", Response.Status.CONFLICT);
        }
        List<TrackedEntryProjection> entries = reportService.findEntries(trackingId, null);
        return buildDto(trackingId, entries);
    }

    private TrackingReport buildDto(String trackingId, List<TrackedEntryProjection> entries) {
        TrackingReport dto = TrackingReport.builder()
                .trackingID(trackingId)
                .uploads(entries.stream()
                        .filter(e -> e.storeEffect() == StoreEffect.UPLOAD)
                        .map(this::toEntryDto)
                        .collect(Collectors.toSet()))
                .downloads(entries.stream()
                        .filter(e -> e.storeEffect() == StoreEffect.DOWNLOAD)
                        .map(this::toEntryDto)
                        .collect(Collectors.toSet()))
                .build();

        return dto;
    }

    private TrackedEntry toEntryDto(TrackedEntryProjection p) {
        PackageType packageType = mapPackageType(p.packageType());
        RepositoryId repoId = RepositoryId.builder()
                .project(p.project())
                .name(p.name())
                .packageType(packageType)
                .build();
        return TrackedEntry.builder()
                .repoId(repoId)
                .path(p.path())
                .md5(p.md5())
                .sha1(p.sha1())
                .sha256(p.sha256())
                .size(p.size())
                .timestamp(toLong(p.timestamp()))
                .build();
    }

    private PackageType mapPackageType(DbPackageType dbType) {
        if (dbType == null) {
            return null;
        }
        return switch (dbType) {
            case MAVEN -> PackageType.MAVEN;
            case NPM -> PackageType.NPM;
            //case RPM -> PackageType.RPM;
            case GENERIC -> PackageType.GENERIC;
            default -> throw new IllegalArgumentException("PackageType doesn't have matching value for: " + dbType);
        };
    }

    private Long toLong(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Override
    public List<String> getUploadPaths(String trackingId) {
        DbTrackingReport report = reportService.getReport(trackingId);

        // State check
        if (report.state != TrackingReportState.SEALED) {
            throw new WebApplicationException("Report is not sealed", Response.Status.CONFLICT);
        }

        // Fetch detached entries and project only the 'path' field
        return reportService.findEntries(trackingId, StoreEffect.UPLOAD)
                .stream()
                .map(entry -> entry.path())
                .toList();
    }

    @Override
    public void clearReport(final String trackingId) {
        reportService.clearReport(trackingId);
    }

}