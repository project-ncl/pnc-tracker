package org.jboss.pnc.tracker.service;

import org.jboss.pnc.tracker.model.DbPackageType;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ArtifactoryConnector {

    @ConfigProperty(name = "artifactory.token")
    String artifactoryToken;

    public ArtifactoryConnector() {
        // TODO Auto-generated constructor stub
    }

    public DbPackageType fetchPackageType(String project, String name) {
        // TODO Auto-generated method stub
        return DbPackageType.MAVEN;
    }

}
