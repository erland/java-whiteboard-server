package info.isaksson.erland.whiteboard.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

@ApplicationScoped
class WsOpenAcceptor {

    private final WsLimits limits;

    @Inject
    WsOpenAcceptor(WsLimits limits) {
        this.limits = limits;
    }

    WsConnectionContext accept(Session session,
                               WsConnectionContext context,
                               BoardJoinAuthorizer.JoinDecision decision) {
        session.getUserProperties().put(WsSessionProps.USER_ID, decision.effectiveUserId());
        session.getUserProperties().put(WsSessionProps.PERMISSION, decision.permission());
        session.getUserProperties().put(WsSessionProps.RATE_LIMITER,
                new TokenBucketRateLimiter(limits.burst(), limits.ratePerSecond()));
        session.getUserProperties().put(WsSessionProps.EPHEMERAL_RATE_LIMITER,
                new TokenBucketRateLimiter(limits.ephemeralBurst(), limits.ephemeralRatePerSecond()));
        session.getUserProperties().put(WsSessionProps.REACTION_RATE_LIMITER,
                new TokenBucketRateLimiter(limits.reactionBurst(), limits.reactionRatePerSecond()));
        return new WsConnectionContext(
                context.boardId(),
                context.connectionId(),
                context.wsSessionId(),
                context.correlationId(),
                decision.effectiveUserId(),
                decision.permission(),
                context.inviteToken());
    }
}
