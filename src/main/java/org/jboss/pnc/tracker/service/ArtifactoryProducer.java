/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.service;

import java.util.Optional;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.pnc.tracker.exception.TrackerException;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ArtifactoryProducer {

    private static final Logger logger = LoggerFactory.getLogger(ArtifactoryProducer.class);

    @ConfigProperty(name = "tracker.artifactory.url")
    Optional<String> url;

    @ConfigProperty(name = "tracker.artifactory.access-token")
    Optional<String> accessToken;

    private volatile Artifactory artifactory;

    @Produces
    @ApplicationScoped
    public Artifactory produce() throws TrackerException {
        if (url.isEmpty() || url.get().isBlank() || accessToken.isEmpty() || accessToken.get().isBlank()) {
            throw new IllegalStateException(
                    "Artifactory is not configured. Please set 'tracker.artifactory.url' and 'tracker.artifactory.access-token'.");
        }

        String artifactoryUrl = url.get();
        logger.info("Creating Artifactory client connection to {}", artifactoryUrl);

        try {
            Artifactory client = ArtifactoryClientBuilder.create()
                    .setAccessToken(accessToken.get())
                    .setUrl(artifactoryUrl)
                    .build();

            try {
                logger.info("Running against Artifactory version: {}", client.system().version().getVersion());
            } catch (Exception e) {
                logger.warn("Artifactory client created, but version check failed: {}", e.getMessage());
            }

            this.artifactory = client;
            return client;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Artifactory client for URL: " + artifactoryUrl, e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (artifactory != null) {
            logger.info("Closing Artifactory client connection");
            try {
                artifactory.close();
            } catch (Exception e) {
                logger.warn("Error closing Artifactory client: {}", e.getMessage());
            }
        }
    }
}
