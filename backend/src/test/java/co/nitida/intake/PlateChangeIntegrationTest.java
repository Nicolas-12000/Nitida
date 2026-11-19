package co.nitida.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.OrderFlowTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RN-14: the plate can change; the motorcycle keeps its id and the old plate stays in the plate history. */
class PlateChangeIntegrationTest extends OrderFlowTest {

    private String plateOf(UUID moto) {
        return jdbc.sql("SELECT current_plate FROM motorcycles.motorcycle WHERE id = ?").param(moto).query(String.class).single();
    }

    private org.springframework.test.web.servlet.ResultActions change(Session who, UUID ws, UUID moto, String plate) throws Exception {
        return call(HttpMethod.PATCH, "/api/v1/motorcycles/" + moto + "/plate", who, ws, Map.of("plate", plate));
    }

    @Test
    void theMotorcycleKeepsItsIdAndItsPlatesAreRecorded() throws Exception {
        var s = newShop();
        var v = openOrder(s, 100);
        String old = plateOf(v.motorcycleId());
        String fresh = nextPlate();

        change(s.desk(), s.ws(), v.motorcycleId(), " " + fresh.toLowerCase() + " ").andExpect(status().isNoContent());

        assertThat(plateOf(v.motorcycleId())).isEqualTo(fresh);
        var rows = jdbc.sql("SELECT plate, valid_to FROM motorcycles.plate_history WHERE motorcycle_id = ? ORDER BY valid_from, plate")
                .param(v.motorcycleId()).query().listOfRows();
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> old.equals(r.get("plate"))).singleElement().satisfies(r -> assertThat(r.get("valid_to")).isNotNull());
        assertThat(rows).filteredOn(r -> fresh.equals(r.get("plate"))).singleElement().satisfies(r -> assertThat(r.get("valid_to")).isNull());
        assertThat(jdbc.sql("SELECT count(*) FROM audit.audit_event WHERE workshop_id = ? AND action = 'PLATE_CHANGED'").param(s.ws()).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void sendingTheSamePlateChangesNothing() throws Exception {
        var s = newShop();
        var v = openOrder(s, 100);
        change(s.owner(), s.ws(), v.motorcycleId(), plateOf(v.motorcycleId())).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT count(*) FROM motorcycles.plate_history WHERE motorcycle_id = ?").param(v.motorcycleId()).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void invalidPlateAndPlateOfAnotherMotorcycleAreRejected() throws Exception {
        var s = newShop();
        var a = openOrder(s, 100);
        var b = openOrder(s, 100);
        change(s.desk(), s.ws(), a.motorcycleId(), "AB123D").andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_PLATE"));
        change(s.desk(), s.ws(), a.motorcycleId(), plateOf(b.motorcycleId())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATE_IN_USE"));
        assertThat(plateOf(a.motorcycleId())).isNotEqualTo(plateOf(b.motorcycleId()));
    }

    @Test
    void onlyAWorkshopThatAttendedTheMotorcycleAndOnlyDeskOrOwnerMayChangeIt() throws Exception {
        var s = newShop();
        var v = openOrder(s, 100);
        String before = plateOf(v.motorcycleId());
        var stranger = newShop();
        change(stranger.owner(), stranger.ws(), v.motorcycleId(), nextPlate()).andExpect(status().isNotFound());
        change(s.mechanic(), s.ws(), v.motorcycleId(), nextPlate()).andExpect(status().isForbidden());
        assertThat(plateOf(v.motorcycleId())).isEqualTo(before);
    }
}
