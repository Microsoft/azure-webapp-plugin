/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package org.jenkinsci.plugins.microsoft.appservice.commands;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.microsoft.exceptions.AzureCloudException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * To provide some common docker-related methods for docker commands.
 */
public abstract class DockerCommand {

    private static final int CONNECT_TIMEOUT = 1000;
    private static final int MAX_TOTAL_CONNECTIONS = 1;
    private static final int MAX_PER_ROUTE_CONNECTIONS = 1;

    protected DockerClient getDockerClient(final AuthConfig authConfig) {
        final AzureDockerClientConfig.Builder builder = AzureDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryUsername(authConfig.getUsername())
                .withRegistryPassword(authConfig.getPassword())
                .withRegistryUrl(authConfig.getRegistryAddress())
                .withRegistryEmail(authConfig.getEmail());

        final DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withMaxTotalConnections(MAX_TOTAL_CONNECTIONS)
                .withMaxPerRouteConnections(MAX_PER_ROUTE_CONNECTIONS);

        return DockerClientBuilder.getInstance(builder.build())
                .withDockerCmdExecFactory(dockerCmdExecFactory).build();
    }

    protected String getFullImageName(final DockerBuildInfo dockerBuildInfo) throws AzureCloudException {
        if (StringUtils.isNotBlank(dockerBuildInfo.getDockerImage())) {
            return dockerBuildInfo.getDockerImage();
        }

        final String linuxFxVersion = dockerBuildInfo.getLinuxFxVersion();
        if (!linuxFxVersion.startsWith("DOCKER|")) {
            throw new AzureCloudException("unrecognized docker container");
        }
        // the linuxFxVersion should be "DOCKER|<registry>/repo:tag"
        // <registry>/repo:tag
        final String originalImageName = linuxFxVersion.substring(linuxFxVersion.indexOf("|") + 1).toLowerCase();

        final String newRegistry = getRegistryHostname(dockerBuildInfo.getAuthConfig().getRegistryAddress());
        final StringBuilder stringBuilder = new StringBuilder();
        if (!newRegistry.contains("index.docker.io")) {
            // append the registry host as part of the image name, it's required for non-dockerHub registry
            stringBuilder.append(newRegistry).append("/");
        }
        // append user name
        stringBuilder.append(dockerBuildInfo.getAuthConfig().getUsername()).append("/");
        // append the original repo part after username
        final NameParser.ReposTag reposTag = NameParser.parseRepositoryTag(originalImageName);
        final String imageNameWithoutTagAndRegistry = NameParser.resolveRepositoryName(reposTag.repos).reposName;
        final String[] nameParts = imageNameWithoutTagAndRegistry.split("/", 2);
        if (nameParts.length == 1) {
            stringBuilder.append(imageNameWithoutTagAndRegistry);
        } else {
            stringBuilder.append(nameParts[1]);
        }

        return stringBuilder.toString();
    }

    protected String imageAndTag(final DockerBuildInfo dockerBuildInfo) throws AzureCloudException {
        return String.format("%s:%s", getFullImageName(dockerBuildInfo), dockerBuildInfo.getDockerImageTag());
    }

    protected String getRegistryHostname(String registryAddress) throws AzureCloudException {
        try {
            if (!registryAddress.toLowerCase().matches("^\\w+://.*")) {
                registryAddress = "http://" + registryAddress;
            }

            final URI uri = new URI(registryAddress);
            return uri.getHost();
        } catch (URISyntaxException e) {
            throw new AzureCloudException("The docker registry is not a valid URI", e);
        }
    }
}
