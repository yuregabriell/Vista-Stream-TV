# Vista Source + Vista Connect — implementation status

## Vista Source v1 (implemented in feature branch)

Portable JSON snapshot with schemaVersion=1, type=vista-source,
id, name, generatedAt, items[] (NativeSavedStream records).
Export and import through Android document picker, in Vista ID settings.

Safety boundaries: 1 MiB maximum file, 250 video entries, no cookies,
authorization headers, content files or known signed stream URLs.
Imported entries go to the existing NativeHarvesterStore provider and
are available when their title/episode matches the catalog.

The browser's v4.3.7 source collector emits the same manifest format.
For synchronization between devices, the existing encrypted LAN SyncPacket
transfers the saved source list when a sync operation is performed.
This is a static snapshot. Dynamic catalogEndpoint/streamsEndpoint and
automatic remote publishing remain future protocol versions.

## Vista Connect (phase 1 in feature branch)

Android NSD/mDNS advertises the receiver with type _vistastream._tcp.
A sender on the same LAN discovers its private IPv4 address and pre-fills
the field. TV mode starts receiver and displays the code; phone mode
shows the code input and connect action.

The existing pairing uses a 12-character 72-bit random code,
PBKDF2-HMAC-SHA256 plus AES-256-GCM. The code is required for every
manual synchronization, is never stored, and expires after 5 minutes.
This version **does not** establish persistent paired credentials and
does not run automatic background sync. It also does not enable cloud
sync through Vista ID. Both are separate security-sensitive features.

## Persistent pairing requirements (not yet implemented)

- Device identity and per-peer revocable credentials stored using Android
  Keystore, never a shared six-digit code as the long-lived key.
- Authenticated pairing with replay protection, server identity binding,
  maximum failed attempts and an expiry for the short displayed code.
- mDNS peer identity, re-discovery after IP changes, and local-network
  permission handling for newer Android versions.
- Opt-in data synchronization service with foreground notifications where
  required, reconnection and offline event queue with conflict resolution.
- Unpair on either TV or phone, plus multi-TV support, tested on real devices.
- Never synchronize cookies, passwords, DRM sessions or browser credentials.

## Android signing

The APK updater can only update an installed application in-place when
its package name and signing certificate are unchanged. Release builds
must use persistent encrypted CI signing secrets; otherwise Android may
require uninstall/reinstall and user data migration.
