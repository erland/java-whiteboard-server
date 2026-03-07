package info.isaksson.erland.whiteboard.ws;

record WsConnectionContext(String boardId,
                           String connectionId,
                           String wsSessionId,
                           String correlationId,
                           String effectiveUserId,
                           String permission,
                           String inviteToken) {
}
