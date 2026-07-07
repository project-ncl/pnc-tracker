/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DbPackageType {
    MAVEN("M"),
    NPM("N"),
    RPM("R"),
    GENERIC("G");

    // Static Map initialized only once when the class is loaded by JVM
    private static final Map<String, DbPackageType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(DbPackageType::getDbCode, Function.identity()));

    private final String dbCode;

    DbPackageType(String dbCode) {
        this.dbCode = dbCode;
    }

    public String getDbCode() {
        return dbCode;
    }

    public static DbPackageType fromDbCode(String dbCode) {
        DbPackageType type = BY_CODE.get(dbCode);
        if (type == null) {
            throw new IllegalArgumentException("Unknown DB code for DbPackageType: " + dbCode);
        }
        return type;
    }
}
