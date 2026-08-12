/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.service;

import org.jboss.pnc.tracker.model.DbPackageType;
import org.jboss.pnc.tracker.model.DbRepository;
import org.jboss.pnc.tracker.model.DbStoreEffect;
import org.jboss.pnc.tracker.model.DbTrackedEntry;
import org.jboss.pnc.tracker.model.DbTrackingReport;
import org.jboss.pnc.tracker.model.DbTrackingReportState;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.RepositoryHandle;
import org.jfrog.artifactory.client.model.AqlItem;
import org.jfrog.artifactory.client.aql.FileSpecBuilder;
import org.jfrog.artifactory.client.model.PackageType;
import org.jfrog.artifactory.client.model.Repository;
import org.jfrog.filespecs.FileSpec;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ArtifactoryConnector {

    private static final String BUILD_PROPERTY_PREFIX = "pnc.";

    /** Safety ceiling — no single build should exceed this. */
    private static final int AQL_RESULT_LIMIT = 50000;

    @Inject
    Logger logger;

    @ConfigProperty(name = "tracker.artifactory.pull-data", defaultValue = "false")
    boolean active;

    @ConfigProperty(name = "tracker.artifactory.project")
    Optional<String> artifactoryProject;

    @Inject
    private Artifactory artifactory;

    @Inject
    ReportCache reportCache;

    @Inject
    RepositoryCache repositoryCache;

    /**
     * Fetches the package type of a repository from Artifactory based on its project and name.
     * <p>
     * The repository name in Artifactory is composed as {@code project-name}. This method executes
     * a synchronous network call to Artifactory to retrieve the repository metadata and maps the
     * remote {@link PackageType} to the internal {@link DbPackageType}.
     * </p>
     * <p>
     * This method enforces a fail-fast strategy: if the repository does not exist, lacks settings,
     * or uses an unsupported package type, an exception is thrown to abort tracking.
     * </p>
     *
     * @param project the project identifier of the repository
     * @param name the repository name
     * @return the resolved internal {@link DbPackageType}
     * @throws IllegalStateException if repository metadata or settings cannot be retrieved from Artifactory
     * @throws IllegalArgumentException if the remote package type is unsupported by the internal model
     */
    public DbPackageType fetchPackageType(String project, String name) {
        String repoName = project + "-" + name;

        try {
            RepositoryHandle repositoryHandle = artifactory.repositories().repository(repoName);
            Repository repo = repositoryHandle.get();

            if (repo == null || repo.getRepositorySettings() == null) {
                throw new IllegalStateException("Repository settings not found for repo: " + repoName);
            }

            PackageType artifactoryType = repo.getRepositorySettings().getPackageType();
            if (artifactoryType == null) {
                throw new IllegalStateException("Package type is null in Artifactory response for repo: " + repoName);
            }

            return mapToDbPackageType(artifactoryType, repoName);

        } catch (Exception e) {
            logger.errorf("Failed to fetch or map package type for repo %s: %s", repoName, e.getMessage());
            // Fail fast: Re-throw or wrap in runtime exception to abort tracking
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Could not retrieve metadata for repo: " + repoName, e);
        }
    }

    /**
     * Explicitly maps supported JFrog Artifactory package types to internal DbPackageType enum.
     */
    private DbPackageType mapToDbPackageType(PackageType artifactoryType, String repoName) {
        String typeName = artifactoryType.name().toLowerCase();

        return switch (typeName) {
            case "maven" -> DbPackageType.MAVEN;
            case "npm" -> DbPackageType.NPM;
            case "rpm" -> DbPackageType.RPM;
            case "generic" -> DbPackageType.GENERIC;
            default -> throw new IllegalArgumentException(
                    String.format(
                            "Unsupported package type '%s' for repository '%s'. Tracking aborted.",
                            typeName,
                            repoName));
        };
    }

    /**
     * Queries Artifactory using AQL to fetch all tracked artifact downloads and uploads
     * associated with the specified tracking ID, converting them into {@link DbTrackedEntry} objects.
     *
     * @param trackingId the business tracking identifier (build content ID)
     * @return list of converted {@link DbTrackedEntry} entities ready for batch persistence
     */
    public List<DbTrackedEntry> fetchEntriesForReport(String trackingId) {
        if (!active) {
            logger.debugf("Artifactory integration is disabled. Skipping fetch for tracking ID: %s", trackingId);
            return List.of();
        }

        String trackPropName = BUILD_PROPERTY_PREFIX + trackingId;
        logger.infof("Querying Artifactory AQL for tracking report: %s (property: %s)", trackingId, trackPropName);

        try {
            // Build single AQL FileSpec search query combining build repo and shared repos
            FileSpec spec = new FileSpec();
            spec = new FileSpecBuilder()
                    .item("type", "file")
                    .match("repo", artifactoryProject + "-*")
                    .eq("property.key", trackPropName)
                    .include(
                            "name",
                            "repo",
                            "path",
                            "size",
                            "actual_sha1",
                            "actual_md5",
                            "sha256",
                            // Note that searching for a property also acts as a filter and excludes those
                            // without this property hence the second FileGroup search below to find the
                            // uploads that don't have this property.
                            "@jf.origin.remote.path")
                    .limit(AQL_RESULT_LIMIT)
                    .addToFileSpec(spec);

            spec = new FileSpecBuilder()
                    .item("type", "file")
                    .match("repo", artifactoryProject + "-*-" + trackingId)
                    .eq("property.key", trackPropName)
                    .include("name", "repo", "path", "size", "actual_sha1", "actual_md5", "sha256")
                    // TODO: Handle pagination
                    .limit(AQL_RESULT_LIMIT)
                    .addToFileSpec(spec);

            List<AqlItem> items = artifactory.searches().artifactsByFileSpec(spec);
            logger.debugf("AQL query returned %d items for tracking ID %s", items.size(), trackingId);

            List<DbTrackedEntry> entries = new ArrayList<>(items.size());

            // verify the artifactory project is configured
            artifactoryProject.orElseThrow(() ->
                new IllegalStateException("tracker.artifactory.project must be set when pull-data is enabled")
            );
            DbTrackingReport reportRef = new DbTrackingReport(reportCache.getReportId(trackingId), trackingId);
            Map<String, DbRepository> repoMap = new HashMap<>();
            String project = artifactoryProject.get();
            for (AqlItem item : items) {
                try {
                    DbTrackedEntry entry = convertAqlItemToEntity(item, reportRef, project, repoMap, trackPropName);
                    entries.add(entry);
                } catch (Exception e) {
                    logger.warnf("Failed to convert AqlItem (%s/%s): %s", item.getRepo(), item.getName(), e.getMessage());
                }
            }

            logger.infof("Successfully fetched and converted %d entries for tracking ID: %s", entries.size(), trackingId);
            return entries;

        } catch (Exception e) {
            logger.errorf(e, "Failed to fetch entries from Artifactory for tracking ID: %s", trackingId);
            throw new IllegalStateException("Failed to retrieve tracking report from Artifactory for: " + trackingId, e);
        }
    }

    /**
     * Converts a single {@link AqlItem} returned by Artifactory AQL into a {@link DbTrackedEntry}.
     */
    private DbTrackedEntry convertAqlItemToEntity(
            AqlItem item,
            DbTrackingReport reportRef,
            String project,
            Map<String, DbRepository> repoMap,
            String trackPropName) {
        String repoKey = item.getRepo();

        // Strip project prefix from repoKey to get clean repository name
        // e.g., "pnc-mvn-build-123" -> "mvn-build-123"
        String repoName = repoKey;
        if (repoKey.startsWith(project + "-")) {
            repoName = repoKey.substring(project.length() + 1);
        }

        // Classify effect: If repoKey contains build/tracking ID -> UPLOAD, otherwise -> DOWNLOAD
        DbStoreEffect storeEffect = repoKey.contains(reportRef.trackingId) ? DbStoreEffect.UPLOAD : DbStoreEffect.DOWNLOAD;

        // Construct normalized relative path
        String path = normalizePath(item.getPath(), item.getName());

        // Get or automatically resolve/create DB repository ID via cache
        DbRepository repositoryRef = repoMap.computeIfAbsent(
                repoName,
                name -> new DbRepository(repositoryCache.getOrCreateRepositoryId(project, name), project, name));

        DbTrackedEntry entry = new DbTrackedEntry();
        entry.report = reportRef;
        entry.repository = repositoryRef;
        entry.path = path;
        entry.originUrl = extractOriginUrl(item);
        entry.storeEffect = storeEffect;
        entry.md5 = item.getActualMd5();
        entry.sha1 = item.getActualSha1();
        entry.sha256 = item.getSha256();
        entry.size = item.getSize();
        entry.timestamp = extractTimestamp(item, trackPropName);

        return entry;
    }

    /**
     * Extracts and parses the timestamp from the property matching the specified tracking property name.
     * <p>
     * If the property is absent or cannot be parsed, the method falls back to the creation timestamp of the item. If
     * the creation timestamp is also missing, the current system time is used as a final fallback.
     * </p>
     *
     * @param item the {@link AqlItem} containing the metadata and properties
     * @param trackPropName the name of the tracking property to look for
     * @return the resolved {@link LocalDateTime}
     */
    private LocalDateTime extractTimestamp(AqlItem item, String trackPropName) {
        if (item.getProperties() == null) {
            return fallbackTimestamp(item);
        }

        String rawValue = item.getProperties().stream()
                .filter(p -> trackPropName.equalsIgnoreCase(p.getkey()))
                .map(AqlItem.Property::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);

        if (rawValue == null) {
            return fallbackTimestamp(item);
        }

        try {
            // Attempt 1: Epoch timestamp (milliseconds or seconds)
            if (rawValue.matches("\\d+")) {
                long epoch = Long.parseLong(rawValue);
                if (epoch < 10_000_000_000L) {
                    epoch *= 1000; // Convert seconds to milliseconds
                }
                return Instant.ofEpochMilli(epoch)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            // Attempt 2: ISO-8601 formatted date-time
            return LocalDateTime.parse(rawValue, DateTimeFormatter.ISO_DATE_TIME);

        } catch (Exception e) {
            logger.warnf("Failed to parse timestamp property '%s' (value: '%s'): %s", trackPropName, rawValue, e.getMessage());
            return fallbackTimestamp(item);
        }
    }

    /**
     * Fallback timestamp strategy if property is absent or unparseable.
     */
    private LocalDateTime fallbackTimestamp(AqlItem item) {
        if (item.getCreated() != null) { //
            return item.getCreated().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }
        return LocalDateTime.now();
    }

    /**
     * Normalizes directory path and filename returned by AQL.
     */
    private String normalizePath(String rawDirectory, String fileName) {
        String directory = (rawDirectory != null && rawDirectory.startsWith("/"))
                ? rawDirectory.substring(1)
                : rawDirectory;

        if (directory == null || directory.isEmpty() || ".".equals(directory)) {
            return fileName;
        }
        return directory + "/" + fileName;
    }

    /**
     * Extracts 'jf.origin.remote.path' property embedded in AqlItem.
     *
     * @param item the AQL item to read the origin url from
     * @return the extracted property value or {@code null} in case the property is absent or empty
     */
    private String extractOriginUrl(AqlItem item) {
        if (item.getProperties() == null) {
            return null;
        }
        return item.getProperties().stream()
                .filter(p -> "jf.origin.remote.path".equals(p.getkey()))
                .map(AqlItem.Property::getValue)
                .filter(v -> v != null && !v.isEmpty())
                .findFirst()
                .orElse(null);
    }

    public boolean isActive() {
        return active;
    }

}
