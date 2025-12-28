# Ananta Platform Architecture

This folder documents the current architecture and behavior of the Ananta Platform edition.

## Files
- architecture.puml: PlantUML diagrams (component + key flows)

## Scope
The project blends the original EDS Lite encrypted container stack with a P2P messaging and exchange layer built on WebRTC. It includes both legacy views/fragments and modern Compose UI.

## Major Modules
- UI Layer
  - Compose screens: chat, exchange/search, trust, pairing, settings, dashboards
  - Legacy fragments: file manager, search fragment, dialogs
  - Navigation and app scaffolding

- Identity and Trust
  - IdentityManager: key generation, rotation, import/export
  - TrustStore: local trust DB of peer keys, trust levels, reasons
  - TrustNetworkManager: verify/import trust packages, compute trust rank
  - TrustRankingManager: interaction-based scoring

- P2P Networking
  - WebRtcService and PeerConnectionManager
  - DataChannelMultiplexer for chat/file/search/offline/relay channels
  - SecureDataChannel for encrypted payloads
  - PfsSession for PFS handshake and message encryption
  - HttpSignalingClient for offer/answer/ICE exchange

- Messaging and Exchange
  - MessengerRepository for chat persistence/integration
  - SearchManager for decentralized search and routing
  - SharedFileManager for shared file metadata and hashes
  - OfflineMessageManager for store/retrieve/bundle flows
  - RelayManager for relayed control/data paths

- Storage and File System
  - Container and file system adapters (VeraCrypt/LUKS/TrueCrypt, EncFS)
  - File manager, browser records, directory settings

- Persistence
  - Room database for offline messages and metadata
  - SharedPreferences for shared file lists and hashes
  - App files for identity and trust data

## Key Functionalities
- Encrypted container creation and management (VeraCrypt, LUKS, TrueCrypt)
- File manager with sorting and browsing of containers and locations
- P2P chat and file transfer over WebRTC data channels
- Perfect Forward Secrecy for chat and control messages (Double Ratchet style)
- QR pairing for trust verification and onboarding
- Identity sync across devices using QR codes
- Key rotation and rotation certificates
- Web-of-Trust model with trust levels and recommendations
- Decentralized search with trust-aware ranking and filters
- Offline messaging via trusted relays
- Relay channels for indirect P2P forwarding
- Trust-aware sharing and security checks
- Compose + legacy UI coexistence for migration

## Security and Privacy Properties
- End-to-end encryption on chat and file transfer channels
- PFS per session with ratcheting keys
- Identity-based trust with local verification
- Offline-first trust data stored locally and shared explicitly
- Key rotation with cryptographic proof

## Networking Properties
- WebRTC for data channels (chat, file, discovery, offline, relay)
- HTTP signaling for SDP/ICE exchange
- Optional relay paths when direct connection is unavailable
- Trust filtering in search results

## Data and Storage Properties
- Identity stored in app files (encrypted private key)
- Trust keys stored locally in TrustStore
- Offline messages stored in Room and delivered via bundle
- Shared file metadata stored in SharedPreferences

## UI/UX Properties
- Trust management includes peer list, trust level control, and graph view
- Search UI includes trust filter, file type filter, and size filters
- Chat UI shows trust status for peer

## External Dependencies
- WebRTC stack
- OkHttp for signaling
- Gson for JSON serialization
- Bouncy Castle for crypto primitives
- Room for storage

## Architecture Notes
- Both legacy and Compose UI paths call into shared managers.
- Most P2P functionality is routed through DataChannelMultiplexer.
- Trust graph view is derived from local TrustStore and recommendations.
- Search request and response routing are TTL-based with loop prevention.
