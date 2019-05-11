#!/bin/bash -eux
echo ""
echo "Get Eclipse"
echo ""
wget http://ftp.halifax.rwth-aachen.de/eclipse//technology/epp/downloads/release/2019-03/R/eclipse-java-2019-03-R-linux-gtk-x86_64.tar.gz
tar xf eclipse-java-2019-03-R-linux-gtk-x86_64.tar.gz


echo ""
echo "Build GraalBasic, a Graal-enabled JDK"
echo ""

mkdir -p ~/.local
git clone https://github.com/smarr/GraalBasic.git
cd GraalBasic
#git checkout d37bbe4de590087231cb17fb8e5e08153cd67a59

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
./build.sh

## Clean Graal Build Folder, not needed
export JVMCI_VERSION_CHECK=ignore
export JAVA_HOME=~/.local/graal-core
#/usr/lib/jvm/java-8-openjdk-amd64/
(cd graal-jvmci-8;    ../mx/mx clean)
(cd truffle/compiler; ../../mx/mx clean)
(cd truffle/sdk;      ../../mx/mx clean)
unset JAVA_HOME

cd ..
export JVMCI_HOME=~/.local/graal-core
echo "" >> ~/.profile
echo "# Export JVMCI_HOME for SOMns" >> ~/.profile
echo "export JVMCI_HOME=~/.local/graal-core" >> ~/.profile
echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64"

git clone ${GIT_REPO} ${REPO_NAME}

cd ${REPO_NAME}
git checkout ${COMMIT_SHA}
git submodule update --init --recursive
ant
rebench -S -B --faulty --setup-only ${REBENCH_CONF} SOMns
