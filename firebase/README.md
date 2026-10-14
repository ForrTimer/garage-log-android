# Shared garages on Firebase

Several people keep one set of vehicles up to date. Built fleet-ready: a garage has members with
roles and per-vehicle access, even though the first UI only needs "invite my wife to her car".

Project: `garage-log-1d7b1`. `app/google-services.json` is gitignored (this repo is public);
download it from the Firebase console.

## Data model (Firestore)

```
users/{uid}                          email, displayName, garageIds[]
users/{uid}/private/notificationPrefs  reminder settings — per person, not per garage

invites/{email}                      email (lower-cased, = doc id), garageId, garageName, role, vehicleIds[],
                                     invitedBy, invitedByName, createdAt

garages/{g}                          name, createdBy, updatedAt, standardPermissions{}
garages/{g}/members/{uid}            role: "admin" | "standard", vehicleIds[], email, displayName, updatedAt

garages/{g}/vehicles/{vehicleId}     the SyncVehicle fields
garages/{g}/schedules/{id}           the SyncSchedule fields (vehicleId)
garages/{g}/logs/{id}                the SyncLog fields (vehicleId)
garages/{g}/issues/{id}              the SyncIssue fields (vehicleId)
garages/{g}/photos/{id}              metadata + vehicleId; the image itself in Cloud Storage (Blaze plan)
```

Record ids are the app's existing UUIDs, so Room rows and Firestore docs map one to one, and every
record keeps `updatedAt` + `deleted`: the same last-write-wins, soft-delete rules the Drive sync
used, applied per document instead of per snapshot file.

## Roles

| | Admin | Standard |
|---|---|---|
| Sees | every vehicle in the garage | vehicles in their `vehicleIds` |
| Vehicles | add, edit, delete | odometer (`miles`, `milesDate`); details too if `editVehicles` |
| Schedules | add, edit, delete | "last done"; everything if `editSchedules` |
| Logs, issues, photos | everything | add; edit their own; others' if `editOthersEntries`; delete if `deleteEntries` |
| Members, invites | manage | — |
| Bob | chat, diagnosis, schedules | diagnosis only |

`firestore.rules` enforces all of this on the server; the app hides what a role can't do, but
the rules are what make it true.

## Sync rules the app must follow

- Rules don't filter queries. A Standard member's pulls must be scoped to their vehicles:
  `where vehicleId in vehicleIds` (Firestore allows up to 30 values per `in`; past that, chunk),
  and vehicles by document id.
- A Standard member pushes vehicles and schedules as field-masked updates (`SetOptions.merge` with
  only the allowed fields), never the whole record, or the rules reject the write.
- Pull incrementally: `updatedAt > lastPulledAt` per collection.

## Standard permissions

Admins switch these per garage (Members screen), stored in `garages/{g}.standardPermissions`.
A missing key means its default, in the rules and the app alike:

| Key | Default | Lets a Standard member |
|---|---|---|
| `editOthersEntries` | on | edit logs and issues someone else created (`createdBy` = Firebase uid; "" = from before shared garages) |
| `deleteEntries` | off | delete logs and issues they may edit |
| `editSchedules` | off | add, edit and delete schedules on their vehicles |
| `editVehicles` | off | edit their vehicles' details (never add, delete or reorder for everyone) |

A Standard member's phone pushes one record at a time; one the rules refuse (a permission switched
off while an edit was waiting) is put back the way the garage has it rather than failing the sync.

## Joining a garage

1. An admin creates `invites/{their email}` (top level, so no index or collection-group query is
   needed to find it) with a role and vehicles.
2. The invitee signs in to the shared garage; with no garage of their own, the app reads
   `invites/{my email}` and asks.
3. Accepting writes `members/{my uid}` (the rules require it to match the invite exactly), then
   deletes the invite. An admin's records merge into the garage on the first sync; a Standard
   member's phone is cleared first (after an optional backup) and shows only what they were given.

The garage's creator bootstraps themself as its first admin, and can always do so again: the
owner can't be locked out of their own garage.

## Testing

There's no Node on the dev machine for the Firebase emulator, so the rules are tested by real
writes from two accounts (an admin's phone and a Standard member's).
