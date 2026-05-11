# Spec — Shared Kanban (Electric Clojure baseline)

## Context

**Purpose.** Baseline evaluation app for an Electric Clojure skill for Claude. Implementations are reviewed by the team; this spec describes app behavior only.

**Target shape (decided during #Frame):**

- Multi-user symmetric collaboration, 2-5 users per room.
- Named rooms via URL slug; no auth.
- In-memory server state; no durable persistence required.
- Domain: shared kanban board.

**Rejected during #Frame (with rationale):**

- TodoMVC — too well-documented; public + in-tree Electric references already exist.
- Single-user / broadcast / competitive shapes — symmetric collab chosen for richer concurrent-edit surface.
- User-creatable columns — fixed three keeps LOC budget and removes column-rename × card-move race.
- Title-only cards — extra fields chosen to widen LWW collision surface.
- Live drag-position broadcast — on-drop-only broadcast chosen to keep mechanic tractable.
- Presence / identity — anonymous chosen to focus the spec on the board itself.

## 1. App behavior

### 1.1 Rooms

1.1.1 **MUST**: Each room is identified by a URL slug; two visits to the same slug join the same room; different slugs are independent rooms.
*Verify:* two browsers on the same slug see the same board; two browsers on different slugs see independent boards.

1.1.2 **MUST**: A room is created lazily on first visit, starting with three empty columns and zero cards.
*Verify:* navigating to a previously-unused slug shows three empty columns and no cards.

1.1.3 **MUST**: No login, no display name, no identity surfaced in the UI.
*Verify:* the UI shows no name field, no user list, no avatar.

1.1.4 **MAY**: Room state survives all users leaving and rejoining, within a single server process.
*Verify:* close all tabs for a room; reopen the slug; cards present. After server restart, state may be lost.

1.1.5 **WONT**: Authentication, accounts, durable persistence, room directory listing.

### 1.2 Board

1.2.1 **MUST**: Every room renders three fixed columns titled "Todo", "Doing", "Done", in that order.
*Verify:* every room shows these three columns, in this order, with these titles.

1.2.2 **MUST**: Column titles are not editable; no UI exists to add, remove, rename, or reorder columns.
*Verify:* no affordance for editing column titles or modifying the column set is reachable.

### 1.3 Cards

1.3.1 **MUST**: A card has exactly three fields — `title` (single-line string), `assignee` (single-line string), `description` (multi-line string). All default to empty on creation.
*Verify:* a new card exposes three editable fields, all empty.

1.3.2 **MUST**: Each column has an "Add card" affordance; activating it creates a new empty card appended at the bottom of that column.
*Verify:* adding to column X appends a card to X's bottom; other columns unchanged.

1.3.3 **MUST**: Each card has a delete affordance; activating it removes the card from the board.
*Verify:* deleting card C removes it from its column; other cards unchanged.

### 1.4 Editing

1.4.1 **MUST**: All three card fields are user-editable in place on the card.
*Verify:* a user can modify each field's value on the card.

1.4.2 **MUST**: An edit committed by one user appears on other users in the same room without their action.
*Verify:* user B observes user A's title change within seconds, no refresh.

1.4.3 **MUST**: Concurrent edits to the same field resolve last-write-wins; all users converge to the same final value.
*Verify:* A and B edit card C's title to different values; both clients eventually show the same single value (whichever committed last).

1.4.4 **WONT**: Field-level locks, character-level merge, OT/CRDT, undo, conflict notifications.

### 1.5 Drag-reorder

1.5.1 **MUST**: A user can drag a card with the mouse to another position within its column, or to another column.
*Verify:* dragging card C from column X position i to column Y position j leaves C at column Y position j on drop.

1.5.2 **MUST**: Drag preview (the moving card, insertion indicator) is local to the dragging user.
*Verify:* during user A's in-flight drag, user B sees no movement; B sees only the final position after drop.

1.5.3 **MUST**: On drop, the new position is broadcast to all users in the room.
*Verify:* after A drops C in column Y position j, B's view shows C at column Y position j.

1.5.4 **MUST**: Card column and within-column position are room state; refresh by any user shows the same positions.
*Verify:* refresh shows identical positions.

1.5.5 **SHOULD**: Concurrent drops on the same card converge deterministically across all users.
*Verify:* A and B drop the same card to different positions concurrently; final order on A and B is identical.

### 1.6 Multi-user sync

1.6.1 **MUST**: All clients in the same room observe the same board state up to network delay.
*Verify:* every observable change made by one client appears on every other client in the room.

1.6.2 **MUST**: A client joining a populated room observes the existing cards on load.
*Verify:* join a room with existing cards; cards present in their current positions on first paint.

### 1.7 Concurrent semantics

1.7.1 **MUST**: All concurrent conflicts (field edits, drops, deletes) resolve last-write-wins.
*Verify:* see 1.4.3 and 1.5.5; deletion concurrent with edit yields delete-wins if delete is last.

1.7.2 **MUST**: Deletion winning over a concurrent edit leaves no orphan card on any client.
*Verify:* once a delete commits, the card is absent on every client, including any that had pending edits.

## 2. Acceptance scenarios

Each is a single repeatable test against a running implementation. Pass = behavior matches; fail = does not.

2.1 **Two-client sync.** Two browsers on the same slug. Add a card in "Todo" from A. *Pass:* B shows the card without refresh.

2.2 **Inline edit propagation.** Continue 2.1; B edits the card's title. *Pass:* A shows the new title without refresh.

2.3 **Drag across columns.** Continue 2.2; A drags the card from "Todo" to "Done". *Pass:* B shows the card in "Done" after drop, with no in-flight preview during the drag.

2.4 **Room isolation.** Two browsers on different slugs. Add a card in one. *Pass:* the other room is unchanged.

2.5 **LWW field edit.** Two browsers on the same slug both edit the same card's title; A commits "alpha", B commits "beta" moments later. *Pass:* both clients converge on "beta".

2.6 **Delete-vs-edit.** A is editing card C; B deletes C. *Pass:* C absent on both clients after delete commits.

2.7 **Empty room init.** Visit a fresh slug. *Pass:* three columns titled Todo, Doing, Done, all empty.

2.8 **Card fields.** Inspect any card. *Pass:* exactly three editable fields — title, assignee, description.

2.9 **Refresh persistence (in-process).** Add cards; refresh the browser. *Pass:* cards still present, assuming server not restarted.
