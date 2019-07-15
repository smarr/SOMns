#!/bin/bash -eux
echo ""
echo "Get Eclipse"
echo ""
wget http://ftp.halifax.rwth-aachen.de/eclipse//technology/epp/downloads/release/2019-03/R/eclipse-java-2019-03-R-linux-gtk-x86_64.tar.gz
tar xf eclipse-java-2019-03-R-linux-gtk-x86_64.tar.gz

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export JVMCI_VERSION_CHECK=ignore

echo "" >> ~/.profile
echo "# Export JVMCI_HOME for SOMns" >> ~/.profile
echo "export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64"

git clone ${GIT_REPO} ${REPO_NAME}

cd ${REPO_NAME}
git checkout ${COMMIT_SHA}
git submodule update --init --recursive
ant
rebench -S -B --faulty --setup-only ${REBENCH_CONF} SOMns
