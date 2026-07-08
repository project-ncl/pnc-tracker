/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.exception;

/**
 * Thrown when an operation fails because the tracking report is in an invalid state
 * (e.g., SEALED or CORRUPTED).
 */
public class ReportInvalidStateException extends TrackerException {

    public ReportInvalidStateException(String message, Object... params) {
        super(message, params);
    }

    public ReportInvalidStateException(String message, Throwable cause, Object... params) {
        super(message, cause, params);
    }

    public ReportInvalidStateException(int status, String message, Object... params) {
        super(status, message, params);
    }

    public ReportInvalidStateException(int status, String message, Throwable cause, Object... params) {
        super(status, message, cause, params);
    }

}
