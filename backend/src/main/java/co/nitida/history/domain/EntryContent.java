package co.nitida.history.domain;

import co.nitida.shared.Json;
import co.nitida.shared.crypto.Sha256;
import co.nitida.shared.crypto.SecureTokens;
import co.nitida.shared.events.OrderDelivered;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * Public and private content of an entry (section 8.3). The private content (prices, serials in clear, mechanic,
 * photo hashes, a random salt) is committed by privateHash inside the signed public content (selective disclosure).
 * No personal data ever goes in either (RN-22).
 */
public record EntryContent(String privateJson, String privateHash, LongFunction<Map<String, Object>> publicForSeq) {

    private static Map<String, Object> base(UUID motorcycleId, long seq, EntryType type, UUID workshopId,
                                            boolean verified, Instant occurredAt, String privateHash) {
        var m = new LinkedHashMap<String, Object>();
        m.put("v", 1);
        m.put("motorcycleId", motorcycleId.toString());
        m.put("seq", seq);
        m.put("type", type.name());
        m.put("workshopId", workshopId.toString());
        m.put("workshopVerified", verified);
        m.put("occurredAt", occurredAt.truncatedTo(ChronoUnit.SECONDS).toString());
        m.put("privateHash", privateHash);
        return m;
    }

    /** privateHash = base64url(SHA-256(JCS(private))); the salt is one of the private fields. */
    private static EntryContent of(Map<String, Object> privateMap, LongFunction<Map<String, Object>> publicForSeq) {
        privateMap.put("salt", Base64.getUrlEncoder().withoutPadding().encodeToString(SecureTokens.randomBytes(16)));
        String canonicalPrivate = JsonCanonicalizer.canonicalize(privateMap);
        String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(Sha256.digest(canonicalPrivate));
        return new EntryContent(canonicalPrivate, hash, seq -> publicForSeq.apply(seq));
    }

    public static EntryContent initialRegistration(UUID motorcycleId, UUID workshopId, boolean verified, int declaredMileage, Instant at) {
        var priv = new LinkedHashMap<String, Object>();
        priv.put("declaredBy", workshopId.toString());
        return withHash(priv, (seq, privateHash) -> {
            var m = base(motorcycleId, seq, EntryType.INITIAL_REGISTRATION, workshopId, verified, at, privateHash);
            m.put("mileage", declaredMileage);
            m.put("mileageDeclared", true); // declared, not verified
            return m;
        });
    }

    public static EntryContent service(UUID motorcycleId, boolean verified, OrderDelivered e) {
        var priv = new LinkedHashMap<String, Object>();
        priv.put("orderId", e.orderId().toString());
        priv.put("mechanicId", e.mechanicId() == null ? null : e.mechanicId().toString());
        priv.put("laborCost", e.laborCost());
        priv.put("total", e.total());
        priv.put("photoHashes", new ArrayList<>(e.photoHashes()));
        var privParts = new ArrayList<Object>();
        for (var p : e.parts()) {
            var pp = new LinkedHashMap<String, Object>();
            pp.put("name", p.name());
            pp.put("reference", p.reference());
            pp.put("unitPrice", p.unitPrice());
            pp.put("serial", p.serial());
            pp.put("authenticityMethod", p.authenticityMethod());
            privParts.add(pp);
        }
        priv.put("parts", privParts);
        return withHash(priv, (seq, privateHash) -> {
            var m = base(motorcycleId, seq, EntryType.SERVICE, e.workshopId(), verified, e.deliveredAt(), privateHash);
            m.put("mileage", e.mileage());
            m.put("diagnosis", e.diagnosis());
            m.put("jobs", e.jobs().stream().map(j -> Map.<String, Object>of("type", j.type(), "description", j.description())).toList());
            var parts = new ArrayList<Object>();
            for (var p : e.parts()) {
                var pm = new LinkedHashMap<String, Object>();
                pm.put("name", p.name());
                pm.put("brand", p.brand());
                pm.put("condition", p.condition());
                pm.put("quantity", p.quantity());
                pm.put("productCode", p.productCode());
                pm.put("evidenceLevel", p.evidenceLevel());
                pm.put("serialHash", p.serial() == null ? null : Sha256.hexOf(p.serial())); // RN-97: hash, never the serial
                pm.put("hasPhotoEvidence", p.hasAuthenticityPhoto() || p.hasReplacementPhoto());
                parts.add(pm);
            }
            m.put("parts", parts);
            var warranty = new LinkedHashMap<String, Object>();
            warranty.put("days", e.warrantyDays());
            warranty.put("km", e.warrantyKm());
            m.put("warranty", e.warrantyDays() == null && e.warrantyKm() == null ? null : warranty);
            m.put("amends", null);
            return m;
        });
    }

    public static EntryContent amendment(UUID motorcycleId, UUID workshopId, boolean verified, Instant at, UUID authorId,
                                         long amendsSeq, String reason, String diagnosis, Integer mileage) {
        var priv = new LinkedHashMap<String, Object>();
        priv.put("authorId", authorId.toString());
        return withHash(priv, (seq, privateHash) -> {
            var m = base(motorcycleId, seq, EntryType.AMENDMENT, workshopId, verified, at, privateHash);
            m.put("amends", Map.of("seq", amendsSeq));
            m.put("reason", reason);
            var corrected = new LinkedHashMap<String, Object>();
            if (diagnosis != null) corrected.put("diagnosis", diagnosis);
            if (mileage != null) corrected.put("mileage", mileage);
            m.put("corrected", corrected);
            return m;
        });
    }

    public static EntryContent odometerChange(UUID motorcycleId, UUID workshopId, boolean verified, Instant at, UUID authorId,
                                              int newMileage, String reason, String photoHash) {
        var priv = new LinkedHashMap<String, Object>();
        priv.put("authorId", authorId.toString());
        priv.put("photoHash", photoHash);
        return withHash(priv, (seq, privateHash) -> {
            var m = base(motorcycleId, seq, EntryType.ODOMETER_CHANGE, workshopId, verified, at, privateHash);
            m.put("mileage", newMileage); // new base reading (RN-63)
            m.put("reason", reason);
            m.put("hasPhotoEvidence", true);
            return m;
        });
    }

    private interface PublicBuilder { Map<String, Object> build(long seq, String privateHash); }

    private static EntryContent withHash(Map<String, Object> privateMap, PublicBuilder builder) {
        var tmp = of(privateMap, seq -> Map.of());
        return new EntryContent(tmp.privateJson(), tmp.privateHash(), seq -> builder.build(seq, tmp.privateHash()));
    }

    /** Parses the stored private JSON (used to check authorship of an entry). */
    public static Map<?, ?> parse(String json) {
        return (Map<?, ?>) Json.mapper().readValue(json, Object.class);
    }
}
