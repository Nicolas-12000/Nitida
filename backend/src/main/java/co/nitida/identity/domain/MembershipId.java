package co.nitida.identity.domain;

import co.nitida.shared.Role;
import java.io.Serializable;
import java.util.UUID;

public record MembershipId(UUID userId, UUID workshopId, Role role) implements Serializable {}
