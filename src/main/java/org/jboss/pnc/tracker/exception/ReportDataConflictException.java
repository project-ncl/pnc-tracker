/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.exception;

/**
 * Thrown when an entry cannot be persisted due to a database data conflict
 * while the report is active.
 */
public class ReportDataConflictException extends TrackerException {

    public ReportDataConflictException(String message, Object... params) {
        super(message, params);
    }

    public ReportDataConflictException(String message, Throwable cause, Object... params) {
        super(message, cause, params);
    }

    public ReportDataConflictException(int status, String message, Object... params) {
        super(status, message, params);
    }

    public ReportDataConflictException(int status, String message, Throwable cause, Object... params) {
        super(status, message, cause, params);
    }

}
