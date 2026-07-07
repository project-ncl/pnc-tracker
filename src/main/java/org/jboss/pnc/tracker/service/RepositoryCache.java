/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.service;

import org.jboss.pnc.tracker.model.DbPackageType;
import org.jboss.pnc.tracker.model.DbRepository;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RepositoryCache {

    @Inject
    ArtifactoryConnector artifactoryConnector;

    @CacheResult(cacheName = "repo-metadata-idx")
    public Long getOrCreateRepositoryId(String project, String name) {
        DbRepository meta = DbRepository
            .find("project = ?1 and name = ?2", project, name)
            .firstResult();
        if (meta != null) {
            return meta.id;
        }

        DbPackageType packageType = artifactoryConnector.fetchPackageType(project, name);

        return saveNewRepository(project, name, packageType);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Long saveNewRepository(String project, String name, DbPackageType packageType) {
        // Double-check lock (defensive approach):
        // Because Artifactory call is outside of the transaction it could happen that another thread created the
        // repository. Checking once again inside the transaction.
        DbRepository meta = DbRepository.find("project = ?1 and name = ?2", project, name).firstResult();

        if (meta == null) {
            meta = new DbRepository();
            meta.project = project;
            meta.name = name;
            meta.packageType = packageType;
            meta.persist();
        }

        return meta.id;
    }

}
