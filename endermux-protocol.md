# Endermux Protocol Specification

Status: Active  
Transport epoch: `15`

This document defines the wire protocol implemented by `endermux-client` and `endermux-server`.

## 1. Conformance Language

The key words `MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, and `MAY` are to be interpreted as described in RFC 2119.

## 2. Versioning Model

1. Transport compatibility is versioned by `SocketProtocolConstants.TRANSPORT_EPOCH` (`15`).
2. `TRANSPORT_EPOCH` covers framing, envelope shape, and handshake schema.
3. Feature behavior is negotiated per capability (see Section 8).
4. Any wire-incompatible transport change MUST increment `TRANSPORT_EPOCH`.
5. Any incompatible feature change MUST increment that capability's version.

## 3. Transport and Framing

1. Transport is a Unix domain socket.
2. Each protocol message is one frame:
   1. 4-byte signed big-endian length prefix.
   2. UTF-8 JSON payload bytes.
3. Frame length MUST be `> 0` and `<= 1048576` bytes (`1 MiB`).
4. EOF while reading the length prefix is treated as a clean close.
5. Invalid frame length is a protocol error.

## 4. Message Envelope

Every frame payload is a JSON object with this envelope:

| Field | Type | Required | Notes |
|---|---|---|---|
| `type` | string | yes | Name of `MessageType` constant |
| `requestId` | string | conditional | Required for request/response flows |
| `data` | object | yes | Payload object for `type` |

Example:

```json
{
  "type": "PING",
  "requestId": "d7a7f8ed-bd7c-4e56-b5e8-cc2867e2bd4c",
  "data": {}
}
```

## 5. Connection Lifecycle and Handshake

1. Client connects to the socket.
2. Client sends `HELLO` with `requestId`.
3. Server reads the first message with handshake timeout.
4. Server responds with exactly one of:
   1. `WELCOME` if transport epoch and required capabilities are accepted.
   2. `REJECT` if request is invalid or negotiation fails.
5. If `WELCOME` is sent, normal message exchange begins.
6. Server then sends initial `INTERACTIVITY_STATUS`.

Handshake constraints:

1. `HELLO` MUST include `requestId`.
2. `HELLO` MUST be the first message.
3. Handshake timeout is `2000ms` with `1000ms` join grace.
4. Timeout or transport failure closes the connection; timeout `REJECT` delivery is not guaranteed.

## 6. Message Catalog

### 6.1 Client to Server

| Type | Requires `requestId` | Expected response |
|---|---|---|
| `HELLO` | yes | `WELCOME` or `REJECT` |
| `COMPLETION_REQUEST` | yes | `COMPLETION_RESPONSE` or `ERROR` |
| `SYNTAX_HIGHLIGHT_REQUEST` | yes | `SYNTAX_HIGHLIGHT_RESPONSE` or `ERROR` |
| `PARSE_REQUEST` | yes | `PARSE_RESPONSE` or `ERROR` |
| `COMMAND_EXECUTE` | no | none (fire-and-forget, `ERROR` possible) |
| `PING` | yes | `PONG` or `ERROR` |
| `LOG_SUBSCRIBE` | no | none |

### 6.2 Server to Client

| Type | Correlated by `requestId` | Purpose |
|---|---|---|
| `WELCOME` | yes (handshake) | Accept connection and return negotiated capabilities |
| `REJECT` | yes when requestId exists | Reject handshake |
| `COMPLETION_RESPONSE` | yes | Completion results |
| `SYNTAX_HIGHLIGHT_RESPONSE` | yes | Highlighted command text |
| `PARSE_RESPONSE` | yes | Parsed line metadata |
| `LOG_FORWARD` | no | Forwarded server log event |
| `PONG` | yes | Ping response |
| `ERROR` | optional | Request error or unsolicited error |
| `INTERACTIVITY_STATUS` | no | Interactivity availability updates |

## 7. Payload Schemas

`nullable` means JSON `null` is valid.

### 7.1 Client to Server payloads

| Type | Payload fields |
|---|---|
| `HELLO` | `transportEpoch: int`, `colorLevel: ColorLevel`, `capabilities: map<string, CapabilityVersionRange>`, `requiredCapabilities: string[]` |
| `COMPLETION_REQUEST` | `command: string`, `cursor: int` |
| `SYNTAX_HIGHLIGHT_REQUEST` | `command: string` |
| `PARSE_REQUEST` | `command: string`, `cursor: int` |
| `COMMAND_EXECUTE` | `command: string` |
| `PING` | _(empty object)_ |
| `LOG_SUBSCRIBE` | _(empty object)_ |

### 7.2 Server to Client payloads

| Type | Payload fields |
|---|---|
| `WELCOME` | `transportEpoch: int`, `selectedCapabilities: map<string, int>` |
| `REJECT` | `reason: string` (see 7.3), `message: string`, `expectedTransportEpoch: int?`, `missingRequiredCapabilities: string[]` |
| `COMPLETION_RESPONSE` | `candidates: CandidateInfo[]` |
| `SYNTAX_HIGHLIGHT_RESPONSE` | `command: string`, `highlighted: string` |
| `PARSE_RESPONSE` | `word: string`, `wordCursor: int`, `wordIndex: int`, `words: string[]`, `line: string`, `cursor: int` |
| `LOG_FORWARD` | `rendered: string` |
| `PONG` | _(empty object)_ |
| `ERROR` | `message: string`, `details: string?` |
| `INTERACTIVITY_STATUS` | `available: boolean` |

### 7.3 `REJECT.reason` Codes

`REJECT.reason` values are machine-readable string constants:

1. `missing_request_id`
2. `expected_hello`
3. `unsupported_transport_epoch`
4. `missing_color_level`
5. `missing_capability_negotiation_data`
6. `invalid_capability_version_range`
7. `invalid_required_capability_declaration`
8. `missing_required_capabilities`

Clients MAY implement specialized handling for known `REJECT.reason` values and SHOULD treat unknown values as fatal incompatibility.

### 7.4 Nested payload types

`CandidateInfo`:

| Field | Type |
|---|---|
| `value` | string |
| `display` | string |
| `description` | string? |

`ColorLevel`:

| Value |
|---|
| `NONE` |
| `INDEXED_8` |
| `INDEXED_16` |
| `INDEXED_256` |
| `TRUE_COLOR` |

`CapabilityVersionRange`:

| Field | Type |
|---|---|
| `min` | int |
| `max` | int |

## 8. Capability Negotiation

Capability names are lowercase strings.

Baseline capabilities in transport epoch `15`:

1. Required by client:
   1. `command_execute`
   2. `log_forward`
   3. `interactivity_status`
2. Optional:
   1. `completion`
   2. `syntax_highlight`
   3. `parse`

Negotiation rules:

1. Client advertises supported version ranges in `HELLO.capabilities`.
2. Client advertises hard-required capability names in `HELLO.requiredCapabilities`.
3. Server computes the highest common version for each known capability.
4. Server returns selected versions in `WELCOME.selectedCapabilities`.
5. If any required capability is not negotiated, server MUST send `REJECT` with `missingRequiredCapabilities`.
6. After `WELCOME`, both peers MUST only use messages associated with negotiated capabilities.
7. Capability policy is role-specific: client `HELLO` capability/required sets and server supported capability set evolve independently.

## 9. Request/Response Rules

1. For message types marked "Requires `requestId`", the sender MUST provide a non-null `requestId`.
2. A response to a request MUST echo the same `requestId`.
3. `ERROR` MAY be correlated (with `requestId`) or unsolicited (without `requestId`).
4. `COMMAND_EXECUTE` is fire-and-forget. Command output is returned through `LOG_FORWARD`, with optional `ERROR`.

## 10. Interactivity and Log Forwarding

1. `interactivity_status` capability is required.
2. Server sends `INTERACTIVITY_STATUS` after successful handshake and whenever availability changes.
3. Interactivity-gated requests are:
   1. `COMPLETION_REQUEST`
   2. `SYNTAX_HIGHLIGHT_REQUEST`
   3. `PARSE_REQUEST`
   4. `COMMAND_EXECUTE`
4. If interactivity is unavailable, server responds with `ERROR` for gated operations.
5. Client sends `LOG_SUBSCRIBE` when it is ready to consume forwarded logs.
6. Server forwards `LOG_FORWARD` messages only for clients marked ready.

## 11. Error Handling and Close Semantics

1. Invalid JSON, unknown `type`, or payload decode failure is a protocol error.
2. Transport read/write failures terminate the session.
3. There is no explicit disconnect message; disconnect is socket closure.
4. `ERROR` reports application/protocol request failures but does not require immediate disconnect.

## 12. Timeouts and Limits

| Constant | Value |
|---|---|
| Handshake timeout | `2000ms` |
| Handshake timeout join grace | `1000ms` |
| Completion timeout | `5000ms` |
| Syntax highlight timeout | `1000ms` |
| Max frame size | `1 MiB` |

## 13. Compatibility Policy

1. Compatibility across releases in the same transport epoch is achieved by capability negotiation.
2. Newer clients SHOULD keep adapters for older selected capability versions.
3. Missing required capabilities MUST fail handshake.
4. If behavior and this document diverge, implementation and documentation MUST be updated together.
