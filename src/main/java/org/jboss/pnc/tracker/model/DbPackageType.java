/**
 * Copyright (C) 2022-2023 Red Hat, Inc. (https://github.com/Commonjava/indy-tracking-service)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
