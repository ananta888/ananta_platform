# EDS Lite

EDS (Encrypted Data Store) is a virtual disk encryption software for Android which allows you to store your files in an encrypted container. EDS Lite is an open source edition of EDS. Visit [EDS web site](https://sovworks.com/eds/) to learn about EDS features and capabilities.

You can download and install the latest version from [Google Play](https://play.google.com/store/apps/details?id=com.sovworks.edslite).

Please support the development of the open source version of EDS by making a donation: [![Donation](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://sovworks.com/eds/donations.php) or by purchasing [the full version](https://play.google.com/store/apps/details?id=com.sovworks.eds.android).

## Building

### Voraussetzungen
*   **JDK 17**: Stellen Sie sicher, dass Java 17 installiert und in der Umgebungsvariable `JAVA_HOME` gesetzt ist.
*   **Android SDK**: Installiert via Android Studio.
*   **CMake & NDK**: Werden automatisch via Gradle heruntergeladen (sofern in `build.gradle` konfiguriert) oder müssen im Android Studio SDK Manager installiert werden.

### Build-Anweisungen (Windows PowerShell/CMD)

1.  **Repository klonen**
2.  **Umgebung konfigurieren**:
    Stellen Sie sicher, dass `JAVA_HOME` auf ein JDK 17 (oder die Android Studio JBR) zeigt.
    ```powershell
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" # Beispielpfad
    ```
3.  **Projekt bauen**:
    ```powershell
    .\gradlew.bat clean :app:assembleDebug
    ```
    Die erzeugte APK befindet sich unter `app/build/outputs/apk/lite.../debug/`.

### Migration Notes (2025)
Das Projekt wurde von einem alten AGP 3.x/jcenter Setup auf den aktuellen Stand (AGP 8.2, Gradle 8.5) migriert:
*   **AndroidX**: Vollständig migriert.
*   **Namespace**: `com.sovworks.eds.android`
*   **Java**: Upgrade auf Java 17.
*   **jcenter**: Entfernt, alle Abhängigkeiten werden über MavenCentral oder Google bezogen.
*   **age-java**: Temporär deaktiviert wegen Repositories-Problemen.

## License
GPLv2+




