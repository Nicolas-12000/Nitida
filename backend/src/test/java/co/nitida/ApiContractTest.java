package co.nitida;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** docs/api.md is the contract handed to the frontend: it must list exactly the routes the backend serves. */
class ApiContractTest extends IntegrationTest {
    private static final Pattern DOC_ROW = Pattern.compile("^\\| `([A-Z]+) (/api/v1[^`]*)`");

    @Autowired List<RouterFunction<ServerResponse>> routers;

    @Test
    void everyRouteIsDocumentedAndEveryDocumentedRouteExists() throws Exception {
        var served = new TreeSet<String>();
        for (var router : routers) router.accept(new RouteCollector(served));

        var documented = new TreeSet<String>();
        for (String line : Files.readAllLines(Path.of("../docs/api.md"))) {
            var m = DOC_ROW.matcher(line);
            if (m.find()) documented.add(m.group(1) + " " + m.group(2));
        }

        assertThat(served).as("routes missing from docs/api.md").isSubsetOf(documented);
        assertThat(documented).as("routes in docs/api.md that the backend does not serve").isSubsetOf(served);
    }

    /** Walks RouterFunction DSL trees: nested path prefixes plus each route's method and path. */
    private static final class RouteCollector implements RouterFunctions.Visitor {
        private final Set<String> out;
        private final Deque<String> prefixes = new ArrayDeque<>();

        RouteCollector(Set<String> out) {
            this.out = out;
        }

        @Override
        public void startNested(RequestPredicate predicate) {
            prefixes.push(new PredicateParts(predicate).path);
        }

        @Override
        public void endNested(RequestPredicate predicate) {
            prefixes.pop();
        }

        @Override
        public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
            var parts = new PredicateParts(predicate);
            var prefix = new StringBuilder();
            prefixes.descendingIterator().forEachRemaining(prefix::append);
            for (HttpMethod m : parts.methods) out.add(m.name() + " " + prefix + parts.path);
        }

        @Override
        public void resources(Function<ServerRequest, Optional<Resource>> lookupFunction) {}

        @Override
        public void attributes(Map<String, Object> attributes) {}

        @Override
        public void unknown(RouterFunction<?> routerFunction) {}
    }

    @SuppressWarnings("removal") // pathExtension is still part of the interface we must implement
    private static final class PredicateParts implements RequestPredicates.Visitor {
        Set<HttpMethod> methods = Set.of();
        String path = "";

        PredicateParts(RequestPredicate predicate) {
            predicate.accept(this);
        }

        @Override public void method(Set<HttpMethod> methods) { this.methods = methods; }
        @Override public void path(String pattern) { this.path += pattern; }
        @Override public void pathExtension(String extension) {}
        @Override public void header(String name, String value) {}
        @Override public void param(String name, String value) {}
        @Override public void version(String version) {}
        @Override public void startAnd() {}
        @Override public void and() {}
        @Override public void endAnd() {}
        @Override public void startOr() {}
        @Override public void or() {}
        @Override public void endOr() {}
        @Override public void startNegate() {}
        @Override public void endNegate() {}
        @Override public void unknown(RequestPredicate predicate) {}
    }
}
