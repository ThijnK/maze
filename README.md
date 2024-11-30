# Symbolicx

## Installing z3

The project uses the z3 library for SMT solving.
To install the necessary java bindings for z3, follow the steps below.

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

After building the java bindings, there should be a file `com.microsoft.z3.jar` in the `build` directory.
Note the path to this file and use it in the next step.
Run the following command to install the jar file to your local maven repository:

```bash
mvn install:install-file -Dfile=path/to/com.microsoft.z3.jar -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 -Dpackaging=jar
```

Note that the version number in the command above should match the version of z3 you are using (check the Z3 GitHub page).

That's it! You should now be able to build the project.
