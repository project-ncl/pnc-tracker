/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.rest;

import org.jboss.pnc.tracker.exception.ReportDataConflictException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Provider that maps a {@link ReportDataConflictException} to an HTTP 409 Conflict response.
 * <p>
 * This handles cases where the report is active, but the tracking entry data violates
 * database unique constraints or integrity checks.
 * </p>
 */
@Provider
public class ReportDataConflictMapper implements ExceptionMapper<ReportDataConflictException> {

    @Override
    public Response toResponse(ReportDataConflictException exception) {
        // Mapping domain data conflict error directly to HTTP 409 Conflict
        return Response.status(Response.Status.CONFLICT)
                .entity(exception.getMessage())
                .build();
    }
}