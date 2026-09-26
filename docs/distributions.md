# Linux packages

MAZE's distribution archive contains its executable dependency JAR, matching Z3
4.13.3 native libraries, a launcher, licenses, and complete extension examples.
Users need Java 21 or newer; a JDK is needed to compile subjects or extensions.
Maven and a separate Z3 installation are unnecessary.

Packages currently target Linux ARM64 and x86-64 with glibc. The archive checks
use Ubuntu 24.04 in Docker; x86-64 is checked under emulation on Apple Silicon.
These are Linux packages, not native macOS, Windows, or Alpine Linux packages.
On macOS or Windows, use [Docker Desktop](https://docs.docker.com/desktop/)
with Linux containers and a package
matching the container's architecture. Java and Z3 then run inside the container.

Download packages and checksums from [GitHub Releases](https://github.com/ThijnK/maze/releases).
The commands below describe building and validating packages locally.
There is no dedicated MAZE runtime image.

## Use an archive

Unpack `maze-<version>-linux-<architecture>.tar.gz`, enter the extracted directory,
and run `./maze --help`. The included README explains how to run MAZE and build
an extension against `maze.jar`. The launcher locates Z3 relative to itself;
subject and output paths remain relative to the caller's working directory.

To use an unpacked package without installing Java on your host:

```sh
docker run --rm -it -v "$PWD:/experiment" -w /experiment \
  eclipse-temurin:21-jdk-noble bash
./maze --help
```

On Windows, run the equivalent command from PowerShell in the extracted directory:

```powershell
docker run --rm -it --mount "type=bind,source=$($PWD.Path),target=/experiment" --workdir /experiment eclipse-temurin:21-jdk-noble bash
```

Inside the container, run `./maze --help` and the examples from the package README.
A JDK is included in this image, so you can compile your subject's Java sources
with `javac` there too. MAZE itself expects compiled classes.

Run these commands from the extracted directory. Mount additional research directories as
needed. Set `JDK_JAVA_OPTIONS` for JVM options such as `-ea` or `-Xmx2g`.

## Build a candidate

From the MAZE checkout, build and test the JAR, then package it with the pinned
development image's matching native libraries:

```sh
docker compose build dev
docker compose run --rm dev mvn -B -ntp clean verify
docker compose run --rm dev sh distribution/build.sh
```

The archive and SHA-256 file appear in `target/distributions/`. The package name
includes MAZE's version and the container architecture. Packaging checks that the
Java binding in the JAR matches the one supplied with the native libraries.

For an x86-64 package on an ARM64 host, build the development image for x86-64 and
package the same Java artifact with that image's native libraries:

```sh
docker build --platform linux/amd64 -t maze-dev:amd64 docker/dev
docker run --rm --platform linux/amd64 --entrypoint sh \
  -v "$PWD:/workspace" -w /workspace maze-dev:amd64 distribution/build.sh
```

## Validate before publishing

Test the **exact archive** in a fresh container, mounting only the downloads and
validation sources. This image supplies Java but contains no Z3 installation.
Networking is disabled during the check. Pull the image first if it is not cached:

```sh
docker pull maven:3.9.16-eclipse-temurin-21-noble@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18
docker run --rm --network none \
  -v "$PWD/target/distributions:/downloads:ro" \
  -v "$PWD/distribution:/checks:ro" \
  maven:3.9.16-eclipse-temurin-21-noble@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18 \
  sh /checks/verify.sh /downloads/maze-1.2.1-linux-arm64.tar.gz
```

Use `--platform linux/amd64` for both pull and run, and the `amd64` archive name,
to check x86-64. Replace the version in these examples when preparing a new release.
Maven is present in this pinned base image but is not used: the check compiles
against the downloaded JAR and runs the JUnit dependency it already contains.

The check unpacks into a fresh directory containing spaces, compiles the included
extensions and subject, and runs baseline, external-strategy, external-heuristic,
and composed configurations in both modes. It requires matching completion
records, compiles and runs all generated suites, and checks that an invalid
extension exits unsuccessfully with a failed record.

For each release, upload only archives that passed this check, together with
their adjacent `.sha256` files. Publish the same bytes that were tested. Record
the platforms and whether checks were native or emulated in the release notes;
a passing Linux check does not justify publishing untested platform packages.
