# CI/CD Pipeline & Native Packaging

A modern desktop application is useless if users are forced to install Java and run Maven commands to use it. This project includes an enterprise-grade CI/CD pipeline that completely automates native distribution.

## 1. The Fat JAR (`maven-shade-plugin`)
When the codebase is compiled, the `maven-shade-plugin` bundles your source code, the JavaFX UI libraries, and all dependencies into a single, massive "Fat JAR".

**Crucial Fix:** Because JavaFX dependencies often contain cryptographic signature files (`.SF`, `.DSA`), merging them into a Fat JAR invalidates their signatures, causing a fatal `SecurityException` upon launch on Windows. The `pom.xml` explicitly strips the `META-INF` signature files during the build to guarantee a smooth launch.

## 2. GitHub Actions Matrix
Located in `.github/workflows/build-and-release.yml`, the automated pipeline triggers automatically whenever you push a Git tag (e.g., `git push origin v1.0.0`).

It uses a build matrix to simultaneously boot up three Microsoft cloud servers. Because JavaFX native libraries (`.dll`, `.dylib`, `.so`) are platform-specific, compiling the code directly on the target OS guarantees the Fat JAR contains the correct native hooks.


## 3. Java `jpackage` 
Once the Fat JAR is compiled on the cloud server, the pipeline executes Java's built-in `jpackage` utility.
`jpackage` takes the Fat JAR, strips down a minimal copy of the Java 21 Runtime Environment (JRE) using `jlink`, and bundles them together into a native desktop installer.

The GitHub Action then uses an automated bot (with `contents: write` permissions) to automatically publish all 5 of these files to the GitHub Releases page.
