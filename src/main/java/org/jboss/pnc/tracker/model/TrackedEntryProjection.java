/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.time.LocalDateTime;

public record TrackedEntryProjection(
    String trackingId,
    String project,
    String name,
    DbPackageType packageType,
    String path,
    String originUrl,
    DbStoreEffect storeEffect,
    String md5,
    String sha1,
    String sha256,
    Long size,
    LocalDateTime timestamp
) {}