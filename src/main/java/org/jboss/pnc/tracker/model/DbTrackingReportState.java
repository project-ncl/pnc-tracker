/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DbTrackingReportState {

    IN_PROGRESS("I"),
    SEALED("S"),
    CORRUPTED("C");

    private final String dbCode;

    // Static Map initialized only once when the class is loaded by JVM
    private static final Map<String, DbTrackingReportState> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(DbTrackingReportState::getDbCode, Function.identity()));

    DbTrackingReportState(String dbCode) {
        this.dbCode = dbCode;
    }

    public String getDbCode() {
        return dbCode;
    }

    public static DbTrackingReportState fromDbCode(String dbCode) {
        DbTrackingReportState state = BY_CODE.get(dbCode);
        if (state == null) {
            throw new IllegalArgumentException("Unknown DB code for TrackingReportState: " + dbCode);
        }
        return state;
    }
}
