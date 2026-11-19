package co.nitida;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** docs/openapi.yaml must describe exactly the routes the backend serves. */
class OpenApiContractTest extends IntegrationTest {

    private static final Pattern OPENAPI_PATH = Pattern.compile("^  (/api/v1\\S+):\\s*$");
    private static final Pattern OPENAPI_METHOD = Pattern.compile("^    (get|post|patch|put|delete):\\s*$");

    @Autowired
    List<RouterFunction<ServerResponse>> routers;

    private static final Path OPENAPI = Path.of("../docs/openapi.yaml");

    @Test
    void openApiPathsMatchServedRoutes() throws Exception {
        var documented = openApiOperations(OPENAPI);
        var served = ApiContractTest.servedRoutes(routers);
        served.add("POST /api/v1/auth/logout");

        assertThat(documented).as("OpenAPI paths missing from backend").isSubsetOf(served);
        assertThat(served).as("backend routes missing from OpenAPI").isSubsetOf(documented);
    }

    static Set<String> openApiOperations(Path yaml) throws Exception {
        var documented = new TreeSet<String>();
        String currentPath = null;
        for (String line : Files.readAllLines(yaml)) {
            var pathMatch = OPENAPI_PATH.matcher(line);
            if (pathMatch.matches()) {
                currentPath = pathMatch.group(1);
                continue;
            }
            var methodMatch = OPENAPI_METHOD.matcher(line);
            if (methodMatch.matches() && currentPath != null) {
                documented.add(methodMatch.group(1).toUpperCase() + " " + currentPath);
            }
        }
        return documented;
    }
}