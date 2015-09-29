#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr

USAGE_STRING="
# Usage: event_run_descriptor_extraction.sh \"EVENT_NAME\" [ --force-start ] [ --overwrite-all ]
#
# --parallel N
#     -> N instances of descriptor extraction in parallel
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
EVENT_NAME=""
FORCE_START=NO
OVERWRITE_ALL=NO
CLEAN_STATE_RUNNING=NO
NB_INSTANCES=1

# accept collection dir as environment variable
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
		--parallel)
		NB_INSTANCES="$2"
		shift
		re='^[0-9]+$'
		if ! [[ $NB_INSTANCES =~ $re ]] ; then
			echo "$USAGE_STRING"
			log_ERR "--parallel: Expecting an integer"
			exit 1
		fi
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

if [[ "$EVENT_NAME" == "" ]]; then
	echo "$USAGE_STRING"
	exit 1
fi

EV_DIR="${COLLECTION_DIR}/events/${EVENT_NAME}"
WORK_DIR="$EV_DIR/workdir"
STATUS_FILE="$WORK_DIR/status"
COMP_DESC_QUEUE_FILE="$WORK_DIR/compute_descriptors_queue"
VIDS_WORK_DIR="${COLLECTION_DIR}/processing/videos_workdir/"
CHANNELS_FILE="${WORK_DIR}/channels.list"


echo      "-----------------------------------"
log_TITLE "Training: run descriptor extraction"
log_INFO "Event ${EVENT_NAME}"

CHANNELS=""
while read -r channel; do
	CHANNELS="${CHANNELS} -c ${channel}"
done < "${CHANNELS_FILE}"

OTHER_PARAMS=""
if [ $FORCE_START == YES ]; then
	OTHER_PARAMS="${OTHER_PARAMS} --force-start"
fi
if [ $CLEAN_STATE_RUNNING == YES ]; then
	OTHER_PARAMS="${OTHER_PARAMS} --clean-state-running"
fi
if [ $OVERWRITE_ALL == YES ]; then
	OTHER_PARAMS="${OTHER_PARAMS} --overwrite-all"
fi
./video_run_descriptor_extraction.sh --event-name "${EVENT_NAME}" ${CHANNELS} --video-list "${COMP_DESC_QUEUE_FILE}" --parallel ${NB_INSTANCES} ${OTHER_PARAMS}





