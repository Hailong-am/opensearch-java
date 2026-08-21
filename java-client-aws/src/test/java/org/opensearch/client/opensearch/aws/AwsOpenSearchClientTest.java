/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.opensearch.aws;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link AwsOpenSearchClient} -- URL parsing, shortcuts, and builder style.
 */
public class AwsOpenSearchClientTest {

    // ---- URL parsing: AOS (managed domains) -----------------------------

    @Test
    public void aosUsEast1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parseEndpoint(
            "https://search-my-domain.us-east-1.es.amazonaws.com");
        assertEquals("us-east-1", info.region);
        assertEquals("es", info.service);
    }

    @Test
    public void aosEuWest1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parseEndpoint(
            "https://search-my-domain.eu-west-1.es.amazonaws.com");
        assertEquals("eu-west-1", info.region);
        assertEquals("es", info.service);
    }

    @Test
    public void aosApSoutheast2() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parseEndpoint(
            "https://my-domain.ap-southeast-2.es.amazonaws.com");
        assertEquals("ap-southeast-2", info.region);
        assertEquals("es", info.service);
    }

    // ---- URL parsing: AOSS (serverless collections) ---------------------

    @Test
    public void aossUsEast1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parseEndpoint(
            "https://abc123.us-east-1.aoss.amazonaws.com");
        assertEquals("us-east-1", info.region);
        assertEquals("aoss", info.service);
    }

    @Test
    public void aossUsWest2() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parseEndpoint(
            "https://abc123.us-west-2.aoss.amazonaws.com");
        assertEquals("us-west-2", info.region);
        assertEquals("aoss", info.service);
    }

    @Test
    public void aossEuCentral1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parseEndpoint(
            "https://my-collection.eu-central-1.aoss.amazonaws.com");
        assertEquals("eu-central-1", info.region);
        assertEquals("aoss", info.service);
    }

    // ---- Error cases ----------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void localhostThrows() {
        AwsOpenSearchClient.parseEndpoint("https://localhost:9200");
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongServiceSuffixThrows() {
        AwsOpenSearchClient.parseEndpoint(
            "https://my-domain.us-east-1.elasticsearch.amazonaws.com");
    }

    @Test
    public void createAossRejectsAosEndpoint() {
        try {
            AwsOpenSearchClient.createAoss(
                "https://search-my-domain.us-east-1.es.amazonaws.com");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("aoss.amazonaws.com"));
        }
    }

    // ---- Builder style: of() / ofAoss() ---------------------------------

    @Test(expected = NullPointerException.class)
    public void builderRequiresEndpoint() {
        // endpoint is required -- missing it must throw, not silently build
        AwsOpenSearchClient.of(b -> b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ofAossRejectsAosEndpoint() {
        AwsOpenSearchClient.ofAoss(b -> b
            .endpoint("https://search-my-domain.us-east-1.es.amazonaws.com"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderRejectsInvalidEndpoint() {
        AwsOpenSearchClient.of(b -> b.endpoint("https://localhost:9200"));
    }

    @Test
    public void builderAcceptsValidAosEndpoint() {
        // Should not throw -- transport is created (no real connection attempted)
        org.opensearch.client.opensearch.OpenSearchClient client =
            AwsOpenSearchClient.of(b -> b
                .endpoint("https://my-domain.us-east-1.es.amazonaws.com"));
        assertNotNull(client);
    }

    @Test
    public void ofAossAcceptsValidAossEndpoint() {
        AossOpenSearchClient aoss = AwsOpenSearchClient.ofAoss(b -> b
            .endpoint("https://abc123.us-east-1.aoss.amazonaws.com"));
        assertNotNull(aoss);
        // Escape hatch must be present
        assertNotNull(aoss.asOpenSearchClient());
    }
}
