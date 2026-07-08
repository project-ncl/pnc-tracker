/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.exception;

/**
 * Thrown when a tracking report cannot be found in the system.
 */
public class ReportNotFoundException extends TrackerException {

    public ReportNotFoundException(String message, Object... params) {
        super(message, params);
    }

    public ReportNotFoundException(String message, Throwable cause, Object... params) {
        super(message, cause, params);
    }

    public ReportNotFoundException(int status, String message, Object... params) {
        super(status, message, params);
    }

    public ReportNotFoundException(int status, String message, Throwable cause, Object... params) {
        super(status, message, cause, params);
    }

}
