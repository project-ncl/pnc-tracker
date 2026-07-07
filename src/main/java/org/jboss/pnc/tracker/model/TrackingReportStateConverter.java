/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrackingReportStateConverter implements AttributeConverter<TrackingReportState, String> {

    @Override
    public String convertToDatabaseColumn(TrackingReportState attribute) {
        return attribute == null ? null : attribute.getDbCode();
    }

    @Override
    public TrackingReportState convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TrackingReportState.fromDbCode(dbData);
    }
}