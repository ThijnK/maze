# Maze

## Installing Z3

Z3 is a theorem prover developed by Microsoft, which we use to solve constraints in the symbolic execution engine.

Start by downloading the native distribution for your platform from the [Z3 GitHub releases](https://github.com/Z3Prover/z3/releases) page, for example `z3-4.13.3-x64-win.zip` for Windows x64.
Extract the contents of the zip file to a directory of your choice, for example `C:\Program Files\z3`.
Go to your system environment variables and set a variable `Z3_HOME` to the path where you extracted the zip file, for example `C:\Program Files\z3`.
Add `%Z3_HOME%\bin` to your system `PATH` variable.

Now to use the Z3 jar with maven, we need to install it to our local maven repository:

```bash
mvn install:install-file -Dfile="C:\Program Files\z3\bin\com.microsoft.z3.jar" -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 -Dpackaging=jar -DgeneratePom=true
```

Replace the path and version number in the command above with the correct values for your system and the version of Z3 you downloaded.

### Building Z3 from source

If for some reason you need to build Z3 from source, follow the instructions below.

Clone or download the z3 repository from https://github.com/Z3Prover/z3, and run the following command in the z3 repo:

```bash
python scripts/mk_make.py --java -x
```

If you need to build for x86 instead of x64, leave out the `-x` flag.
Now run the following commands to build the java bindings:

```bash
cd build
nmake
```

If you do not have nmake, install it using Visual Studio Installer.
You'll also need some other C++ build tools, which you can install using the Visual Studio Installer as well.

After building the java bindings, the `build` directly should contain the files needed to run Z3, including the `com.microsoft.z3.jar` file.
Set the environment varialbes and install into your local maven repository as described above.

## License

This project is licensed under the [MIT license](./LICENSE).
