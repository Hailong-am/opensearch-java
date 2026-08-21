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
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * Demonstrates the compile-time safety guarantee of {@link AossOpenSearchClient}.
 *
 * <p>The key POC claim: blocked AOSS operations do not exist on {@link AossOpenSearchClient}.
 * Calling them is a COMPILE ERROR, not a runtime 400/403.
 *
 * <p>The commented-out lines below are the proof -- uncomment any of them and
 * the build will fail with "cannot find symbol".
 */
public class AossCompileTimeSafetyTest {

    @Test
    public void supportedOperationsCompileAndAreAccessible() throws Exception {
        // These operations ARE supported by AOSS -- they compile and are accessible.
        // (We only check method existence via reflection here; actual invocation
        //  requires a real endpoint.)
        Class<?> clazz = AossOpenSearchClient.class;

        assertNotNull("search() must be present",
            clazz.getMethod("search", 
                org.opensearch.client.opensearch.core.SearchRequest.class,
                Class.class));

        assertNotNull("index() must be present",
            clazz.getMethod("index",
                org.opensearch.client.opensearch.core.IndexRequest.class));

        assertNotNull("delete() must be present",
            clazz.getMethod("delete",
                org.opensearch.client.opensearch.core.DeleteRequest.class));

        assertNotNull("bulk() must be present",
            clazz.getMethod("bulk",
                org.opensearch.client.opensearch.core.BulkRequest.class));

        assertNotNull("indices() must be present",
            clazz.getMethod("indices"));

        assertNotNull("asOpenSearchClient() escape hatch must be present",
            clazz.getMethod("asOpenSearchClient"));
    }

    @Test
    public void blockedOperationsAreAbsentFromType() {
        // Blocked AOSS operations must NOT exist on AossOpenSearchClient.
        // Their absence == compile error if called.
        Class<?> clazz = AossOpenSearchClient.class;
        String[] blockedMethods = {
            "reindex", "reindexRethrottle",
            "scroll", "clearScroll",
            "fieldCaps",
            "explain",
            "termsEnum",
            "rankEval",
            "renderSearchTemplate",
            "scriptsPainlessExecute",
        };

        for (String method : blockedMethods) {
            boolean found = false;
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                if (m.getName().equals(method)) {
                    found = true;
                    break;
                }
            }
            assertFalse("Blocked AOSS operation '" + method
                + "' must NOT be present on AossOpenSearchClient", found);
        }
    }

    @Test
    public void escapeHatchReturnsFullClient() throws Exception {
        // asOpenSearchClient() must return OpenSearchClient for 3rd-party lib compat
        Class<?> clazz = AossOpenSearchClient.class;
        java.lang.reflect.Method m = clazz.getMethod("asOpenSearchClient");
        assertEquals("asOpenSearchClient() return type must be OpenSearchClient",
            OpenSearchClient.class, m.getReturnType());
    }

    /*
     * ---- COMPILE-TIME PROOF ------------------------------------------------
     *
     * Uncomment ANY of the lines below to see a compile error.
     * This is the core value proposition: wrong distribution = build breaks.
     *
     * void proofBlockedMethodsDoNotCompile(AossOpenSearchClient aoss) throws Exception {
     *     aoss.reindex(r -> r);                   // ERROR: cannot find symbol
     *     aoss.scroll(s -> s, String.class);      // ERROR: cannot find symbol
     *     aoss.fieldCaps(f -> f);                 // ERROR: cannot find symbol
     *     aoss.explain(e -> e, String.class);     // ERROR: cannot find symbol
     * }
     */
}
