# Basic User Setup

A brief overview for a basic development setup for SOMns.

## Minimal Software Requirements

SOMns requires Java 9, uses Ant as a build system, git as
source control system, and Python for a launcher script.

We test SOMns on Linux and macOS. Windows is not currently supported.

On Ubuntu, the following instructions will install the necessary dependencies:

```bash
sudo add-apt-repository ppa:webupd8team/java
sudo apt install oracle-java9-installer git ant
```

On macOS, the relevant dependencies can be installed, for instance with
[Homebrew](https://brew.sh/):

```bash
brew tap caskroom/versions
brew cask install java
brew install ant
export JAVA_HOME=`/usr/libexec/java_home`
```

MacPorts or other Linux package manager should allow the installation of
dependencies with similar instructions.


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
