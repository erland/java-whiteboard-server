package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;

@ApplicationScoped
class WsOpenAuthorizer {

    private final BoardJoinAuthorizer authorizer;
    private final WsAuthResolver authResolver;
    private final WsSessionRegistry sessionRegistry;
    private final WsContractSupport contractSupport;
    private final WsLimits limits;
    private final WsMetrics metrics;
    private final WsOutboundSupport outboundSupport;

    @Inject
    WsOpenAuthorizer(BoardJoinAuthorizer authorizer,
                     WsAuthResolver authResolver,
                     WsSessionRegistry sessionRegistry,
                     WsContractSupport contractSupport,
                     WsLimits limits,
                     WsMetrics metrics,
                     WsOutboundSupport outboundSupport) {
        this.authorizer = authorizer;
        this.authResolver = authResolver;
        this.sessionRegistry = sessionRegistry;
        this.contractSupport = contractSupport;
        this.limits = limits;
        this.metrics = metrics;
        this.outboundSupport = outboundSupport;
    }

    BoardJoinAuthorizer.JoinDecision authorizeOrReject(Session session, WsConnectionContext context) {
        var versionDecision = contractSupport.check(session);
        if (!versionDecision.allowed()) {
            metrics.incRejected("incompatible_protocol");
            outboundSupport.send(session, new WsMessage.Error(versionDecision.code(), versionDecision.message(), contractSupport.protocolVersion(), contractSupport.capabilities()));
            outboundSupport.close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Incompatible protocol");
            return null;
        }

        BoardJoinAuthorizer.JoinDecision decision = authorizer.authorize(
                context.boardId(),
                authResolver.resolveUserId(session),
                context.inviteToken());
        if (!decision.allowed()) {
            metrics.incRejected("not_allowed");
            outboundSupport.close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "Not allowed");
            return null;
        }

        if (sessionRegistry.connectionCount(context.boardId()) >= limits.maxConnectionsPerBoard()) {
            metrics.incRejected("board_connection_limit");
            outboundSupport.close(session, CloseReason.CloseCodes.TRY_AGAIN_LATER, "Board connection limit reached");
            return null;
        }

        return decision;
    }
}
