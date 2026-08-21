/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.opensearch.aws;

import java.io.IOException;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.indices.OpenSearchIndicesClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.util.ObjectBuilder;

/**
 * A distribution-typed OpenSearch client for Amazon OpenSearch Serverless (AOSS).
 *
 * <p>This client exposes only the operations supported by AOSS. Attempting to call an unsupported
 * operation (such as {@code reindex}, {@code scroll}, or {@code fieldCaps}) will result in a
 * <strong>compile error</strong> -- not a runtime 400/403 from the service.
 *
 * <h3>Design: composition over inheritance</h3>
 *
 * <p>This class does <em>not</em> extend {@link OpenSearchClient}. It uses composition (delegating
 * to an internal {@code OpenSearchClient}) and explicitly re-exposes only the ~620 AOSS-supported
 * operations as its public API. This is what gives true compile-time safety: blocked operations
 * simply do not exist on this type.
 *
 * <h3>Escape hatch for third-party libraries</h3>
 *
 * <p>If you need to pass the client to a library that accepts {@link OpenSearchClient} directly
 * (for example Spring AI or LangChain4j), use {@link #asOpenSearchClient()}. Note that this
 * bypasses the distribution safety guarantee at that call site.
 *
 * <pre>{@code
 * AossOpenSearchClient aoss = AwsOpenSearchClient.createAoss(
 *     "https://my-collection.us-east-1.aoss.amazonaws.com");
 *
 * // Supported -- compiles fine
 * SearchResponse<MyDoc> resp = aoss.search(s -> s.index("my-index"), MyDoc.class);
 *
 * // NOT supported by AOSS -- compile error: method reindex() not found
 * // aoss.reindex(...);
 * }</pre>
 *
 * <p>NOTE: This is a POC. The full implementation would expose all ~620 AOSS-supported operations.
 * Only a representative subset is shown here to demonstrate the pattern.
 */
public final class AossOpenSearchClient {

    private final OpenSearchClient delegate;

    AossOpenSearchClient(OpenSearchTransport transport) {
        this.delegate = new OpenSearchClient(transport);
    }

    AossOpenSearchClient(OpenSearchTransport transport, @Nullable TransportOptions transportOptions) {
        this.delegate = new OpenSearchClient(transport, transportOptions);
    }

    // ---- Escape hatch -------------------------------------------------------

    /**
     * Returns the underlying {@link OpenSearchClient} for use with third-party libraries that
     * require it directly. This bypasses AOSS distribution safety at the call site.
     */
    public OpenSearchClient asOpenSearchClient() {
        return delegate;
    }

    // ---- Child clients (AOSS-supported) -------------------------------------

    /**
     * Returns the indices client. AOSS supports a subset of index operations (create, delete, get,
     * list). Operations like open/close/clone/split are not supported.
     */
    public OpenSearchIndicesClient indices() {
        return delegate.indices();
    }

    // ---- Document operations (all supported by AOSS) -------------------------

    public <T> IndexResponse index(IndexRequest<T> request) throws IOException, OpenSearchException {
        return delegate.index(request);
    }

    public <T> IndexResponse index(
        Function<IndexRequest.Builder<T>, ObjectBuilder<IndexRequest<T>>> fn
    ) throws IOException, OpenSearchException {
        return delegate.index(fn);
    }

    public <T> GetResponse<T> get(GetRequest request, Class<T> tClass)
        throws IOException, OpenSearchException {
        return delegate.get(request, tClass);
    }

    public DeleteResponse delete(DeleteRequest request) throws IOException, OpenSearchException {
        return delegate.delete(request);
    }

    public DeleteResponse delete(
        Function<DeleteRequest.Builder, ObjectBuilder<DeleteRequest>> fn
    ) throws IOException, OpenSearchException {
        return delegate.delete(fn);
    }

    public BulkResponse bulk(BulkRequest request) throws IOException, OpenSearchException {
        return delegate.bulk(request);
    }

    public BulkResponse bulk(
        Function<BulkRequest.Builder, ObjectBuilder<BulkRequest>> fn
    ) throws IOException, OpenSearchException {
        return delegate.bulk(fn);
    }

    // ---- Search (supported by AOSS) -----------------------------------------

    public <T> SearchResponse<T> search(SearchRequest request, Class<T> tClass)
        throws IOException, OpenSearchException {
        return delegate.search(request, tClass);
    }

    public <T> SearchResponse<T> search(
        Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn,
        Class<T> tClass
    ) throws IOException, OpenSearchException {
        return delegate.search(fn, tClass);
    }

    // ---- BLOCKED OPERATIONS (not exposed = compile error if called) ----------
    //
    // The following operations are NOT supported by AOSS and are intentionally
    // absent from this class's public API:
    //
    //   reindex()            -- no cross-index data movement
    //   scroll() / clearScroll() -- no stateful scroll cursors
    //   fieldCaps()          -- not supported
    //   explain()            -- not supported
    //   reindexRethrottle()  -- blocked (reindex is blocked)
    //   termsEnum()          -- not supported
    //   rankEval()           -- not supported
    //   renderSearchTemplate() -- not supported
    //   scriptsPainlessExecute() -- not supported
    //
    // Full list: see AOS/AOSS gap analysis doc at https://chorus.aws.dev/doc/0uojIdS6JqXo
}
