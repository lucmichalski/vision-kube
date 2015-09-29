#!/bin/bash

#
# FIND MED DIRECTORY (+ ./processing/)
#
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

export MED_BASEDIR="${DIR}/"

export LD_LIBRARY_PATH=/usr/lib64:${LD_LIBRARY_PATH:=''}

export PATH=${MED_BASEDIR}usr/bin:${MED_BASEDIR}usr/yael/progs:${MED_BASEDIR}usr/scripts:${PATH}
export LD_LIBRARY_PATH=${MED_BASEDIR}usr/yael/yael:${LD_LIBRARY_PATH}
export PYTHONPATH=${MED_BASEDIR}usr/yael:${MED_BASEDIR}compute_index/event_recognition_axes:${MED_BASEDIR}usr/py

