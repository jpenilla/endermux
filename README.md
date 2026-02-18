# Endermux

Endermux provides a Unix domain socket based remote console stack for Minecraft servers:

- `endermux-server`: server-side transport/session/handlers
- `endermux-client`: interactive terminal client (JLine + syntax highlighting + completions)
- `endermux-common`: shared wire protocol types, framing, serializers, constants, JLine and Log4j2 utilities

Protocol semantics and wire format are specified in [`endermux-protocol.md`](endermux-protocol.md)

The Endermux Client provides a fully featured interactive experience with support for tab completion,
syntax highlighting, log forwarding, and more over the socket. This gives a far superior experience to alternatives
like RCON or even tmux, especially when running a server as a headless service (although the client can be used *with*
tmux, for scrollback etc.).

Currently, the only transport supported is Unix domain sockets. However, the client is designed to be somewhat latency
tolerant, so both local socket connections (client and server run on the same machine), and remote socket connections
(client and server run on different machines, socket is forwarded using SSH or similar) should work.
If there is serious interest in supporting other transport (i.e., TCP), it's something we would consider.

## Requirements
- Java 25 runtime (or JDK to build)
- Unix domain socket support (Linux, macOS, BSDs, modern Windows versions with AF_UNIX support)
- A modern terminal emulator (for the best client experience)

## Usage (Server)

`endermux-server` is a library; it must be embedded in a server process. Two popular server implementations include it:
- [Better Fabric Console](https://github.com/jpenilla/better-fabric-console) includes the Endermux server.
  - Enabled and configured in `better-fabric-console.conf`
- [Paper](https://github.com/PaperMC/Paper) may eventually include the Endermux server, see [this PR](https://github.com/PaperMC/Paper/pull/13603).
  - Enabled and configured through `paper.endermux.enabled`, `paper.endermux.socketPath`, and `paper.endermux.maxConnections` JVM system properties. 

## Usage (Client)

### Get Started

- Download the latest client distribution from GitHub Actions.
- Extract the archive.
- `cd` into the extracted directory.
- Have `JAVA_HOME` set to a Java 25+ installation.
- Run `./bin/endermux-client --help` or `.\bin\endermux-client.bat --help` on Windows to see available options.

### Behavior

1) If the socket does not exist yet, the client will use file watching (falling back to polling if necessary) to wait for it to appear.
2) Once the socket exists, the client will attempt to connect to it.
3) If the connection fails for transient reasons (for example I/O errors), the client will retry indefinitely with exponential backoff (capped at 1 minute).
4) If handshake fails with a fatal reject reason (for example transport epoch mismatch, missing required capabilities, or an unknown reject reason), the client exits immediately without retrying (non-zero exit code).
5) On successful connection, the client attaches to the remote console session.
6) On lost connection (graceful or otherwise), the client will restart at step 1.

### Controls

When connected:
- `Ctrl+C` - clears the current command buffer and prints an informational message
- `Ctrl+D` - disconnects from the remote console session and exits the client

When disconnected (i.e., waiting for socket or reconnection backoff):
- `Ctrl+C` - exits the client

## Building

Use the Gradle wrapper to build the project (`./gradlew build` on Unix, `.\gradlew.bat build` on Windows).

Common build tasks include:
- `./gradlew build` - compile and check everything
- `./gradlew :endermux-client:installDist` - build and install the client to `./endermux-client/build/install/endermux-client`
- `./gradlew publishToMavenLocal` - publish the project to your local Maven repository for testing
