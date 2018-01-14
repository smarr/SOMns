# Basic User Setup

A brief overview for a basic development setup for SOMns.

## Minimal Software Requirements

SOMns is implemented in Java 8, uses Ant as a build system, git as
source control system, and Python for a launcher script.

On Ubuntu, the following instructions will install the necessary dependencies:

```bash
sudo add-apt-repository ppa:webupd8team/java
sudo apt install oracle-java8-installer git ant
```

## Getting the Code and Running Hello World

To checkout the code:

```bash
git clone https://github.com/smarr/SOMns.git
```

Then, SOMns can be build with Ant:

```bash
cd SOMns
ant compile  ## will also download dependencies
```

Afterwards, the simple Hello World program is executed with:

```bash
./som -G core-lib/Hello.ns
```
