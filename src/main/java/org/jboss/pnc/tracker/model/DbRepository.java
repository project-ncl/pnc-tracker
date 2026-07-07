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
