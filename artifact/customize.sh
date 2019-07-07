#!/bin/bash -eux

echo ""
echo "Customize Image"
echo ""

cd ~/
## Create Links on Desktop
mkdir -p ~/Desktop
ln -s ~/eclipse/eclipse ~/Desktop/eclipse

ln -s ~/${REPO_NAME} ~/Desktop/${REPO_NAME}
ln -s ~/${REPO_NAME}/README.md ~/Desktop/README.md

echo ""
echo "Customization Done"
echo ""
