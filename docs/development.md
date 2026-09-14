# Developing and testing in Docker

With Docker running and Docker Compose available, use the development container
to build and test MAZE. It includes Java 21, Maven, and matching Z3 4.13.3 Java
bindings and native libraries, so you do not need to install or configure them
separately.

The checkout is mounted at `/workspace`, so edits take effect on the next build
or test run without rebuilding the image. MAZE's sources are not copied into the
image. Maven dependencies are stored in a project-specific Docker volume, not
your host's Maven repository. Generated files go into ignored `target/` and `tmp/`
directories in the checkout.

## Build the environment

From the repository root:

```sh
docker compose build dev
docker compose run --rm dev mvn --version
```

The Dockerfile pins the official Maven/Java image by digest and checks the SHA-256
of the Z3 archive for the selected architecture. It downloads from the official
[Z3 4.13.3 release](https://github.com/Z3Prover/z3/releases/tag/z3-4.13.3).
The Linux ARM64 or x86-64 archive is selected to match the container architecture.

The image build exercises Z3 through its Java binding with satisfiable and
unsatisfiable queries. A missing JNI library or incompatible binary fails the
build. The matching Java binding is installed in the Maven image's reference
repository, which seeds each Docker Maven cache at startup.

The full test suite and packaged-JAR checks have been exercised on Linux ARM64
through Docker Desktop on Apple Silicon. On x86-64, validation so far covers image
construction and Z3 Java/native loading under emulation; the full suite and
packaged-JAR checks still need to be run there.

The default Compose limits are four CPUs and 6 GiB of memory. Make sure Docker
has enough resources available, or adjust `compose.yaml` for your setup.

## Run the test suite

```sh
docker compose run --rm dev mvn --batch-mode --no-transfer-progress clean verify
```

The container creates `/workspace/tmp` before executing your command. Existing
MAZE tests expect this directory to exist when they write generated Java files.
Test reports are written to `target/surefire-reports/` in your checkout.

For a focused test run:

```sh
docker compose run --rm dev mvn --batch-mode --no-transfer-progress \
  -Dtest=ExceptionalFlowFindingTest test
```

## Exercise the packaged JAR

A successful `clean verify` also produces the executable JAR. Exercise it with
the separate end-to-end check:

```sh
docker compose run --rm dev sh docker/smoke/verify.sh \
  target/maze-1.1.1-jar-with-dependencies.jar
```

The smoke script cleans and compiles a separate example project, runs the
packaged MAZE JAR in symbolic-driven and concrete-driven modes, then compiles and
executes each generated JUnit suite. Its Maven project does not depend on MAZE's
source or test classes. It fails when generation exits unsuccessfully, no tests
are found, compilation fails, or a generated test fails. Old generated tests are
removed before the run so they cannot make it pass accidentally.

Generated test sources and reports are under:

```text
docker/smoke/target/generated-tests/symbolic/
docker/smoke/target/generated-tests/concrete/
docker/smoke/target/surefire-reports/symbolic/
docker/smoke/target/surefire-reports/concrete/
```

To check external extensions against the packaged artifact:

```sh
docker compose run --rm dev sh docker/extensions/verify.sh \
  target/maze-1.1.1-jar-with-dependencies.jar
```

This copies only the JAR, example sources/configuration, and validation sources
into a fresh `/tmp/maze-extension-check.*` directory inside the container. It
compiles separate strategy/heuristic JARs, exercises both modes and failure cases,
and runs generated tests. It also checks verification limits, executes generated
counterexamples, and verifies file/log export destinations. The printed directory
is inside the container; capture stdout if you need a persistent validation log. See the
[author guide](search-extensions.md) for the same build/run workflow by hand.

## Everyday commands

Open a shell with the toolchain and checkout available:

```sh
docker compose run --rm dev bash
```

Show command-line help:

```sh
docker compose run --rm dev java -jar target/maze-1.1.1-jar-with-dependencies.jar --help
```

Use the same pattern for ordinary MAZE invocations. Paths inside the checkout are
relative to `/workspace`. The CLI creates its output directory when starting the
invocation record; direct engine users should prepare their output directories.

Containers are removed by `--rm`. The image and Maven cache remain for subsequent
runs. Rebuild the image when its Dockerfile or toolchain changes; editing MAZE's
Java files only requires rerunning Maven. Avoid concurrent Maven runs against the
same checkout because they share build output and the existing tests share files.

To remove this Compose project's cache and network when you no longer need them:

```sh
docker compose down --volumes
```

This removes the project's cached dependencies, which must be downloaded again
next time. It does not remove files from the checkout or other Compose projects.
Depending on your Docker setup, files created through the bind mount may be owned
by root because the development container runs as root.

## Troubleshooting

### Connecting to Docker

If Docker cannot connect to its daemon, make sure the Docker engine is running
and your selected Docker context points to it. `docker --version` only checks
that the client is installed.
