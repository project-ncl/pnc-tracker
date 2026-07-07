/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.model;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "repository",
        uniqueConstraints = @UniqueConstraint(columnNames = { "project", "name" }))
public class DbRepository extends PanacheEntity {

    /** The name of the project this repository belongs to. */
    @Column(name = "project", length = 32)
    public String project;

    /** The specific name of the repository. */
    @Column(name = "project", length = 32)
    public String name;

    /** The type of packages managed by this repository. */
    @Column(name = "package_type", columnDefinition = "char(1)")
    public DbPackageType packageType;

    public DbRepository() {
    }

    /**
     * Finds a tracking report by its unique tracking key.
     *
     * @param trackingId the unique identifier of the tracking report to find
     * @return the matching {@link DbRepository} instance, or {@code null} if no such report exists
     */
    public static DbRepository findByProjectAndName(String project, String name) {
        @SuppressWarnings("unchecked")
        List<DbRepository> resultList = getEntityManager()
                .createQuery("FROM DbRepository WHERE project = :project AND name = :name")
                .setParameter("project", project)
                .setParameter("name", name)
                .getResultList();
        if (resultList.isEmpty()) {
            return null;
        } else {
            return resultList.get(0);
        }
    }

}
