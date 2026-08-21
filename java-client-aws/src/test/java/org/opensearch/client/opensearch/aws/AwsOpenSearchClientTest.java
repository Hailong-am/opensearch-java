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
 * Unit tests for URL parsing in {@link AwsOpenSearchClient}.
 *
 * These tests prove the core POC claim: region and service name are correctly
 * inferred from the endpoint URL, eliminating the #1 pain point of AwsSdk2Transport
 * (users had to manually specify "es" vs "aoss" even though the URL already contains it).
 */
public class AwsOpenSearchClientTest {

    // ---- AOS (managed domains) -------------------------------------------

    @Test
    public void aosUsEast1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parse(
            "https://search-my-domain.us-east-1.es.amazonaws.com");
        assertEquals("us-east-1", info.region);
        assertEquals("es", info.service);
    }

    @Test
    public void aosEuWest1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parse(
            "https://search-my-domain.eu-west-1.es.amazonaws.com");
        assertEquals("eu-west-1", info.region);
        assertEquals("es", info.service);
    }

    @Test
    public void aosApSoutheast2() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parse(
            "https://my-domain.ap-southeast-2.es.amazonaws.com");
        assertEquals("ap-southeast-2", info.region);
        assertEquals("es", info.service);
    }

    // ---- AOSS (serverless collections) -----------------------------------

    @Test
    public void aossUsEast1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parse(
            "https://abc123.us-east-1.aoss.amazonaws.com");
        assertEquals("us-east-1", info.region);
        assertEquals("aoss", info.service);
    }

    @Test
    public void aossUsWest2() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parse(
            "https://abc123.us-west-2.aoss.amazonaws.com");
        assertEquals("us-west-2", info.region);
        assertEquals("aoss", info.service);
    }

    @Test
    public void aossEuCentral1() {
        AwsOpenSearchClient.EndpointInfo info = AwsOpenSearchClient.parse(
            "https://my-collection.eu-central-1.aoss.amazonaws.com");
        assertEquals("eu-central-1", info.region);
        assertEquals("aoss", info.service);
    }

    // ---- Error cases -----------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void localhostThrows() {
        AwsOpenSearchClient.parse("https://localhost:9200");
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongServiceSuffixThrows() {
        AwsOpenSearchClient.parse(
            "https://my-domain.us-east-1.elasticsearch.amazonaws.com");
    }

    @Test
    public void createAossRejectsAosEndpoint() {
        // createAoss() on an AOS URL must fail fast with a clear message --
        // NOT silently sign with the wrong service name causing a 403
        try {
            AwsOpenSearchClient.createAoss(
                "https://search-my-domain.us-east-1.es.amazonaws.com");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("message should mention aoss.amazonaws.com",
                e.getMessage().contains("aoss.amazonaws.com"));
        }
    }
}
