/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.opensearch.aws;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.util.ObjectBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;

/**
 * Factory for AWS-managed OpenSearch clients.
 *
 * <p>Provides two construction styles:
 *
 * <h3>1. Zero-config shortcut</h3>
 *
 * <pre>{@code
 * // Region + service auto-detected from URL. Uses DefaultCredentialsProvider + AwsCrtHttpClient.
 * OpenSearchClient     client = AwsOpenSearchClient.create("https://my-domain.us-east-1.es.amazonaws.com");
 * AossOpenSearchClient aoss   = AwsOpenSearchClient.createAoss("https://abc123.us-east-1.aoss.amazonaws.com");
 * }</pre>
 *
 * <h3>2. Builder style (full control)</h3>
 *
 * <pre>{@code
 * // SigV4 with custom credentials
 * OpenSearchClient client = AwsOpenSearchClient.of(b -> b
 *     .endpoint("https://my-domain.us-east-1.es.amazonaws.com")
 *     .credentials(myProvider)           // optional -- defaults to DefaultCredentialsProvider
 *     .httpClient(myHttpClient)          // optional -- defaults to AwsCrtHttpClient
 * );
 *
 * // Basic auth (AOS fine-grained access control)
 * OpenSearchClient client = AwsOpenSearchClient.of(b -> b
 *     .endpoint("https://my-domain.us-east-1.es.amazonaws.com")
 *     .basicAuth("admin", "myPassword")
 * );
 *
 * AossOpenSearchClient aoss = AwsOpenSearchClient.ofAoss(b -> b
 *     .endpoint("https://abc123.us-east-1.aoss.amazonaws.com")
 * );
 * }</pre>
 */
public final class AwsOpenSearchClient {

    // Matches AOS:  search-xxx.us-east-1.es.amazonaws.com
    // Matches AOSS: xxx.us-east-1.aoss.amazonaws.com
    static final Pattern ENDPOINT_PATTERN = Pattern.compile(
        "https?://[^.]+\\.([a-z0-9-]+)\\.(es|aoss)\\.amazonaws\\.com.*",
        Pattern.CASE_INSENSITIVE
    );

    private AwsOpenSearchClient() {}

    // ---- Zero-config shortcuts -------------------------------------------

    /**
     * Creates a full {@link OpenSearchClient} with sensible defaults (Strategy A).
     *
     * <p>Region and service name are auto-detected from the endpoint URL.
     * Uses {@link DefaultCredentialsProvider} and {@link AwsCrtHttpClient}.
     *
     * @param endpoint full HTTPS endpoint, e.g. {@code https://my-domain.us-east-1.es.amazonaws.com}
     */
    public static OpenSearchClient create(String endpoint) {
        return new Builder().endpoint(endpoint).build();
    }

    /**
     * Creates a distribution-typed {@link AossOpenSearchClient} with sensible defaults (Strategy B).
     *
     * <p>Use for new projects targeting Amazon OpenSearch Serverless.
     * Blocked operations are absent from the API -- calling them is a compile error.
     *
     * @param endpoint full HTTPS AOSS endpoint, e.g. {@code https://abc123.us-east-1.aoss.amazonaws.com}
     */
    public static AossOpenSearchClient createAoss(String endpoint) {
        return new Builder().endpoint(endpoint).buildAoss();
    }

    // ---- Builder-style construction (full control) -----------------------

    /**
     * Creates a full {@link OpenSearchClient} using a builder lambda.
     *
     * <pre>{@code
     * OpenSearchClient client = AwsOpenSearchClient.of(b -> b
     *     .endpoint("https://my-domain.us-east-1.es.amazonaws.com")
     *     .credentials(myProvider)
     * );
     * }</pre>
     */
    public static OpenSearchClient of(
        Function<Builder, ObjectBuilder<OpenSearchClient>> fn
    ) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Creates a distribution-typed {@link AossOpenSearchClient} using a builder lambda.
     *
     * <pre>{@code
     * AossOpenSearchClient aoss = AwsOpenSearchClient.ofAoss(b -> b
     *     .endpoint("https://abc123.us-east-1.aoss.amazonaws.com")
     *     .credentials(myProvider)
     * );
     * }</pre>
     */
    public static AossOpenSearchClient ofAoss(
        Function<AossBuilder, ObjectBuilder<AossOpenSearchClient>> fn
    ) {
        return fn.apply(new AossBuilder()).build();
    }

    // ---- Builder ---------------------------------------------------------

    /**
     * Builder for {@link OpenSearchClient} (Strategy A).
     * Region and service name are always auto-detected from the endpoint URL.
     */
    public static final class Builder implements ObjectBuilder<OpenSearchClient> {

        @Nullable private String endpoint;
        @Nullable private AwsCredentialsProvider credentials;
        @Nullable private SdkHttpClient httpClient;
        @Nullable private String basicAuthHeader;

        private Builder() {}

        /**
         * Required. Full HTTPS endpoint URL.
         * Region and service name ("es" / "aoss") are inferred automatically.
         */
        @Nonnull
        public Builder endpoint(@Nonnull String endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            return this;
        }

        /**
         * Optional. AWS credentials provider for SigV4 signing.
         * Defaults to {@link DefaultCredentialsProvider} (env vars, instance profile, etc.)
         * Mutually exclusive with {@link #basicAuth}.
         */
        @Nonnull
        public Builder credentials(@Nonnull AwsCredentialsProvider credentials) {
            this.credentials = Objects.requireNonNull(credentials, "credentials");
            return this;
        }

        /**
         * Optional. Use HTTP Basic authentication instead of SigV4.
         *
         * <p>AOS supports basic auth via fine-grained access control (internal user database).
         * When set, SigV4 signing is disabled -- the {@link #credentials} setting is ignored.
         *
         * <p>AOSS does not support basic auth -- use SigV4 (the default) instead.
         *
         * @param username OpenSearch username
         * @param password OpenSearch password
         */
        @Nonnull
        public Builder basicAuth(@Nonnull String username, @Nonnull String password) {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            String raw = username + ":" + password;
            this.basicAuthHeader = "Basic "
                + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /**
         * Optional. AWS SDK v2 HTTP client.
         * Defaults to {@link AwsCrtHttpClient} (best throughput for OpenSearch workloads).
         */
        @Nonnull
        public Builder httpClient(@Nonnull SdkHttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        /** Builds a full {@link OpenSearchClient}. */
        @Override
        @Nonnull
        public OpenSearchClient build() {
            return new OpenSearchClient(buildTransport());
        }

        /** Builds an {@link AossOpenSearchClient}. Validates that the endpoint is an AOSS URL. */
        @Nonnull
        AossOpenSearchClient buildAoss() {
            EndpointInfo info = parsedEndpoint();
            if (!"aoss".equals(info.service)) {
                throw new IllegalArgumentException(
                    "Endpoint does not look like an AOSS endpoint (expected *.aoss.amazonaws.com): "
                        + endpoint
                );
            }
            if (basicAuthHeader != null) {
                throw new IllegalArgumentException(
                    "AOSS does not support basic auth. Use SigV4 (the default) instead."
                );
            }
            return new AossOpenSearchClient(buildTransport());
        }

        private AwsSdk2Transport buildTransport() {
            EndpointInfo info = parsedEndpoint();
            String host = endpoint.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
            SdkHttpClient http = httpClient != null
                ? httpClient
                : AwsCrtHttpClient.builder().build();

            AwsSdk2TransportOptions.Builder optBuilder = AwsSdk2TransportOptions.builder();

            if (basicAuthHeader != null) {
                // Basic auth: inject Authorization header, do not set SigV4 credentials
                optBuilder.addHeader("Authorization", basicAuthHeader);
            } else {
                // SigV4: use provided credentials or fall back to DefaultCredentialsProvider
                optBuilder.setCredentials(credentials != null
                    ? credentials
                    : DefaultCredentialsProvider.create());
            }

            return new AwsSdk2Transport(
                http,
                host,
                info.service,
                Region.of(info.region),
                optBuilder.build()
            );
        }

        private EndpointInfo parsedEndpoint() {
            Objects.requireNonNull(endpoint, "endpoint is required");
            return parseEndpoint(endpoint);
        }
    }

    /**
     * Builder for {@link AossOpenSearchClient} (Strategy B).
     * Identical API to {@link Builder} but {@link #build()} returns the typed AOSS client.
     */
    public static final class AossBuilder implements ObjectBuilder<AossOpenSearchClient> {

        private final Builder inner = new Builder();

        private AossBuilder() {}

        /** Required. Full HTTPS AOSS endpoint URL. */
        @Nonnull
        public AossBuilder endpoint(@Nonnull String endpoint) {
            inner.endpoint(endpoint);
            return this;
        }

        /** Optional. AWS credentials provider. Defaults to {@link DefaultCredentialsProvider}. */
        @Nonnull
        public AossBuilder credentials(@Nonnull AwsCredentialsProvider credentials) {
            inner.credentials(credentials);
            return this;
        }

        /** Optional. AWS SDK v2 HTTP client. Defaults to {@link AwsCrtHttpClient}. */
        @Nonnull
        public AossBuilder httpClient(@Nonnull SdkHttpClient httpClient) {
            inner.httpClient(httpClient);
            return this;
        }

        /**
         * Not supported for AOSS. Calling {@link #build()} after this will throw
         * {@link IllegalArgumentException} with a clear message.
         * AOSS requires SigV4 -- use {@link #credentials} instead.
         */
        @Nonnull
        public AossBuilder basicAuth(@Nonnull String username, @Nonnull String password) {
            inner.basicAuth(username, password);
            return this;
        }

        @Override
        @Nonnull
        public AossOpenSearchClient build() {
            return inner.buildAoss();
        }
    }

    // ---- Internal helpers -----------------------------------------------

    static EndpointInfo parseEndpoint(String endpoint) {
        Matcher m = ENDPOINT_PATTERN.matcher(endpoint);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                "Cannot parse region/service from endpoint URL: "
                    + endpoint
                    + ". Expected: https://<host>.<region>.<es|aoss>.amazonaws.com"
            );
        }
        return new EndpointInfo(m.group(1), m.group(2));
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
