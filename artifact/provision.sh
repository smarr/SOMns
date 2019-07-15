#!/bin/bash -eux

wget -O- https://deb.nodesource.com/setup_8.x | bash -

apt-get update
apt-get install -y openjdk-8-jdk openjdk-8-source python-pip ant nodejs

pip install git+https://github.com/smarr/ReBench

# enable nice without sudo
echo "artifact       -     nice       -20" >> /etc/security/limits.conf
