package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.history.domain.ChainHead;
import co.nitida.history.domain.ChainSealer;
import co.nitida.history.domain.EntryContent;
import co.nitida.history.domain.EntryType;
import co.nitida.shared.crypto.Sha256;
import co.nitida.shared.events.OrderDelivered;
import co.nitida.shared.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** What goes into the signed public content and what must stay in the private one (RN-22, RN-73, RN-97). */
@SuppressWarnings("unchecked") // the public content is a JSON-like tree of maps and lists
class EntryContentTest {
    static final Instant AT = Instant.parse("2026-03-04T05:06:07.890Z");
    static final UUID MOTO = UuidV7.next();
    static final UUID WS = UuidV7.next();
    static final UUID AUTHOR = UuidV7.next();

    private static Map<String, Object> publicAt(EntryContent c, long seq) { return c.publicForSeq().apply(seq); }

    @Test
    void everyEntryCarriesTheCommonFieldsAndTheCommitmentToItsPrivatePart() {
        var c = EntryContent.initialRegistration(MOTO, WS, true, 1234, AT);
        var m = publicAt(c, 1);
        assertThat(m).containsEntry("v", 1).containsEntry("motorcycleId", MOTO.toString()).containsEntry("seq", 1L)
                .containsEntry("type", "INITIAL_REGISTRATION").containsEntry("workshopId", WS.toString())
                .containsEntry("workshopVerified", true).containsEntry("occurredAt", "2026-03-04T05:06:07Z")
                .containsEntry("privateHash", c.privateHash()).containsEntry("mileage", 1234).containsEntry("mileageDeclared", true);
        assertThat(c.privateHash()).isNotBlank();
        assertThat(c.privateJson()).contains("declaredBy").contains(WS.toString());
        assertThat(publicAt(c, 7)).as("the same content gets the sequence number it is sealed with").containsEntry("seq", 7L);
    }

    @Test
    void theSaltMakesTwoEqualContentsCommitToDifferentPrivateHashes() {
        var a = EntryContent.initialRegistration(MOTO, WS, false, 10, AT);
        var b = EntryContent.initialRegistration(MOTO, WS, false, 10, AT);
        assertThat(a.privateHash()).isNotEqualTo(b.privateHash());
        assertThat(a.privateJson()).contains("salt");
    }

    @Test
    void serviceEntryPublishesHashesAndNeverPricesNorClearSerials() {
        var part = new OrderDelivered.Part("Kit de arrastre", "REF-9", "Marca", "OEM", 2, 45_000L, "7701234567892", "SERIE-123",
                "UNIQUE_SERIAL", "QR leído", true, false);
        var plain = new OrderDelivered.Part("Aceite", null, "Otra", "AFTERMARKET", 1, 30_000L, null, null, "MANUAL", null, false, false);
        var delivered = new OrderDelivered(UuidV7.next(), WS, MOTO, 23_450, "Ruido en la transmisión al acelerar",
                List.of(new OrderDelivered.Job("OIL_CHANGE", "Cambio de aceite")), List.of(part, plain), 20_000L, 140_000L,
                List.of("hash-foto-1"), AUTHOR, 30, 1000, AT);
        var c = EntryContent.service(MOTO, false, delivered);
        var m = publicAt(c, 2);

        assertThat(m).containsEntry("type", "SERVICE").containsEntry("workshopVerified", false)
                .containsEntry("mileage", 23_450).containsEntry("diagnosis", "Ruido en la transmisión al acelerar")
                .containsEntry("amends", null);
        assertThat((List<Object>) m.get("jobs")).containsExactly(Map.of("type", "OIL_CHANGE", "description", "Cambio de aceite"));
        var parts = (List<Map<String, Object>>) m.get("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).containsEntry("name", "Kit de arrastre").containsEntry("brand", "Marca").containsEntry("condition", "OEM")
                .containsEntry("quantity", 2).containsEntry("productCode", "7701234567892").containsEntry("evidenceLevel", "UNIQUE_SERIAL")
                .containsEntry("serialHash", Sha256.hexOf("SERIE-123")).containsEntry("hasPhotoEvidence", true);
        assertThat(parts.get(1)).containsEntry("serialHash", null).containsEntry("hasPhotoEvidence", false);
        assertThat((Map<String, Object>) m.get("warranty")).containsEntry("days", 30).containsEntry("km", 1000);

        String publicJson = m.toString();
        assertThat(publicJson).doesNotContain("SERIE-123").doesNotContain("45000").doesNotContain("140000").doesNotContain("unitPrice")
                .doesNotContain(AUTHOR.toString());
        assertThat(c.privateJson()).contains("SERIE-123").contains("140000").contains("hash-foto-1").contains(AUTHOR.toString());
    }

    @Test
    void withoutWarrantyTheFieldIsNullAndEitherHalfIsEnoughToPublishIt() {
        var none = new OrderDelivered(UuidV7.next(), WS, MOTO, 1, "Diagnóstico de prueba", List.of(), List.of(), 0, 0, List.of(), null, null, null, AT);
        assertThat(publicAt(EntryContent.service(MOTO, true, none), 2)).containsEntry("warranty", null);
        var kmOnly = new OrderDelivered(UuidV7.next(), WS, MOTO, 1, "Diagnóstico de prueba", List.of(), List.of(), 0, 0, List.of(), null, null, 500, AT);
        assertThat((Map<String, Object>) publicAt(EntryContent.service(MOTO, true, kmOnly), 2).get("warranty")).containsEntry("km", 500).containsEntry("days", null);
        var daysOnly = new OrderDelivered(UuidV7.next(), WS, MOTO, 1, "Diagnóstico de prueba", List.of(), List.of(), 0, 0, List.of(), null, 15, null, AT);
        assertThat((Map<String, Object>) publicAt(EntryContent.service(MOTO, true, daysOnly), 2).get("warranty")).containsEntry("days", 15);
    }

    @Test
    void amendmentNamesWhatItCorrectsAndKeepsTheAuthorPrivate() {
        var c = EntryContent.amendment(MOTO, WS, true, AT, AUTHOR, 3, "Error de digitación del kilometraje", "Diagnóstico corregido", 5000);
        var m = publicAt(c, 4);
        assertThat(m).containsEntry("type", "AMENDMENT").containsEntry("reason", "Error de digitación del kilometraje");
        assertThat((Map<String, Object>) m.get("amends")).containsEntry("seq", 3L);
        assertThat((Map<String, Object>) m.get("corrected")).containsEntry("diagnosis", "Diagnóstico corregido").containsEntry("mileage", 5000);
        assertThat(m.toString()).doesNotContain(AUTHOR.toString());
        assertThat(c.privateJson()).contains(AUTHOR.toString());

        var onlyMileage = publicAt(EntryContent.amendment(MOTO, WS, true, AT, AUTHOR, 3, "Motivo suficiente", null, 5000), 4);
        assertThat((Map<String, Object>) onlyMileage.get("corrected")).containsOnlyKeys("mileage");
        var onlyDiagnosis = publicAt(EntryContent.amendment(MOTO, WS, true, AT, AUTHOR, 3, "Motivo suficiente", "Texto nuevo", null), 4);
        assertThat((Map<String, Object>) onlyDiagnosis.get("corrected")).containsOnlyKeys("diagnosis");
    }

    @Test
    void odometerChangeSetsTheNewBaseAndOnlyHashesThePhoto() {
        var c = EntryContent.odometerChange(MOTO, WS, true, AT, AUTHOR, 12, "Cambio de tablero por avería", "abc123photo");
        var m = publicAt(c, 5);
        assertThat(m).containsEntry("type", "ODOMETER_CHANGE").containsEntry("mileage", 12).containsEntry("reason", "Cambio de tablero por avería")
                .containsEntry("hasPhotoEvidence", true);
        assertThat(m.toString()).doesNotContain("abc123photo");
        assertThat(c.privateJson()).contains("abc123photo");
    }

    @Test
    void ownershipTransferHasNoNamesNoUsersAndIsAttestedAsVerified() {
        UUID transfer = UuidV7.next();
        var c = EntryContent.ownershipTransfer(MOTO, WS, AT, transfer);
        var m = publicAt(c, 6);
        assertThat(m).containsEntry("type", EntryType.OWNERSHIP_TRANSFER.name()).containsEntry("workshopVerified", true).containsEntry("workshopId", WS.toString());
        assertThat(c.privateJson()).contains(transfer.toString());
        assertThat(m.toString()).doesNotContain(transfer.toString());
    }

    @Test
    void parsedContentComesBackAsAMap() {
        assertThat(EntryContent.parse("{\"a\":1,\"b\":\"x\"}").get("b")).isEqualTo("x");
    }

    @Test
    void aNewChainStartsAtZeroWithTheGenesisHashAndAdvances() {
        var head = ChainHead.genesis(MOTO);
        assertThat(head.getLastSeq()).isZero();
        assertThat(head.getLastHash()).isEqualTo(ChainSealer.GENESIS_HASH);
        head.advance(3, new byte[] {1, 2, 3});
        assertThat(head.getLastSeq()).isEqualTo(3);
        assertThat(head.getLastHash()).containsExactly(1, 2, 3);
    }
}
