# Tether desktop demo

A small chat app for Linux, Windows and macOS that shows how to use the [Tether XMPP SDK](https://github.com/usmandurrany/tether-xmpp) on the JVM. It connects to any standards-compliant XMPP server (ejabberd, Prosody, Openfire, MongooseIM and others) and uses every part of the SDK:

| Screen | What it shows | SDK API |
| --- | --- | --- |
| Connection settings | Address, password, optional host/port, direct TLS, trusting a self-signed certificate, PLAIN and SCRAM-SHA-1 opt-ins, "remember me" | `XmppConfig`, `TlsConfig`, `SaslConfig`, `TetherJvm.client`, `CredentialVault`, `FileKeyValueStore` |
| Status bar | Live connection state, reconnecting, presence and status message, disconnect, log out | `XmppClient.state`, `Presences.set` / `hide` |
| Chats | Contacts with presence, unread counts, add/rename/remove, subscription requests, messages with sent and delivered ticks, typing, history | `Roster`, `Presences`, `Messaging`, `History` |
| Server | What the server offers on this session (Stream Management, carbons, archive, CSI, disco features), push registration | `XmppSession.capabilities`, `Carbons`, `Push` |
| Debug log | Every stanza sent and received, credentials always redacted, optional upload to a [tether-debug](https://github.com/usmandurrany/tether-debug) log portal | `TetherDebug`, `MemorySink`, `ConsoleSink`, `RemoteLogSink` |

The UI is plain Swing with the [FlatLaf](https://www.formdev.com/flatlaf/) look, so the app starts fast and needs no native libraries. Everything the SDK does lives in one file, [`DemoSession.kt`](src/main/kotlin/io/github/usmandurrany/tether/demo/DemoSession.kt); the `ui/` package only draws it.

## Run it

You need JDK 17 or newer and an internet connection. Nothing else: Gradle comes with the wrapper, and the first build fetches the SDK from GitHub and compiles it (see below).

```sh
git clone https://github.com/usmandurrany/tether-demo-desktop
cd tether-demo-desktop
./gradlew run            # Windows: gradlew.bat run
```

To hand it to a tester, build a single jar and run it anywhere with Java 17+:

```sh
./gradlew packageJar
java -jar build/libs/tether-demo-desktop-0.1.0-all.jar
```

`./gradlew installDist` gives a folder with start scripts for Linux/macOS and Windows in `build/install/tether-demo/bin`.

## Connect to a server

1. Enter your address (`alice@example.com`) and password and press **Connect**. Most servers need nothing else: the server is found through the domain's DNS SRV records.
2. If the domain has no SRV records, or you reach the server by IP or another name, fill in **Host** (and **Port** if it isn't 5222). Tick **Direct TLS** for servers that take TLS from the first byte, usually on port 5223.
3. If the server uses a self-signed certificate, Connect shows its SHA-256 fingerprint and asks whether to trust it. Yes pins that certificate in **Trusted certificate**.
4. If the server only offers older login mechanisms, the error says so. Tick **Allow SCRAM-SHA-1** (Prosody 0.12 and other older servers) or **Allow PLAIN**. PLAIN is still only sent over verified TLS.

With **Remember me** on, the app keeps the SCRAM login keys (never the password) and signs in by itself on the next start. **Disconnect** keeps them; **Log out** deletes them.

Tether never connects without TLS.

## Where files go

| OS | Folder |
| --- | --- |
| Linux | `$XDG_CONFIG_HOME/tether-demo` (usually `~/.config/tether-demo`) |
| Windows | `%APPDATA%\TetherDemo` |
| macOS | `~/Library/Application Support/TetherDemo` |

`settings.properties` holds the settings page (no password). `tether-state.properties` is the SDK's own state: stream resumption, unsent messages, keepalive timing, the roster and, with Remember me, the login keys. It is readable by your user only, but not encrypted. Pass `-Dtether.demo.home=<folder>` to use another folder, for example to run two accounts side by side:

```sh
JAVA_OPTS="-Dtether.demo.home=/tmp/bob" ./gradlew run
```

## Debug logging

The **Debug log** tab always shows the traffic. On the settings page you can also print it to the terminal, hide message bodies, and upload it to a tether-debug log portal (URL, ingest key and an optional label for the testing round). The portal groups uploads by an install id the app creates once.

## How the SDK is included

Tether is not on Maven Central yet, so Gradle builds it from its public repository, [usmandurrany/tether-xmpp](https://github.com/usmandurrany/tether-xmpp). [`settings.gradle.kts`](settings.gradle.kts) declares the repository as a Gradle source dependency: on the first build Gradle clones it into `.gradle/vcs-1` and compiles the JVM parts of the SDK (no Kotlin/Native or Android SDK needed); later builds reuse that copy. The app asks for the SDK by the same coordinates it will use once Tether is published, so moving to a released version only means deleting the `sourceControl` block:

```kotlin
implementation("io.github.usmandurrany.tether:tether-core")  { version { branch = "main" } }
implementation("io.github.usmandurrany.tether:tether-im")    { version { branch = "main" } }
implementation("io.github.usmandurrany.tether:tether-debug") { version { branch = "main" } }
```

The build follows the SDK's `main` branch. To try another branch, or to work on the SDK and the app together:

```sh
./gradlew run -Ptether.branch=my-branch          # another branch of tether-xmpp
./gradlew run -Ptether.sdkPath=../tether-xmpp    # your own checkout, rebuilt on every change
```

The log portal that **Log portal URL** uploads to is a separate project, [usmandurrany/tether-debug](https://github.com/usmandurrany/tether-debug), which you run yourself with Docker.

## The parts worth copying

- **One client per account, features installed before connecting.** `DemoSession.create` builds the client with `TetherJvm.client`, installs `InstantMessaging`, starts collecting events, then connects. Events nobody collects yet are dropped.
- **Desktop connection policy.** `BackgroundMode.STAY_CONNECTED`: the app stays connected when its window loses focus and only tells the server it is inactive (XEP-0352). Window focus drives `client.setForeground`.
- **Network changes.** The JVM has no network event, so [`NetworkMonitor`](src/main/kotlin/io/github/usmandurrany/tether/demo/NetworkMonitor.kt) checks the interfaces every few seconds and calls `client.networkChanged`.
- **First attempt versus reconnects.** `connect()` returns after the first attempt. The settings page stops on a failure and explains it; after a successful login, Tether reconnects on its own and the status bar shows it.
- **Trusting a self-signed certificate.** `DemoSession.refusedCertificate` finds the fingerprint in the error, and the app retries with it pinned.
- **Not remembering a login.** A small `KeyValueStore` wrapper drops the SCRAM keys while still letting the SDK save its other state.
- **Message state.** [`Conversations`](src/main/kotlin/io/github/usmandurrany/tether/demo/Conversations.kt) turns `MessageEvent`s and archive pages into a list with delivery ticks, and removes duplicates between live messages and history.

## Tests

`./gradlew test` runs an end-to-end test against a real server when you give it two accounts, and is skipped otherwise:

```sh
TETHER_A_JID=alice@example.com TETHER_A_PASSWORD=... \
TETHER_B_JID=bob@example.com TETHER_B_PASSWORD=... \
./gradlew test
```

Add `TETHER_HOST` / `TETHER_PORT` when the domain has no SRV records, and `TETHER_ALLOW_SCRAM_SHA1=true` for servers that offer nothing newer. The test trusts a self-signed certificate the same way the app does. It covers the subscription handshake, presence, a message with its server ack and delivery receipt, history without duplicates, signing in again with saved keys, and log out.

## License

Apache License 2.0, like the SDK.
