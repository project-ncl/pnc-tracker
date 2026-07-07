/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.rest;

import org.jboss.pnc.tracker.exception.ReportInvalidStateException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Provider that maps a {@link ReportInvalidStateException} to an HTTP 409 Conflict response.
 * <p>
 * This handles cases where a client attempts to modify a report that is already
 * in a finalized state (e.g., SEALED or CORRUPTED).
 * </p>
 */
@Provider
public class ReportInvalidStateMapper implements ExceptionMapper<ReportInvalidStateException> {

    @Override
    public Response toResponse(ReportInvalidStateException exception) {
        // Mapping domain invalid state error directly to HTTP 409 Conflict
        return Response.status(Response.Status.CONFLICT)
                .entity(exception.getMessage())
                .build();
    }
}