/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

public enum DbStoreEffect {
    UPLOAD("U"),
    DOWNLOAD("D");

    private final String dbCode;

    DbStoreEffect(String dbCode) {
        this.dbCode = dbCode;
    }

    public String getDbCode() {
        return dbCode;
    }

    public static DbStoreEffect fromDbCode(String dbCode) {
        if ("U".equals(dbCode)) {
            return UPLOAD;
        }
        if ("D".equals(dbCode)) {
            return DOWNLOAD;
        }
        throw new IllegalArgumentException("Unknown DB code for StoreEffect: " + dbCode);
    }
}
