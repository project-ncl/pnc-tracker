package org.jboss.pnc.tracker.model;

import java.time.LocalDateTime;

public record TrackedEntryProjection(
    String trackingId,
    String project,
    String name,
    DbPackageType packageType,
    String path,
    String originUrl,
    StoreEffect storeEffect,
    String md5,
    String sha1,
    String sha256,
    Long size,
    LocalDateTime timestamp
) {}