package info.isaksson.erland.whiteboard.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import info.isaksson.erland.whiteboard.domain.Invite;
import io.quarkus.arc.profile.IfBuildProfile;

@ApplicationScoped
@IfBuildProfile("test")
@Priority(1)
public class InMemoryInvitesRepository implements InvitesRepository {

    private final ConcurrentHashMap<String, Invite> invites = new ConcurrentHashMap<>();

    @Override
    public Invite create(Invite invite) {
        Instant now = Instant.now();
        Invite created = new Invite(
                invite.id(),
                invite.boardId(),
                invite.tokenHash(),
                invite.permission(),
                invite.expiresAt(),
                invite.maxUses(),
                invite.uses(),
                invite.revokedAt(),
                now
        );
        invites.put(created.id(), created);
        return created;
    }

    @Override
    public List<Invite> listForBoard(String boardId) {
        return invites.values().stream()
                .filter(i -> i.boardId().equals(boardId))
                .sorted(Comparator.comparing(Invite::createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<Invite> findById(String inviteId) {
        return Optional.ofNullable(invites.get(inviteId));
    }

    @Override
    public Optional<Invite> findByTokenHash(String tokenHash) {
        return invites.values().stream().filter(i -> i.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public boolean revoke(String inviteId) {
        return invites.computeIfPresent(inviteId, (k, existing) -> {
            if (existing.revokedAt() != null) return existing;
            return new Invite(existing.id(), existing.boardId(), existing.tokenHash(), existing.permission(),
                    existing.expiresAt(), existing.maxUses(), existing.uses(), Instant.now(), existing.createdAt());
        }) != null;
    }

    @Override
    public Optional<Invite> incrementUses(String inviteId) {
        Invite updated = invites.computeIfPresent(inviteId, (k, existing) -> {
            return new Invite(existing.id(), existing.boardId(), existing.tokenHash(), existing.permission(),
                    existing.expiresAt(), existing.maxUses(), existing.uses() + 1, existing.revokedAt(), existing.createdAt());
        });
        return Optional.ofNullable(updated);
    }
}
