/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StoreEffectConverter implements AttributeConverter<StoreEffect, String> {

    @Override
    public String convertToDatabaseColumn(StoreEffect attribute) {
        return attribute == null ? null : attribute.getDbCode();
    }

    @Override
    public StoreEffect convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StoreEffect.fromDbCode(dbData);
    }
}