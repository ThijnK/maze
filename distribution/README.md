# MAZE Linux package

This package contains MAZE, its Java dependencies, matching Z3 4.13.3 native
libraries, and complete search-extension examples. It requires Java 21 or newer,
Linux with glibc (validated on Ubuntu 24.04), and the architecture named in the archive
(`arm64` or `amd64`). Use a JDK if you also want to compile subjects or extensions.
You do not need Maven or a separate Z3 installation.

MAZE reads compiled `.class` files. Build your project first, or compile a source
file with `javac -d classes path/to/MyClass.java`. Set `--classpath` to the output
root (such as `classes` or Maven's `target/classes`) and `--class-name` to the Java
class name, including its package and without a `.java` or `.class` suffix.

From the extracted directory:

```sh
./maze --help
./maze --classpath /path/to/classes --class-name example.MyClass \
  --output-path generated --strategy BFS --time-budget 30
```

The launcher finds Z3 relative to itself. Subject, plugin, configuration, and
output paths remain relative to your working directory. You can set `JAVA_HOME`
to choose Java. For JVM flags, use Java's `JDK_JAVA_OPTIONS`, for example
`JDK_JAVA_OPTIONS='-ea -Xmx2g' ./maze ...` for assertion checking and a heap limit.

## Build and run an extension

From the extracted directory, with a JDK available:

```sh
mkdir -p extension-classes subject-classes
javac -cp maze.jar -d extension-classes examples/search-extensions/src/research/*.java
jar --create --file research.jar -C extension-classes .
javac -d subject-classes examples/search-extensions/subject/example/*.java
./maze --plugin research.jar \
  --search-config examples/search-extensions/search.json \
  --classpath subject-classes --class-name example.SmokeSubject \
  --output-path generated/symbolic --time-budget 10
./maze --plugin research.jar \
  --search-config examples/search-extensions/search.json \
  --classpath subject-classes --class-name example.SmokeSubject \
  --output-path generated/concrete --time-budget 10 --concrete-driven
```

The configuration interleaves two differently configured instances of the example
strategy with a probabilistic strategy using an external heuristic. For a baseline,
omit `--plugin` and `--search-config` and use `--strategy BFS` instead.
See [the extension author guide](docs/search-extensions.md) for the API and lifecycle;
the build-from-source instructions there are unnecessary with this package.

## Run inside Docker

Select a package matching the **container's** architecture. From the extracted
directory, this mounts the package and starts a Java 21 development shell:

```sh
docker run --rm -it -v "$PWD:/experiment" -w /experiment \
  eclipse-temurin:21-jdk-noble bash
```

Run the commands above in that shell. This is a standard Java image, not a MAZE
image; the native solver comes from this package. Mount any additional subjects
or research directories too, and use their paths inside the container.

The complete CLI and usage documentation are in the
[MAZE README](https://github.com/ThijnK/maze#using-maze).
