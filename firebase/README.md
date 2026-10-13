# Shared garages on Firebase

Several people keep one set of vehicles up to date. Built fleet-ready: a garage has members with
roles and per-vehicle access, even though the first UI only needs "invite my wife to her car".

Project: `garage-log-1d7b1`. `app/google-services.json` is gitignored (this repo is public);
download it from the Firebase console.

## Data model (Firestore)

```
users/{uid}                          email, displayName, garageIds[]
users/{uid}/private/notificationPrefs  reminder settings — per person, not per garage

garages/{g}                          name, createdBy, updatedAt
garages/{g}/members/{uid}            role: "admin" | "standard", vehicleIds[], email, displayName, updatedAt
garages/{g}/invites/{email}          email (lower-cased, = doc id), role, vehicleIds[], invitedBy, createdAt

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
| Vehicles | add, edit, delete | odometer only (`miles`, `milesDate`), as a side effect of a log |
| Schedules | add, edit, delete | "last done" only, as a side effect of a log that fulfils it |
| Logs, issues, photos | everything | add and edit on their vehicles |
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

## Joining a garage

1. An admin creates `invites/{their email}` with a role and vehicles.
2. The invitee signs in; the app finds the invite with a collection-group query on `invites`
   where `email == my email`.
3. Accepting writes `members/{my uid}` (the rules require it to match the invite exactly), then
   deletes the invite.

The garage's creator bootstraps themself as its first admin, and can always do so again: the
owner can't be locked out of their own garage.

## Not verified yet

The rules haven't been run: there's no Node on the dev machine for the Firebase emulator. Check
them in the console's Rules Playground (Firestore -> Rules -> Rules Playground) before any real
data goes up.
