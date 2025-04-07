# Maze

Maze is a dynamic symbolic execution (DSE) engine for automated test generation.
It uses a combination of symbolic and concrete execution to explore program paths and generate test cases that mazimize code coverage.

The project was developed as part of a master's thesis at Utrecht University.
The thesis focuses on comparing the effectiveness of different search strategies and heuristics in DSE engines.

## Getting Started

To get started with Maze, follow the instructions below to set up your environment and run the application.

### Prerequisites

Before you begin, ensure you have the following software installed on your machine:

- Java Development Kit (JDK) 21 or higher
- Apache Maven
- Z3 Theorem Prover (see [Installing Z3](#installing-z3))

### Building the Project

Clone the repository and build the project using Maven:

```bash
git clone https://github.com/ThijnK/maze.git
cd maze
mvn clean install
```

### Running the Application

You can run the application using the following Maven command:

```bash
mvn exec:java -Dexec.args="--help"
```

This will display the help message with available options and arguments.

Alternatively, you can run the packaged JAR file directly:

```bash
java -jar target/maze-1.0-jar-with-dependencies.jar --help
```

For example, to run the application on a specific Java class located in the `/tareget/classes` directory using BFS (rather than the default DFS), use the following command:

```bash
java -jar target/maze-1.0-jar-with-dependencies.jar --classPath target/classes --className com.example.MyClass --outPath tests --strategy BFS
```

### Installing Z3

Z3 is a theorem prover developed by Microsoft, which is used in this project to solve constraints in the symbolic execution engine.

Start by downloading the native distribution for your platform from the [Z3 GitHub releases](https://github.com/Z3Prover/z3/releases) page, for example `z3-4.13.3-x64-win.zip` for Windows x64.
Extract the contents of the zip file to a directory of your choice, for example `C:\Program Files\z3`.
Go to your system environment variables and set a variable `Z3_HOME` to the path where you extracted the zip file, for example `C:\Program Files\z3`.
Add `%Z3_HOME%\bin` to your system `PATH` variable.

Now to use the Z3 jar with maven, you need to install it to your local maven repository:

```bash
mvn install:install-file -Dfile="C:\Program Files\z3\bin\com.microsoft.z3.jar" -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 -Dpackaging=jar -DgeneratePom=true
```

Replace the path and version number in the command above with the correct values for your system and the version of Z3 you downloaded.

**Note**: It is possible that Z3 will not work after installing it this way, in which case your best bet is to build Z3 from source as described below.

#### Building Z3 from source

If for some reason you need to build Z3 from source, follow the instructions below.

Clone or download the Z3 repository from https://github.com/Z3Prover/z3, and run the following command in the Z3 repo:

```bash
python scripts/mk_make.py --java -x
```

If you need to build for x86 instead of x64, leave out the `-x` flag.
Now run the following commands to build the java bindings:

```bash
cd build
nmake
```

If you do not have `nmake`, install it using Visual Studio Installer.
You'll also need some other C++ build tools, which you can install using the Visual Studio Installer as well.

After building the java bindings, the `build` directly should contain the files needed to run Z3, including the `com.microsoft.z3.jar` file.
Set the environment variables and install into your local maven repository as described above.

## Dependencies

Maze relies on the following libraries and frameworks to function effectively:

- [SootUp](https://soot-oss.github.io/SootUp/latest/) for Java bytecode analysis and transformation.
- [Z3 Theorem Prover](https://github.com/Z3Prover/z3) for constraint solving.
- [ASM](https://asm.ow2.io/) for bytecode manipulation.
- [JavaPoet](https://github.com/square/javapoet) for Java source code generation.
- [Logback](https://logback.qos.ch/) for logging.
- [JUnit 5](https://junit.org/junit5/) for testing.
- [Picocli](https://picocli.info/) for command-line argument parsing.

## License

This project is licensed under the [MIT license](./LICENSE).
