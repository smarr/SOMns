# Basic User Setup

A brief overview for a basic development setup for SOMns.

## Minimal Software Requirements

SOMns works on Java 8+JVMCI, Java 9, 10 and 11, uses Ant as a build system,
git as source control system, and Python for a launcher script.

For performance, SOMns relies on the Graal compiler using the JVMCI,
which is present by default in Java starting from Java 9.
To use Java 8, one needs to use the [Oracle Labs JDK 8](https://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) instead of the default JDK 8.

Although SOMns can be used with Java 9, 10, and 11, the Substrate VM dependency
can be compiled so far only using JDK 8+JVMCI. To work around this problem,
the build process includes two environment variables, `JAVA_HOME` and `JVMCI_HOME`.
`JVMCI_HOME` must point to the JDK 8+JVMCI home directory,
while `JAVA_HOME` can point to a standard JDK 8 or later.

We test SOMns on Linux and macOS (fully under JDK 8+JVMCI, and in Java 11 mixed
with Java 8+JVMCI). Windows is not currently supported.

On Ubuntu, the following instructions will install the necessary dependencies,
in this case JDK 11:

```bash
sudo apt install openjdk-11-jdk git ant
```

On macOS, the relevant dependencies can be installed, for instance with
[Homebrew](https://brew.sh/):

```bash
brew tap caskroom/versions
brew cask install java
brew install ant
```

MacPorts or other Linux package manager should allow the installation of
dependencies with similar instructions.

In addition to the above dependencies, the [Oracle Labs JDK 8](https://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html)
has to be installed.

The environment variables `JAVA_HOME` and `JVMCI_HOME` have also to be set,
so that `JVMCI_HOME` points to a JDK 8 + JVMCI and `JAVA_HOME` to the desired Java version.
On a bash-compatible shell on macOS, the commands would be similar to:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
export JVMCI_HOME=/Library/Java/JavaVirtualMachines/labsjdk1.8.0_192-jvmci-0.53/Contents/Home
```

## Getting the Code and Running Hello World

After the dependencies are installed, the code can be checked out with:

```bash
git clone https://github.com/smarr/SOMns.git
```

Then, SOMns can be built with Ant:

```bash
cd SOMns
ant compile  ## will also download dependencies
```

Afterwards, the simple Hello World program is executed with:

```bash
./som core-lib/Hello.ns
```
