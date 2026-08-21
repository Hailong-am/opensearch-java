/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.opensearch.aws;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;

/**
 * Factory for AWS-managed OpenSearch clients.
 *
 * <p>Eliminates the #1 pain point of {@link AwsSdk2Transport}: users no longer need to manually
 * specify the service name ("es" vs "aoss") or the region -- both are inferred from the endpoint
 * URL.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // Strategy A: full OpenSearchClient, zero boilerplate
 * OpenSearchClient client = AwsOpenSearchClient.create(
 *     "https://my-domain.us-east-1.es.amazonaws.com");
 *
 * // Strategy B: distribution-typed client for compile-time safety (new projects)
 * AossOpenSearchClient aoss = AwsOpenSearchClient.createAoss(
 *     "https://my-collection.us-east-1.aoss.amazonaws.com");
 * }</pre>
 */
public final class AwsOpenSearchClient {

    // Matches AOS:  search-xxx.us-east-1.es.amazonaws.com
    // Matches AOSS: xxx.us-east-1.aoss.amazonaws.com
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile(
        "https?://[^.]+\\.([a-z0-9-]+)\\.(es|aoss)\\.amazonaws\\.com.*",
        Pattern.CASE_INSENSITIVE
    );

    private AwsOpenSearchClient() {}

    /**
     * Creates a full {@link OpenSearchClient} (Strategy A).
     *
     * <p>Use this when:
     * <ul>
     *   <li>Migrating from the OSS jar with zero code changes beyond the factory call</li>
     *   <li>Passing the client to a third-party library (Spring AI, LangChain4j) that accepts
     *       {@code OpenSearchClient}</li>
     * </ul>
     *
     * @param endpoint full HTTPS endpoint URL, e.g.
     *     {@code https://my-domain.us-east-1.es.amazonaws.com}
     */
    public static OpenSearchClient create(String endpoint) {
        EndpointInfo info = parse(endpoint);
        return new OpenSearchClient(buildTransport(endpoint, info));
    }

    /**
     * Creates a distribution-typed {@link AossOpenSearchClient} (Strategy B).
     *
     * <p>Use this for new projects targeting Amazon OpenSearch Serverless. Operations not supported
     * by AOSS are absent from the API -- calling them produces a compile error.
     *
     * @param endpoint full HTTPS endpoint URL, e.g.
     *     {@code https://my-collection.us-east-1.aoss.amazonaws.com}
     */
    public static AossOpenSearchClient createAoss(String endpoint) {
        EndpointInfo info = parse(endpoint);
        if (!"aoss".equals(info.service)) {
            throw new IllegalArgumentException(
                "Endpoint does not look like an AOSS endpoint (expected *.aoss.amazonaws.com): "
                    + endpoint
            );
        }
        return new AossOpenSearchClient(buildTransport(endpoint, info));
    }

    // ---- internals -------------------------------------------------------

    static EndpointInfo parse(String endpoint) {
        Matcher m = ENDPOINT_PATTERN.matcher(endpoint);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                "Cannot parse region/service from endpoint URL: "
                    + endpoint
                    + ". Expected pattern: https://<host>.<region>.<es|aoss>.amazonaws.com"
            );
        }
        return new EndpointInfo(m.group(1), m.group(2));
    }

    private static AwsSdk2Transport buildTransport(String endpoint, EndpointInfo info) {
        // Strip scheme for AwsSdk2Transport (it expects host only)
        String host = endpoint.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        return new AwsSdk2Transport(
            AwsCrtHttpClient.builder().build(),
            host,
            info.service,
            Region.of(info.region),
            AwsSdk2TransportOptions.builder()
                .setCredentials(DefaultCredentialsProvider.create())
                .build()
        );
    }

    /** Parsed region and service name extracted from an endpoint URL. */
    static final class EndpointInfo {
        final String region;
        final String service;

        EndpointInfo(String region, String service) {
            this.region = region;
            this.service = service;
        }
    }
}
