package org.jboss.pnc.tracker.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DbPackageTypeConverter implements AttributeConverter<DbPackageType, String> {

    @Override
    public String convertToDatabaseColumn(DbPackageType attribute) {
        return attribute != null ? attribute.getDbCode() : null;
    }

    @Override
    public DbPackageType convertToEntityAttribute(String dbData) {
        return dbData != null ? DbPackageType.fromDbCode(dbData) : null;
    }
}