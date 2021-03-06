/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cloud.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.*;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;

/**
 *
 */
public class AwsS3Service extends AbstractLifecycleComponent<AwsS3Service> {

    private AmazonS3Client client;

    @Inject
    public AwsS3Service(Settings settings, SettingsFilter settingsFilter) {
        super(settings);

        settingsFilter.addFilter(new AwsSettingsFilter());
    }

    public synchronized AmazonS3 client() {
        if (client != null) {
            return client;
        }

        ClientConfiguration clientConfiguration = new ClientConfiguration();
        String protocol = componentSettings.get("protocol", "http").toLowerCase();
        if ("http".equals(protocol)) {
            clientConfiguration.setProtocol(Protocol.HTTP);
        } else if ("https".equals(protocol)) {
            clientConfiguration.setProtocol(Protocol.HTTPS);
        } else {
            throw new ElasticsearchIllegalArgumentException("No protocol supported [" + protocol + "], can either be [http] or [https]");
        }
        String account = componentSettings.get("access_key", settings.get("cloud.account"));
        String key = componentSettings.get("secret_key", settings.get("cloud.key"));

        String proxyHost = componentSettings.get("proxy_host");
        if (proxyHost != null) {
            String portString = componentSettings.get("proxy_port", "80");
            Integer proxyPort;
            try {
                proxyPort = Integer.parseInt(portString, 10);
            } catch (NumberFormatException ex) {
                throw new ElasticsearchIllegalArgumentException("The configured proxy port value [" + portString + "] is invalid", ex);
            }
            clientConfiguration.withProxyHost(proxyHost).setProxyPort(proxyPort);
        }

        AWSCredentialsProvider credentials;

        if (account == null && key == null) {
            credentials = new AWSCredentialsProviderChain(
                    new EnvironmentVariableCredentialsProvider(),
                    new SystemPropertiesCredentialsProvider(),
                    new InstanceProfileCredentialsProvider()
            );
        } else {
            credentials = new AWSCredentialsProviderChain(
                    new StaticCredentialsProvider(new BasicAWSCredentials(account, key))
            );
        }
        this.client = new AmazonS3Client(credentials, clientConfiguration);

        if (componentSettings.get("s3.endpoint") != null) {
            String endpoint = componentSettings.get("s3.endpoint");
            logger.debug("using explicit s3 endpoint [{}]", endpoint);
            client.setEndpoint(endpoint);
        } else if (componentSettings.get("region") != null) {
            String endpoint;
            String region = componentSettings.get("region").toLowerCase();
            if ("us-east".equals(region)) {
                endpoint = "s3.amazonaws.com";
            } else if ("us-east-1".equals(region)) {
                endpoint = "s3.amazonaws.com";
            } else if ("us-west".equals(region)) {
                endpoint = "s3-us-west-1.amazonaws.com";
            } else if ("us-west-1".equals(region)) {
                endpoint = "s3-us-west-1.amazonaws.com";
            } else if ("us-west-2".equals(region)) {
                endpoint = "s3-us-west-2.amazonaws.com";
            } else if ("ap-southeast".equals(region)) {
                endpoint = "s3-ap-southeast-1.amazonaws.com";
            } else if ("ap-southeast-1".equals(region)) {
                endpoint = "s3-ap-southeast-1.amazonaws.com";
            } else if ("ap-southeast-2".equals(region)) {
                endpoint = "s3-ap-southeast-2.amazonaws.com";
            } else if ("ap-northeast".equals(region)) {
                endpoint = "s3-ap-northeast-1.amazonaws.com";
            } else if ("ap-northeast-1".equals(region)) {
                endpoint = "s3-ap-northeast-1.amazonaws.com";
            } else if ("eu-west".equals(region)) {
                endpoint = "s3-eu-west-1.amazonaws.com";
            } else if ("eu-west-1".equals(region)) {
                endpoint = "s3-eu-west-1.amazonaws.com";
            } else if ("sa-east".equals(region)) {
                endpoint = "s3-sa-east-1.amazonaws.com";
            } else if ("sa-east-1".equals(region)) {
                endpoint = "s3-sa-east-1.amazonaws.com";
            } else {
                throw new ElasticsearchIllegalArgumentException("No automatic endpoint could be derived from region [" + region + "]");
            }
            if (endpoint != null) {
                logger.debug("using s3 region [{}], with endpoint [{}]", region, endpoint);
                client.setEndpoint(endpoint);
            }
        }

        return this.client;
    }

    @Override
    protected void doStart() throws ElasticsearchException {
    }

    @Override
    protected void doStop() throws ElasticsearchException {
    }

    @Override
    protected void doClose() throws ElasticsearchException {
        if (client != null) {
            client.shutdown();
        }
    }

}
