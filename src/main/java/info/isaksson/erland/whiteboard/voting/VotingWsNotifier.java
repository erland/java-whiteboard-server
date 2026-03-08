package info.isaksson.erland.whiteboard.voting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import info.isaksson.erland.whiteboard.ws.WsMessage;
import info.isaksson.erland.whiteboard.ws.WsOutboundSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VotingWsNotifier {

    private final ObjectMapper mapper;
    private final WsOutboundSupport outboundSupport;

    @Inject
    public VotingWsNotifier(ObjectMapper mapper, WsOutboundSupport outboundSupport) {
        this.mapper = mapper;
        this.outboundSupport = outboundSupport;
    }

    public void sessionOpened(VotingSession session, VotingResults publicResults) {
        ObjectNode payload = sessionPayload(session);
        payload.set("results", resultsPayload(publicResults));
        outboundSupport.broadcastEphemeral(session.boardId(), new WsMessage.Ephemeral(
                session.boardId(),
                "server",
                session.createdByUserId(),
                "voting-session-opened",
                payload,
                false));
    }

    public void sessionClosed(VotingSession session, VotingResults publicResults) {
        ObjectNode payload = sessionPayload(session);
        payload.set("results", resultsPayload(publicResults));
        outboundSupport.broadcastEphemeral(session.boardId(), new WsMessage.Ephemeral(
                session.boardId(),
                "server",
                session.createdByUserId(),
                "voting-session-closed",
                payload,
                false));
    }

    public void resultsRevealed(VotingSession session, VotingResults publicResults) {
        ObjectNode payload = sessionPayload(session);
        payload.set("results", resultsPayload(publicResults));
        outboundSupport.broadcastEphemeral(session.boardId(), new WsMessage.Ephemeral(
                session.boardId(),
                "server",
                session.createdByUserId(),
                "voting-results-revealed",
                payload,
                false));
    }

    public void votesUpdated(VotingSession session, VotingResults publicResults, String actorUserId) {
        ObjectNode payload = sessionPayload(session);
        payload.put("actorUserId", actorUserId);
        payload.set("results", resultsPayload(publicResults));
        outboundSupport.broadcastEphemeral(session.boardId(), new WsMessage.Ephemeral(
                session.boardId(),
                "server",
                actorUserId,
                "voting-votes-updated",
                payload,
                false));
    }

    private ObjectNode sessionPayload(VotingSession session) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("sessionId", session.id());
        payload.put("state", session.state().storageValue());
        payload.put("scopeType", session.scopeType().storageValue());
        if (session.scopeRef() == null) {
            payload.putNull("scopeRef");
        } else {
            payload.put("scopeRef", session.scopeRef());
        }
        if (session.updatedAt() == null) {
            payload.putNull("updatedAt");
        } else {
            payload.put("updatedAt", session.updatedAt().toString());
        }
        return payload;
    }

    private ObjectNode resultsPayload(VotingResults results) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("identitiesHidden", results.identitiesHidden());
        payload.put("progressHidden", results.progressHidden());
        ObjectNode totals = payload.putObject("totalsByTarget");
        results.totalsByTarget().forEach(totals::put);
        payload.put("visibleVoteCount", results.visibleVotes().size());
        return payload;
    }
}
