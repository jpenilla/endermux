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

- Download the latest client distribution from GitHub Actions.
- Extract the archive
- `cd` into the extracted directory
- Run `./bin/endermux-client --help` or `.\bin\endermux-client.bat --help` on Windows with a Java 25+ `JAVA_HOME`

## Building

Use the Gradle wrapper to build the project (`./gradlew build` on Unix, `.\gradlew.bat build` on Windows).

Common build tasks include:
- `./gradlew build` - compile and check everything
- `./gradlew :endermux-client:installDist` - build and install the client to `./endermux-client/build/install/endermux-client`
- `./gradlew publishToMavenLocal` - publish the project to your local Maven repository for testing
