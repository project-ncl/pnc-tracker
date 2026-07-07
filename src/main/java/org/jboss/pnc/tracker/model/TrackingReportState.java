/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TrackingReportState {

    IN_PROGRESS("I"),
    SEALED("S"),
    CORRUPTED("C");

    private final String dbCode;

    // Static Map initialized only once when the class is loaded by JVM
    private static final Map<String, TrackingReportState> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(TrackingReportState::getDbCode, Function.identity()));

    TrackingReportState(String dbCode) {
        this.dbCode = dbCode;
    }

    public String getDbCode() {
        return dbCode;
    }

    public static TrackingReportState fromDbCode(String dbCode) {
        TrackingReportState state = BY_CODE.get(dbCode);
        if (state == null) {
            throw new IllegalArgumentException("Unknown DB code for TrackingReportState: " + dbCode);
        }
        return state;
    }
}
