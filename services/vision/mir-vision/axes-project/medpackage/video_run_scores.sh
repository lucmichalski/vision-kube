#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr

USAGE_STRING="
# Usage: video_run_scores.sh [--event \"EVENT_NAME\"]* [--event-list \"FILENAME\"]* [--video \"VIDNAME\"]* [--video-list \"FILENAME\"]* [ --force-start ] [ --overwrite-all ]
#
# --force-start
#     -> videos already registered as being processed for another event are started anyway
#
# --clean-state-running
#     -> all videos marked as 'running' get a clean slate
#     -> no jobs will be launched with this option
#
# --overwrite-all
#     -> starts extraction for all videos, even those already processed
#
# --event
# --event-list
# --video
# --video-list
#
# --output-directory DIR
#
# --collection-dir DIR
#     If your events are defined in a separate collection directory, specify it here.
#     This defaults to the package's base directory, and dictates where descriptors will be saved.
#
# RETURN VALUE (non-parallel): number of missing videos
#
# Requires all components to be compiled.
"
#
# SWITCHING TO MED DIRECTORY
#
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
cd "${DIR}"

set -u
source processing/usr/scripts/bash_utils.sh

##
## PARSE ARGUMENTS
##
EVENT_LIST=()
VIDEO_LIST=()
OUTPUT_DIRECTORY=""
COLLECTION_DIR=${COLLECTION_DIR:='./'}
while [[ $# > 0 ]]
	do
	key="$1"

	case $key in
#    	-e|--extension)
#	    EXTENSION="$2"
#	    shift
#	    ;;
		-h|--help)
		echo "$USAGE_STRING"
		exit 1
		;;
		--event)
		EVENT_LIST+=( "$2" )
		shift
		;;
		--event-list)
		while read -r event; do
			EVENT_LIST+=( "${event}" )
		done < "$2"
		shift
		;;
		--video)
		VIDEO_LIST+=( "$2" )
		shift
		;;
		--video-list)
		while read -r video; do
			VIDEO_LIST+=( "${video}" )
		done < "$2"
		shift
		;;
		--output-directory)
		OUTPUT_DIRECTORY="$2"
		shift
		;;
		--force-start)
		FORCE_START=YES
		;;
		--clean-state-running)
		CLEAN_STATE_RUNNING=YES
		;;
		--overwrite-all)
		OVERWRITE_ALL=YES
		;;
		--collection-dir)
		COLLECTION_DIR="$2"
		shift
		;;
		*)
		# Event name here
		EVENT_NAME="$key"
		;;
	esac
	shift
done

if [[ ${#EVENT_LIST[@]} -eq 0 ]]; then
	echo "$USAGE_STRING"
	log_ERR "Need at least one event to rank."
	exit 1
fi

if [[ ${#VIDEO_LIST[@]} -eq 0 ]]; then
	echo "$USAGE_STRING"
	log_ERR "Need at least one video to rank."
	exit 1
fi

if [ "${OUTPUT_DIRECTORY}" == "" ]; then
	echo "$USAGE_STRING"
	log_ERR "Need to specify the scores output directory (--output-directory)."
	exit 1
fi

# test whether lockfile is available


source processing/config_MED.sh
mkdir -p ${OUTPUT_DIRECTORY}

which lockfile > /dev/null
if [ $? -eq 0 ]; then
	COMPUTE_SCORES_SCRIPT_PY="processing/compute_scores/compute_scores.py"
else
	COMPUTE_SCORES_SCRIPT_PY="processing/compute_scores/compute_scores_singlethread.py"
	log_WARN "Couldn't find \"lockfile\", scores will be computed in single-threaded mode."
fi

export COLLECTION_DIR
python ${COMPUTE_SCORES_SCRIPT_PY} --videos <( for (( i=0; i < ${#VIDEO_LIST[@]}; i++ )); do echo ${VIDEO_LIST[${i}]}; done )\
                    --events <( for (( i=0; i < ${#EVENT_LIST[@]}; i++ )); do echo ${EVENT_LIST[${i}]}; done ) --output-directory ${OUTPUT_DIRECTORY}



