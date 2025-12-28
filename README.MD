# EDS Lite (Ananta Platform Edition)

EDS (Encrypted Data Store) ist eine virtuelle Festplattenverschlüsselungssoftware für Android, mit der Sie Ihre Dateien in einem verschlüsselten Container speichern können. EDS Lite ist die Open-Source-Edition von EDS.

## Architekturübersicht

Die Anwendung ist in mehrere Schichten unterteilt, um eine saubere Trennung zwischen der Benutzeroberfläche, der Logik für virtuelle Dateisysteme, der kryptographischen Verarbeitung und der dezentralen Kommunikation zu gewährleisten.

```mermaid
graph TD
    subgraph "Android UI Layer"
        UI[Activities & Compose UI]
        Services[Services (File Operations & WebRTC)]
        Trust[Trust Management UI]
    end

    subgraph "Networking & P2P"
        RTC[WebRTC Data Channels]
        Signaling[Peer Discovery / QR-Sync]
        Archive[Public Key Archive]
    end

    subgraph "FileSystem Layer"
        FS[FS Interface]
        FSImpl[FAT/exFAT/EncFS]
    end

    subgraph "Container Logic"
        Container[VeraCrypt/LUKS/TrueCrypt]
        VolMgmt[Volume Management]
    end

    subgraph "Crypto Core"
        Asym[Asymmetric Crypto (Ed25519)]
        Crypto[Symmetric Encryption Engines]
        JNI[Native JNI Bridges]
    end

    subgraph "Native Implementation (C)"
        Native[AES/Serpent/Twofish]
    end

    UI --> Services
    UI --> Trust
    Services --> RTC
    Services --> FS
    Trust --> Archive
    RTC --> Signaling
    RTC --> Asym
    FS --> Container
    Container --> Crypto
    Crypto --> Native
```

### Komponentenbeschreibung

*   **Android UI Layer**: Beinhaltet moderne Jetpack Compose Screens für Dateiverwaltung, Messenger-Funktionen und Trust-Management.
*   **Networking & P2P**: Implementiert die dezentrale Kommunikation via WebRTC. Das **Public Key Archiv** verwaltet Identitäten und deren Vertrauens-Rankings.
*   **FileSystem Layer**: Eine Abstraktionsschicht, die verschiedene Dateisystemformate unterstützt.
*   **Container Logic**: Unterstützung für VeraCrypt, LUKS und TrueCrypt.
*   **Crypto Core & Native Implementation**: Kombiniert asymmetrische Kryptographie für Identitäten mit performanter symmetrischer Verschlüsselung (C/JNI).

## Vision: Ananta Secure Exchange
Basierend auf der soliden Basis von EDS Lite wird die Ananta Platform zu einem Werkzeug für dezentrale, sichere Kommunikation entwickelt:
*   **Dezentrale Vertrauenskette**: Nutzer bewerten Public Keys und teilen diese Bewertungen im Peer-Netzwerk.
*   **QR-Kopplung**: Einfacher Verbindungsaufbau zwischen Geräten ohne zentrale Benutzerkonten.
*   **WebRTC P2P**: Direkter Dateiaustausch und Kommunikation über Internet und lokales WLAN.

Detaillierte Aufgaben finden sich in der `todo.json`.

## Building

### Voraussetzungen
*   **JDK 17**: Stellen Sie sicher, dass Java 17 installiert und in der Umgebungsvariable `JAVA_HOME` gesetzt ist.
*   **Android SDK**: Installiert via Android Studio.
*   **CMake & NDK**: Werden automatisch via Gradle heruntergeladen oder über den SDK Manager installiert.

### Build-Anweisungen (Windows PowerShell)

1.  **Umgebung konfigurieren**:
    ```powershell
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" # Pfad anpassen
    ```
2.  **Projekt bauen**:
    ```powershell
    .\gradlew.bat clean :app:assembleDebug
    ```
    Die APK befindet sich unter `app/build/outputs/apk/lite.../debug/`.

## Migration Notes (2025)
Das Projekt wurde erfolgreich auf moderne Standards migriert:
*   **AndroidX & Jetpack Compose**: Integration für moderne UI-Komponenten.
*   **AGP 8.2 / Gradle 8.5**: Aktuelles Build-System.
*   **Java 17**: Ziel-Laufzeitumgebung.
*   **Dependency Management**: Umstellung von jcenter auf MavenCentral/Google.

## Lizenz
GPLv2+




