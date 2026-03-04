# Functional Specification — Whiteboard Collaboration Server

## 1. Purpose
Provide an in-house backend server for a web-based whiteboard application that supports:
- Real-time multi-user collaboration over WebSockets
- Board metadata management over HTTP APIs
- Sharing via invite links and access control
- Optional durable state via snapshots and operation logs
- Operational safeguards (limits, validation, observability)

This specification is **technology-agnostic** and describes required behaviors only.

## 2. Scope

### 2.1 In scope
- Authentication-based access for registered users
- Invite-link access for external participants (optional read/write permissions)
- WebSocket-based collaboration: join, presence, cursor updates, operation broadcast
- Board CRUD (create, list, rename, delete/archive)
- Snapshot storage and retrieval (durable state)
- Optional append-only operation log (for audit/replay)
- Rate limiting and payload limits
- Health endpoints and basic operational metrics/logging requirements

### 2.2 Out of scope
- Whiteboard rendering, canvas logic, or client-side tooling
- Payments/subscriptions
- Full-text search across board contents
- Complex organization/tenant management (can be added later)

## 3. Actors
- **Authenticated User**: a registered user who can own boards and collaborate.
- **Invited Participant**: a user accessing a board via an invite token.
- **Administrator**: operator role for maintenance and diagnostics.

## 4. Concepts & Definitions
- **Board**: a collaborative workspace with metadata (id, name, type, owner, timestamps).
- **Snapshot**: a persisted full representation of a board state at a point in time.
- **Operation (Op)**: a small incremental change emitted by the client (e.g., add/update/delete object).
- **Presence**: ephemeral information about connected participants (name/id, cursor, selection, status).
- **Session**: a single WebSocket connection from a participant to a board room.
- **Permissions**:
  - **Owner/Admin**: manage board, invites, and write operations.
  - **Editor**: can write ops and update presence.
  - **Viewer**: can read ops/presence, cannot write ops.

## 5. Functional Requirements

### 5.1 Authentication
1. The server must accept authenticated requests using bearer tokens.
2. The server must verify token integrity and expiration.
3. The server must extract a stable user identifier and display name (if available).
4. The server must support unauthenticated access **only** when a valid invite token is presented for a board.

### 5.2 Authorization
1. For each board access, the server must determine the participant's permission level:
   - Authenticated users: derived from board ownership and/or explicit sharing rules.
   - Invite participants: derived from invite token configuration.
2. Authorization must be enforced for:
   - HTTP endpoints
   - WebSocket join and every incoming operation message
3. Viewer permissions must prevent write operations but still allow receiving updates.

### 5.3 Board Management (HTTP)
Required endpoints (logical behavior; exact routes are implementation details):

#### 5.3.1 List boards
- Returns boards accessible to the authenticated user (owned and shared).
- Supports pagination.
- Returns metadata only (id, name, type, updatedAt, owner).

#### 5.3.2 Create board
- Creates a new board with name and type.
- Sets authenticated user as owner.
- Initializes an empty snapshot.

#### 5.3.3 Get board details
- Returns board metadata and current access level for the requesting principal.

#### 5.3.4 Rename/update metadata
- Owner (or admin) can update name and type.
- Updates `updatedAt`.

#### 5.3.5 Delete or archive board
- Owner (or admin) can delete or archive.
- If delete is supported, server must define behavior for snapshots and logs (hard delete vs soft delete).

### 5.4 Sharing & Invites (HTTP)
#### 5.4.1 Create invite
- Owner can create an invite for a board with:
  - permission: viewer/editor
  - optional expiry timestamp
  - optional max uses (or unlimited)
- Server returns an invite token suitable for embedding in a URL.

#### 5.4.2 List invites
- Owner can list active invites for a board.

#### 5.4.3 Revoke invite
- Owner can revoke an invite immediately; revoked invites must be rejected.

#### 5.4.4 Validate invite
- Server must support validating an invite token (for the client to show “valid/expired/revoked”).

### 5.5 Collaboration (WebSocket)
The server must support real-time collaboration per board room.

#### 5.5.1 Join flow
- Client initiates a WebSocket connection and requests to join a board room.
- Server validates authentication/invite token and determines permissions.
- On successful join, server must send:
  - current participant list (presence)
  - latest snapshot (or a snapshot version pointer)
  - a server-assigned connection/session id
  - optional server time/version counters

#### 5.5.2 Presence updates
- Participants can publish presence updates (cursor position, name, status).
- Server broadcasts presence updates to all participants in the same board room.
- Presence is ephemeral and cleared on disconnect/timeout.

#### 5.5.3 Operation broadcast
- Editor/owner participants can submit operations.
- Server must validate operations (schema + size + rate limits).
- Server assigns a monotonically increasing sequence number per board (or equivalent ordering mechanism).
- Server broadcasts accepted operations to all participants in the room (including sender) with sequence metadata.

#### 5.5.4 Snapshot persistence
- Server must persist snapshots:
  - periodically (server-defined), and/or
  - on explicit client request, and/or
  - after N operations
- Server must expose latest snapshot to new joiners.
- Server must define conflict rules when concurrent snapshot requests arrive:
  - last-write-wins with version checks, or
  - reject outdated snapshot updates.

#### 5.5.5 Reconnect and resync
- If a client reconnects:
  - server must allow re-join and receive the latest snapshot and/or missed operations since a known sequence.
- If missed-ops replay is not supported, server must at minimum send the latest snapshot.

#### 5.5.6 Leave and disconnect
- On disconnect, server updates presence and broadcasts leave events.
- Server should use heartbeat/keepalive to detect dead connections.

### 5.6 Data Validation & Limits
1. Server must reject messages that do not conform to the expected schemas.
2. Server must enforce:
   - maximum message size
   - maximum snapshot size
   - maximum operations per second per connection
   - maximum concurrent connections per board (configurable)
3. Server must sanitize any user-provided strings used for display/logging.

### 5.7 Observability & Operations
1. Health checks:
   - liveness
   - readiness (includes database connectivity)
2. Structured logs including correlation ids for WebSocket sessions.
3. Metrics:
   - active connections
   - connections per board
   - operations accepted/rejected
   - snapshot saves
   - error counts by type

## 6. Data Model (Conceptual)
- **boards**
  - id, name, type, ownerUserId, createdAt, updatedAt, status
- **board_permissions** (optional for sharing beyond invites)
  - boardId, userId, role (editor/viewer)
- **invites**
  - tokenId (or token hash), boardId, permission, expiresAt, maxUses, uses, revokedAt, createdAt
- **board_snapshots**
  - boardId, version, snapshotJson, createdAt, createdBy
- **board_ops** (optional)
  - boardId, seq, opJson, createdAt, actorUserId

## 7. Error Handling Requirements
- HTTP: use consistent error format with machine code + message.
- WebSocket: send error messages that are machine-readable and do not leak secrets.
- Authentication/authorization failures must not reveal whether a board exists.

## 8. Security Requirements
- Enforce TLS in production deployments (terminating at proxy is acceptable).
- Do not log bearer tokens or raw invite tokens.
- Store invite tokens securely (prefer hashing or signing).
- Protect against replay and brute force by rate limiting and expiry.

## 9. Compatibility Requirements
- The server must be able to evolve protocol/schema versions:
  - include protocol version in join handshake
  - reject incompatible clients with a clear error.
