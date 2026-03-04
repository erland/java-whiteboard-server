package info.isaksson.erland.whiteboard.persistence;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@IfBuildProfile("test")
public class InMemoryBoardPermissionsRepository implements BoardPermissionsRepository {

    // key = boardId|userId
    private final ConcurrentHashMap<String, String> roles = new ConcurrentHashMap<>();

    @Override
    public void upsert(String boardId, String userId, String role) {
        if (boardId == null || userId == null) throw new IllegalArgumentException("boardId and userId required");
        roles.put(boardId + "|" + userId, role);
    }

    @Override
    public Optional<String> findRole(String boardId, String userId) {
        if (boardId == null || userId == null) return Optional.empty();
        return Optional.ofNullable(roles.get(boardId + "|" + userId));
    }

    @Override
    public List<String> listBoardIdsForUser(String userId) {
        if (userId == null) return List.of();
        List<String> out = new ArrayList<>();
        String suffix = "|" + userId;
        for (String key : roles.keySet()) {
            if (key.endsWith(suffix)) {
                out.add(key.substring(0, key.length() - suffix.length()));
            }
        }
        return out;
    }
}
