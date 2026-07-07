/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DbTrackingReportStateConverter implements AttributeConverter<DbTrackingReportState, String> {

    @Override
    public String convertToDatabaseColumn(DbTrackingReportState attribute) {
        return attribute == null ? null : attribute.getDbCode();
    }

    @Override
    public DbTrackingReportState convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DbTrackingReportState.fromDbCode(dbData);
    }
}