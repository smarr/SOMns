# Basic User Setup

A brief overview for a basic development setup for SOMns.

## Minimal Software Requirements

SOMns works on Java 8 or later, uses Ant as a build system,
git as source control system, and Python for a launcher script.

We test SOMns on Linux and macOS. Windows is not currently supported.

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