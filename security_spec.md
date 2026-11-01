# Security Specification (`security_spec.md`)

## 1. Data Invariants
1. **Strict Multi-Tenant Isolation**: Every document under `/users/{userId}` and `/users/{userId}/memories/{memoryId}` is strictly owned by `userId == request.auth.uid`. Cross-user reads, lists, creations, updates, and deletions are unconditionally rejected.
2. **Immutable Identity & Creation Timestamps**: `userId` and `createdAt` can never be mutated on `update`.
3. **Bounded Payloads & Valid IDs**: All document IDs (`userId`, `memoryId`) must satisfy `isValidId(id)` (`^[a-zA-Z0-9_\-]+$`, max 128 chars). All string fields have strict `.size()` bounds.
4. **Temporal Integrity**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects with `<= request.time`.

## 2. The "Dirty Dozen" Adversarial Payloads
1. Unauthenticated read on `/users/alice_123`.
2. Authenticated read by `alice_123` on `/users/bob_456`.
3. Authenticated create on `/users/alice_123` where payload `userId` is `"bob_456"` (Identity Spoofing).
4. Authenticated create on `/users/alice_123` with a ghost field `"isAdmin": true` (Shadow Field Injection).
5. Authenticated update on `/users/alice_123` mutating immutable `createdAt`.
6. Authenticated update on `/users/alice_123` mutating immutable `userId`.
7. Authenticated create on `/users/alice_123` with an oversized `userName` (> 80 chars).
8. Unauthenticated list on `/users/alice_123/memories`.
9. Authenticated list by `bob_456` on `/users/alice_123/memories`.
10. Authenticated create on `/users/alice_123/memories/mem_1` with `importance: 99` (out of 1..10 range).
11. Authenticated create on `/users/alice_123/memories/mem_1` with `content: ""` (empty content).
12. Authenticated update on `/users/alice_123/memories/mem_1` with a ghost field `"verified": true`.
