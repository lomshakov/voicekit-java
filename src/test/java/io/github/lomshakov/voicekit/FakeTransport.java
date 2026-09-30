package io.github.lomshakov.voicekit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A {@link Transport} that answers from a route table instead of the network,
 * and records everything it was asked to do.
 */
final class FakeTransport implements Transport {

    private final List<Request> requests = new ArrayList<>();
    private final Map<String, Supplier<Response>> routes = new LinkedHashMap<>();
    private Supplier<Response> fallback = () -> Response.text(200, "{}");

    /** Registers a JSON answer for a path (matched exactly, then by prefix). */
    FakeTransport on(String path, int status, String body) {
        routes.put(path, () -> Response.of(
            status,
            Map.of("Content-Type", List.of("application/json")),
            body.getBytes(StandardCharsets.UTF_8)));

        return this;
    }

    /** Registers a {@code 200} JSON answer for a path. */
    FakeTransport on(String path, String body) {
        return on(path, 200, body);
    }

    /** Registers a raw answer for a path. */
    FakeTransport onBytes(String path, byte[] body) {
        routes.put(path, () -> Response.of(200, Map.of(), body));

        return this;
    }

    /** Registers a transport-level failure for a path. */
    FakeTransport onFailure(String path, RuntimeException failure) {
        routes.put(path, () -> {
            throw failure;
        });

        return this;
    }

    /** The answer used when no route matches. */
    FakeTransport always(String body) {
        fallback = () -> Response.text(200, body);

        return this;
    }

    /** The answer used when no route matches. */
    FakeTransport alwaysBytes(byte[] body) {
        fallback = () -> Response.of(200, Map.of(), body);

        return this;
    }

    /** The answer used when no route matches. */
    FakeTransport always(int status, String body) {
        fallback = () -> Response.text(status, body);

        return this;
    }

    @Override
    public Response send(Request request) {
        requests.add(request);

        String path = request.uri().getPath();
        Supplier<Response> route = routes.get(path);

        if (route == null) {
            for (Map.Entry<String, Supplier<Response>> entry : routes.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    route = entry.getValue();
                    break;
                }
            }
        }

        return (route == null ? fallback : route).get();
    }

    /** Every request performed, in order. */
    List<Request> requests() {
        return requests;
    }

    /** The request performed at the given position. */
    Request request(int index) {
        return requests.get(index);
    }

    /** The last request performed. */
    Request lastRequest() {
        return requests.get(requests.size() - 1);
    }

    /** The number of requests performed. */
    int count() {
        return requests.size();
    }

    /** The parsed JSON body of the last request. */
    Map<String, Object> lastBody() {
        return Json.parseObject(lastRequest().bodyText());
    }

    /** The parsed JSON body of the request at the given position. */
    Map<String, Object> bodyAt(int index) {
        return Json.parseObject(request(index).bodyText());
    }

    /** The raw query string of the last request. */
    String lastQuery() {
        String query = lastRequest().uri().getRawQuery();

        return query == null ? "" : query;
    }
}
