#!/bin/sh
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
python -m black ${SCRIPT_DIR}/mx_somns.py ${SCRIPT_DIR}/suite.py
python -m pylint ${SCRIPT_DIR}/mx_somns.py
