# WebSocket Protocol Specification

## Purpose

This document specifies the realtime WebSocket protocol used by `java-whiteboard-server` for collaborative board sessions.

It complements the generated REST API documentation. The REST API covers board metadata, snapshots, and invites. This document covers the bidirectional realtime protocol used at runtime by connected whiteboard clients.

## Endpoint

WebSocket endpoint:

```text
/ws/boards/{boardId}
```

Path parameter:
- `boardId`: target board identifier.

## Connection model

A client opens a WebSocket connection directly to the board-specific endpoint.

On successful join, the server:
- authorizes the connection
- registers the session against the board
- adds the participant to board presence
- sends an initial `joined` message to the new client
- broadcasts a `presence` message to all sessions connected to the board

The protocol is server-driven at connection time. There is no separate application-level `join` message from the client.

## Authentication and authorization

A connection is accepted only if one of the supported authorization paths succeeds.

### Supported authorization paths

#### 1. Authenticated user principal
If the WebSocket session already has an authenticated principal, the server uses it as the primary user identity source.

Resolution behavior:
- if the principal is a JWT, the server prefers `preferred_username`
- otherwise it falls back to JWT subject
- if the principal is not a JWT, the server uses `principal.getName()`

#### 2. Bearer token from handshake metadata
If no principal is present, the server attempts to resolve a bearer token captured during handshake processing.

Expected format:

```text
Authorization: Bearer <token>
```

#### 3. Access token query parameter
If no principal and no bearer token are available, the server checks the query parameter:

```text
access_token=<jwt>
```

#### 4. Invite token query parameter
Unauthenticated guest-style access can be granted through an invite token:

```text
invite=<invite-token>
```

### Authorization rules

A join is allowed only when all required checks pass.

#### Board checks
The board must:
- exist
- not be deleted

If this check fails, the server rejects the connection without exposing whether the board exists.

#### Authenticated user access
If a user identity is resolved, access is granted when the user:
- is the board owner, or
- has readable shared access to the board

Effective permission values may include:
- `owner`
- `editor`
- `viewer`

#### Invite-based access
If no user identity is resolved and an invite token is present, access is granted only when:
- the invite token resolves to a valid invite
- the invite is not revoked
- the invite is not expired
- the invite has not exceeded `maxUses`
- the invite belongs to the same `boardId`

When invite-based access succeeds:
- the invite usage counter is incremented during authorization
- the effective user id becomes `invite:<inviteId>`
- the effective permission is derived from the invite permission

### Rejected connections
If authorization fails, the server closes the connection with:
- close code: `VIOLATED_POLICY`
- reason: `Not allowed`

The server intentionally avoids detailed failure reasons in order to reduce information leakage.

## Session metadata assigned by the server

For an accepted connection, the server assigns and stores session metadata including:
- board id
- connection id
- WebSocket session id
- effective user id
- effective permission
- correlation id, when present from handshake processing
- per-session rate limiter

### Generated identifiers
If not already present, the server generates:
- `connectionId`: UUID
- `wsSessionId`: UUID

## Runtime limits

The server enforces configurable runtime limits.

Current configuration keys:
- `whiteboard.limits.ws.max-message-bytes`
- `whiteboard.limits.ws.rate.per-second`
- `whiteboard.limits.ws.rate.burst`
- `whiteboard.limits.ws.max-connections-per-board`

Default values:
- max message bytes: `65536`
- rate per second: `20`
- burst: `40`
- max connections per board: `64`

### Board connection limit
If the number of active board connections is already at the configured limit, the server rejects the new connection with:
- close code: `TRY_AGAIN_LATER`
- reason: `Board connection limit reached`

## Message model

The protocol uses JSON text messages.

Current server message families:
- `joined`
- `presence`
- `op`
- `ephemeral`
- `error`

Compatibility notes:
- clients may send `protocolVersion` as a query parameter or `X-Whiteboard-Protocol-Version` as a handshake header
- when the client provides an unsupported version, the server sends an `error` with code `INCOMPATIBLE_PROTOCOL` and closes the connection
- the `joined` message includes `protocolVersion` and `capabilities` so clients can adapt safely
- ephemeral event handling may be disabled with the `whiteboard.features.ws.ephemeral.enabled` toggle

## Server-to-client messages

### `joined`
Sent only to the newly accepted session after successful authorization and registration.

Purpose:
- confirms the connection is active
- tells the client who it is connected as
- provides latest snapshot bootstrap information
- provides the current presence list

Example:

```json
{
  "type": "joined",
  "boardId": "board-1",
  "yourUserId": "alice",
  "latestSnapshotVersion": 7,
  "latestSnapshot": {
    "shapes": ["a"]
  },
  "users": [
    {
      "userId": "alice",
      "joinedAt": "2026-01-01T10:15:30Z"
    }
  ],
  "wsSessionId": "6cbac4c0-4f80-4e79-a3af-7d6d4f24c3e9",
  "correlationId": "corr-1"
}
```

Fields:
- `type`: always `joined`
- `boardId`: board identifier
- `yourUserId`: effective user id for this session
- `latestSnapshotVersion`: latest persisted snapshot version, or `null`
- `latestSnapshot`: latest persisted snapshot JSON payload, or `null`
- `users`: current board presence list
- `wsSessionId`: server-generated logical WebSocket session id
- `correlationId`: optional correlation id propagated from handshake processing

Notes:
- `latestSnapshotVersion` and `latestSnapshot` are both omitted as meaningful values when no snapshot exists
- the current user is already included in `users` because presence is registered before the `joined` message is sent

### `presence`
Broadcast to all currently connected sessions for a board whenever presence changes.

Typical triggers:
- successful join
- disconnect / close

Example:

```json
{
  "type": "presence",
  "boardId": "board-1",
  "users": [
    {
      "userId": "alice",
      "joinedAt": "2026-01-01T10:15:30Z"
    },
    {
      "userId": "bob",
      "joinedAt": "2026-01-01T10:16:02Z"
    }
  ]
}
```

Fields:
- `type`: always `presence`
- `boardId`: board identifier
- `users`: current board presence list

Presence semantics:
- the list contains unique connection-presence entries represented by user id and joined time
- multiple simultaneous connections can result in multiple presence entries for the same logical user if they are tracked as separate connections internally
- ordering should not be relied on as a stable contract

### `op`
Broadcast to all sessions connected to the board when a non-viewer client publishes an operation.

Example:

```json
{
  "type": "op",
  "boardId": "board-1",
  "seq": 1,
  "from": "alice",
  "op": {
    "kind": "add",
    "id": "shape-1"
  }
}
```

Fields:
- `type`: always `op`
- `boardId`: board identifier
- `seq`: server-assigned monotonically increasing sequence number for the board
- `from`: effective user id of the publishing session
- `op`: opaque client operation payload

Sequencing rules:
- the server assigns sequence numbers per board
- sequence numbers are monotonic within a board
- clients should treat `seq` as the canonical ordering value for received operations


### `ephemeral`
Broadcast to all currently connected sessions for a board when a participant publishes a non-durable collaboration signal or when that signal is cleared during disconnect cleanup.

Supported event types in the current foundation:
- `cursor`
- `viewport`
- `follow`
- `presence-meta`

Example publish/broadcast payload:

```json
{
  "type": "ephemeral",
  "boardId": "board-1",
  "connectionId": "conn-1",
  "from": "alice",
  "eventType": "cursor",
  "payload": {
    "x": 120,
    "y": 340
  },
  "cleared": false
}
```

Disconnect cleanup uses the same message family with `cleared: true` so consumers can drop session-scoped signal state without touching durable board state.

Rules:
- ephemeral events are never sequenced as board operations
- ephemeral events are never written to snapshots
- viewers may emit `cursor`, `viewport`, and `presence-meta`
- only owners/editors may emit `follow`
- payload must be a JSON object

### `error`
Sent to a session when the server detects a request or protocol problem.

Example:

```json
{
  "type": "error",
  "code": "BAD_REQUEST",
  "message": "Invalid JSON."
}
```

Fields:
- `type`: always `error`
- `code`: machine-readable error code
- `message`: human-readable message

Known error codes currently used by the implementation:
- `BAD_REQUEST`
- `VALIDATION_ERROR`
- `FORBIDDEN`
- `RATE_LIMITED`
- `MESSAGE_TOO_LARGE`

## Client-to-server messages

### `op`
The only currently supported client-originated message type is `op`.

Example:

```json
{
  "type": "op",
  "op": {
    "kind": "add",
    "id": "shape-1"
  }
}
```

Fields:
- `type`: must be `op`
- `op`: required opaque JSON payload representing the client operation

Validation rules:
- message must be valid JSON
- `type` must equal `op` to trigger operation handling
- `op` must be present and non-null
- session must already be accepted and fully initialized
- effective permission must not be `viewer`

Behavior:
- on success, the server assigns a sequence number and broadcasts an `op` message to all board sessions, including the sender
- the server does not currently send a separate ack message

### Unknown message types
If the message JSON is valid but `type` is not `op`, the server currently ignores the message.

There is no error response for unknown message types in the current implementation.

## Error and close behavior

### Invalid JSON
If a client sends malformed JSON:
- server sends `error` with code `BAD_REQUEST`
- connection remains open

### Missing `op`
If a client sends `{"type":"op"}` without a valid `op` payload:
- server sends `error` with code `VALIDATION_ERROR`
- connection remains open

### Viewer attempts to publish operations
If a session with effective permission `viewer` sends an `op` message:
- server sends `error` with code `FORBIDDEN`
- connection remains open

### Rate limit exceeded
If a session exceeds the token-bucket message rate limit:
- server sends `error` with code `RATE_LIMITED`
- connection remains open

### Message too large
If the UTF-8 encoded message exceeds `whiteboard.limits.ws.max-message-bytes`:
- server sends `error` with code `MESSAGE_TOO_LARGE`
- server closes the connection with:
  - close code: `TOO_BIG`
  - reason: `Message too large`

### Session not fully initialized
If a message arrives before the session has required board/user/permission metadata:
- server closes the connection with:
  - close code: `VIOLATED_POLICY`
  - reason: `Not allowed`

### Unhandled endpoint error
If the endpoint error handler is triggered:
- the server closes the session with:
  - close code: `UNEXPECTED_CONDITION`
  - reason: `Error`

## Presence lifecycle

### On successful open
The server performs these actions in order:
1. initialize session metadata
2. authorize the join
3. enforce board connection limit
4. register the session
5. add the connection to presence
6. send `joined` to the new session
7. broadcast `presence` to all sessions on the board

### On close
If the session had an associated board connection:
1. remove the connection from presence
2. unregister the session
3. broadcast updated `presence`

## Snapshot bootstrap behavior

The `joined` message includes the latest persisted snapshot if one exists.

Bootstrap source:
- latest snapshot is loaded from `SnapshotsRepository.getLatest(boardId)`

If snapshot loading fails unexpectedly:
- the server suppresses the failure
- the connection still succeeds
- `latestSnapshotVersion` and `latestSnapshot` may be absent as meaningful values

## Invite-join semantics

For invite-based joins:
- the effective user id is synthetic: `invite:<inviteId>`
- permission is taken from invite policy normalization
- invite usage is recorded during authorization, before the session is fully active

Clients should treat invite-based users as guest-like participants unless their product UX defines a richer representation.

## Non-goals / current limitations

The current realtime protocol does not define:
- reconnect tokens
- resume/replay protocol
- server-side op persistence in the WebSocket flow
- delta sync negotiation
- optimistic ack/commit phases
- cursor/selection presence messages
- ping/pong application-level protocol messages
- explicit version negotiation

Clients should therefore treat the protocol as a lightweight realtime broadcast channel with snapshot bootstrap.

## Implementation references

This document reflects the behavior implemented in:
- `BoardWebSocketEndpoint`
- `WsLifecycleService`
- `WsInboundMessageHandler`
- `WsOutboundSupport`
- `BoardJoinAuthorizer`
- `WsAuthResolver`
- `WsLimits`

It is also aligned with the current WebSocket-focused tests:
- `BoardWebSocketEndpointTest`
- `WsLifecycleServiceTest`
- `BoardJoinAuthorizerTest`
- `TokenBucketRateLimiterTest`
- `PresenceHubTest`
- `BoardOpSequencerTest`
