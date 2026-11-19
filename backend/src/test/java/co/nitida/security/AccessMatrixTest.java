package co.nitida.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.support.OrderFlowTest;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

/**
 * A01 Broken Access Control: spec §3.1 role x endpoint matrix. Allowed roles must get past authorization (any answer
 * but 401/403; the random ids then end in 404/422 on purpose), forbidden roles get 403 and anonymous gets 401.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessMatrixTest extends OrderFlowTest {
    enum Who { OWNER, MECHANIC, FRONT_DESK }

    private static final Set<Who> ALL = EnumSet.allOf(Who.class);
    private static final Set<Who> OWNER = EnumSet.of(Who.OWNER);
    private static final Set<Who> OWNER_MECHANIC = EnumSet.of(Who.OWNER, Who.MECHANIC);
    private static final Set<Who> OWNER_DESK = EnumSet.of(Who.OWNER, Who.FRONT_DESK);

    /** Every primitive field any request record needs, so JSON binding never answers 400 before authorization runs. */
    private static final Map<String, Object> BODY = Map.ofEntries(Map.entry("version", 0), Map.entry("quantity", 1), Map.entry("unitPrice", 1),
            Map.entry("amount", 1), Map.entry("amendsSeq", 1), Map.entry("mileage", 1), Map.entry("consentAccepted", false),
            Map.entry("intakeAlertHours", 24), Map.entry("cashTolerance", 0), Map.entry("stalledDays", 7), Map.entry("counted", 0));

    private static final String ID = "00000000-0000-7000-8000-0000000000aa";

    private Shop shop;

    @BeforeAll
    void createShop() throws Exception {
        shop = newShop();
    }

    private static Arguments row(HttpMethod m, String path, Set<Who> allowed) {
        return Arguments.of(m, path, allowed);
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                // recepción
                row(HttpMethod.POST, "/api/v1/intakes", ALL),
                row(HttpMethod.POST, "/api/v1/intakes/" + ID + "/discard", ALL),
                row(HttpMethod.GET, "/api/v1/intakes", ALL),
                row(HttpMethod.PATCH, "/api/v1/motorcycles/" + ID + "/plate", OWNER_DESK),
                // clientes
                row(HttpMethod.POST, "/api/v1/customers", OWNER_DESK),
                row(HttpMethod.PATCH, "/api/v1/customers/" + ID, OWNER_DESK),
                row(HttpMethod.POST, "/api/v1/customers/" + ID + "/erasure", OWNER),
                // órdenes
                row(HttpMethod.POST, "/api/v1/orders", OWNER_MECHANIC),
                row(HttpMethod.PATCH, "/api/v1/orders/" + ID, OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/transitions", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/parts", OWNER_MECHANIC),
                row(HttpMethod.DELETE, "/api/v1/orders/" + ID + "/parts/" + ID, OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/parts/" + ID + "/serial", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/parts/" + ID + "/authenticity", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/photos", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/serials/" + ID + "/retire", OWNER_MECHANIC),
                // dinero y entrega
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/payments", OWNER_DESK),
                row(HttpMethod.POST, "/api/v1/payments/" + ID + "/void", OWNER_DESK),
                row(HttpMethod.POST, "/api/v1/orders/" + ID + "/delivery", OWNER_DESK),
                // catálogo
                row(HttpMethod.GET, "/api/v1/catalog/products/7701234567892", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/catalog/products", OWNER_MECHANIC),
                // caja y alertas: solo el dueño
                row(HttpMethod.GET, "/api/v1/cashdesk/counts/2026-01-01", OWNER),
                row(HttpMethod.POST, "/api/v1/cashdesk/counts/2026-01-01/close", OWNER),
                row(HttpMethod.POST, "/api/v1/cashdesk/counts/2026-01-01/reopen", OWNER),
                row(HttpMethod.GET, "/api/v1/cashdesk/alerts?status=PENDING", OWNER),
                row(HttpMethod.POST, "/api/v1/cashdesk/alerts/" + ID + "/resolve", OWNER),
                // historial
                row(HttpMethod.POST, "/api/v1/motorcycles/" + ID + "/history/amendments", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/motorcycles/" + ID + "/history/odometer-changes", OWNER_MECHANIC),
                row(HttpMethod.POST, "/api/v1/motorcycles/" + ID + "/links", OWNER_DESK),
                row(HttpMethod.DELETE, "/api/v1/links/" + ID, OWNER_DESK));
    }

    private Session as(Who who) {
        return switch (who) {
            case OWNER -> shop.owner();
            case MECHANIC -> shop.mechanic();
            case FRONT_DESK -> shop.desk();
        };
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrix")
    void rolesGetExactlyTheAccessTheSpecGives(HttpMethod method, String path, Set<Who> allowed) throws Exception {
        for (Who who : Who.values()) {
            int status = call(method, path, as(who), shop.ws(), BODY).andReturn().getResponse().getStatus();
            if (allowed.contains(who)) {
                assertThat(status).as("%s %s as %s must pass authorization", method, path, who).isNotIn(401, 403).isLessThan(500);
            } else {
                assertThat(status).as("%s %s as %s must be forbidden", method, path, who).isEqualTo(403);
            }
        }
        assertThat(call(method, path, null, null, BODY).andReturn().getResponse().getStatus())
                .as("%s %s anonymous", method, path).isEqualTo(401);
    }

    /** Workshop-scoped endpoints with someone else's workshop id: 404 whatever the role, never 403 (no existence oracle). */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrix")
    void aWorkshopThatIsNotYoursLooksNonExistent(HttpMethod method, String path, Set<Who> allowed) throws Exception {
        var outsider = newShop();
        int status = call(method, path, outsider.owner(), shop.ws(), BODY).andReturn().getResponse().getStatus();
        assertThat(status).as("%s %s with a foreign X-Workshop-Id", method, path).isEqualTo(404);
    }

    @Test
    void adminEndpointsAreForThePlatformAdminOnly() throws Exception {
        var admin = registerAdmin();
        var status = "/api/v1/admin/workshops/" + shop.ws() + "/status";
        for (Session s : new Session[] {shop.owner(), shop.mechanic(), shop.desk()}) {
            assertThat(call(HttpMethod.POST, status, s, null, Map.of("status", "VERIFIED")).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(call(HttpMethod.GET, "/api/v1/admin/workshops", s, null, null).andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(call(HttpMethod.GET, "/api/v1/admin/workshops", admin, null, null).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/v1/admin/workshops", null, null, null).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void workshopManagementIsForTheOwnerOnly() throws Exception {
        String base = "/api/v1/workshops/" + shop.ws();
        for (Session s : new Session[] {shop.mechanic(), shop.desk()}) {
            assertThat(call(HttpMethod.POST, base + "/invitations", s, shop.ws(), Map.of("email", "a@b.co", "role", "MECHANIC")).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(call(HttpMethod.GET, base + "/members", s, shop.ws(), null).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(call(HttpMethod.DELETE, base + "/members/" + UUID.randomUUID(), s, shop.ws(), null).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(call(HttpMethod.PATCH, base + "/settings", s, shop.ws(), BODY).andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(call(HttpMethod.GET, base + "/members", shop.owner(), shop.ws(), null).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    /** The owner portal needs a login but no workshop; a workshop role gives no access to someone else's motorcycle. */
    @Test
    void ownerPortalNeedsLoginAndOwnership() throws Exception {
        for (var r : new Object[][] {{HttpMethod.GET, "/api/v1/owner/motorcycles"}, {HttpMethod.POST, "/api/v1/owner/claims"},
                {HttpMethod.GET, "/api/v1/owner/motorcycles/" + ID + "/history"}, {HttpMethod.POST, "/api/v1/owner/motorcycles/" + ID + "/transfers"},
                {HttpMethod.POST, "/api/v1/owner/transfers/ABCDEFGH/accept"}, {HttpMethod.POST, "/api/v1/owner/motorcycles/" + ID + "/links"}}) {
            assertThat(call((HttpMethod) r[0], (String) r[1], null, null, BODY).andReturn().getResponse().getStatus()).as("anonymous %s", r[1]).isEqualTo(401);
        }
        assertThat(call(HttpMethod.GET, "/api/v1/owner/motorcycles/" + ID + "/history", shop.owner(), null, null).andReturn().getResponse().getStatus()).isEqualTo(404);
    }
}
