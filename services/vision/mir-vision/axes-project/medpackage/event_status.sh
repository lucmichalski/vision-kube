#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr

USAGE_STRING="
# Usage: event_status.sh \"EVENT_NAME\" [ --collection-dir DIR ]
#
# Reports status string and jobs currently in queue where applicable.
#
# --collection-dir DIR
#     If your events are defined in a separate collection directory, specify it here.
#     This defaults to the package's base directory, and dictates where descriptors will be saved.
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

set -e
set -u
source processing/usr/scripts/bash_utils.sh

ERROR_COLOR="${TXT_BOLD}${TXT_RED}ERROR${TXT_RESET}"
WARNING_COLOR="${TXT_RED}WARNING${TXT_RESET}"

#
# PARSE ARGUMENT
#

# accept collection dir as environment variable
COLLECTION_DIR=${COLLECTION_DIR:='./'}
EVENT_NAME=""
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
		--collection-dir)
		COLLECTION_DIR="$2"
		shift
		;;
		*)
		# Event name here
		EVENT_NAME=$key
		;;
	esac
	shift
done
if [[ "$EVENT_NAME" == "" ]]; then echo "${USAGE_STRING}"; exit 1; fi


echo      "--------------------"
#log_TITLE "${EVENT_NAME}"

#
# CHECK EXISTENCE
#
EV_DIR="${COLLECTION_DIR}/events/${EVENT_NAME}"
WORK_DIR="$EV_DIR/workdir"
STATUS_FILE="$WORK_DIR/status"
POSITIVE_FILE="$WORK_DIR/_positive.txt"
BACKGROUND_FILE="$WORK_DIR/_background.txt"
COMP_DESC_QUEUE_FILE="$WORK_DIR/compute_descriptors_queue"
VIDS_WORK_DIR="${COLLECTION_DIR}/processing/videos_workdir/"
CLASSIFIERS_DIR="$EV_DIR/classifiers"

# EVENT NAME AND STATUS
#echo "${TXT_BOLD}Event: ${TXT_RESET}${EVENT_NAME}"
if [[ ! -e "$STATUS_FILE" ]]; then
	log_ERR "status file does not exist, state unclean"
	exit 1
fi

# VIDEOS WE HAVE
if [[ -e "$POSITIVE_FILE" && -e "$BACKGROUND_FILE" ]]; then
	NB_POS=`cat "${POSITIVE_FILE}" | wc -l`
	NB_BG=`cat "${BACKGROUND_FILE}" | wc -l`
	echo "$(( ${NB_POS} + ${NB_BG} )) training videos (${NB_POS} positive and ${NB_BG} background)."
else
	log_ERR "Couldn't find training video lists."
	exit 1
fi

NB_TOT=$(( $NB_POS + $NB_BG ))

# DESCRIPTORS PROCESSING
# -- running, done and waiting
while read -r CHANNEL; do
	NB_RUNNING=0
	NB_DONE=0
	NB_WAITING=0

	set +e
	while read -r video; do
		VID_STATUS=`cat "${VIDS_WORK_DIR}${video}/${CHANNEL}_extraction_status" 2> /dev/null`
		case "$VID_STATUS" in
			"running")
			NB_RUNNING=$(( $NB_RUNNING + 1 ))
			;;
			"done")
			NB_DONE=$(( $NB_DONE + 1 ))
			;;
			*)
			NB_WAITING=$(( $NB_WAITING + 1 ))
			;;
		esac
	done < "$COMP_DESC_QUEUE_FILE"

	echo ""
	echo -e "${TXT_BOLD}${CHANNEL}${TXT_RESET}"
	if [[ $NB_DONE == $NB_TOT ]]; then
		echo -e "${TXT_BOLD}${TXT_GREEN}${TXT_BG_GREEN}Descriptors:${TXT_RESET} waiting: $NB_WAITING; running: ${NB_RUNNING}; done: ${NB_DONE}."
	elif [[ $NB_RUNNING > 0 ]]; then
		echo -e "${TXT_BOLD}Descriptors:${TXT_RESET} waiting: $NB_WAITING; ${TXT_BOLD}${TXT_WHITE}${TXT_BG_BLUE}running: ${NB_RUNNING}${TXT_RESET}; done: ${NB_DONE}."
	else
		echo -e "${TXT_BOLD}Descriptors:${TXT_RESET} waiting: $NB_WAITING; running: ${NB_RUNNING}; done: ${NB_DONE}."
	fi
done < "${WORK_DIR}/channels.list"
