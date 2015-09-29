#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr

USAGE_STRING="
# Usage: event_init.sh \"EVENT_NAME\" [ --positive-vids POSITIVE_LIST.TXT ] [ --background-vids BG_LIST.TXT ] [ --overwrite ] [ --ignore-missing ] [ --collection-dir DIR ]
#
#	--positive-vids POSITIVE_LIST.TXT
#		contains list of positive videos
#		default value: \"events/EVENT_NAME/positive.txt\"
#	--background-vids BG_LIST.TXT
#		list of neutral videos
#		default value: \"events/EVENT_NAME/background.txt\".
#
#		Info: the positive and background videos are considered as a single entity (no shot detection).
#
#	--channels CHANNEL_LIST.TXT
#		text file containing the descriptor channels requested
#		default: denseTrack
#
#	--overwrite
#		writes over existing event where applicable
#
#	--check-missing
#		ensures all videos exist (slow over NFS)
#
# --collection-dir DIR
#     If your events are defined in a separate collection directory, specify it here.
#     This defaults to the package's base directory, and dictates where descriptors will be saved.
#
# Prepares the directory structure, creates a job queue.
# Does not overwrite existing data unless explicitly asked.
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

##
## PARSE ARGUMENTS
##
EVENT_NAME=""
BACKGROUND_FILE=""
POSITIVE_FILE=""
CHANNELS_FILE=""
OVERWRITE=NO
CHECK_MISSING=NO

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
		--background-vids)
		BACKGROUND_FILE="$2"
		shift
		;;
		--positive-vids)
		POSITIVE_FILE="$2"
		shift
		;;
		--channels)
		CHANNELS_FILE="$2"
		shift
		;;
		--overwrite)
		OVERWRITE=YES
		;;
		--check-missing)
		CHECK_MISSING=YES
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

if [[ "$EVENT_NAME" == "" ]]; then
	echo "$USAGE_STRING"
	exit 1
fi

echo      "--------------------"
log_TITLE "Event initialization"

EV_DIR="${COLLECTION_DIR}/events/${EVENT_NAME}"
WORK_DIR="$EV_DIR/workdir"
mkdir -p "$WORK_DIR"

STATUS_FILE="$WORK_DIR/status"
if [[ "$POSITIVE_FILE" == "" ]]; then POSITIVE_FILE="$EV_DIR/positive.txt"; fi
if [[ "$BACKGROUND_FILE" == "" ]]; then BACKGROUND_FILE="$EV_DIR/background.txt"; fi
COMP_DESC_QUEUE_FILE="$WORK_DIR/compute_descriptors_queue"


log_INFO "${TXT_BOLD}Creating \"$EVENT_NAME\"${TXT_RESET} (overwrite=${OVERWRITE}, check-missing=${CHECK_MISSING})"
log_INFO "Positive videos: \"$POSITIVE_FILE\""
log_INFO "Background videos: \"$BACKGROUND_FILE\""
log_INFO "Collection directory: \"${COLLECTION_DIR}\""
log_INFO ""

#
# CHECK IF EVENT ALREADY EXISTS
#
if [[ -e "$STATUS_FILE" && $OVERWRITE == NO ]]; then
	log_ERR "\"$EVENT_NAME\" is already initialized. Use --overwrite option to ignore this message."
	exit 1
fi

# CHECK BACKGROUND.TXT, POSITIVE.TXT
# FILL VIDEO LIST


#
# 1) EXISTS
if [[ ! -e "$POSITIVE_FILE" || ! -e "$BACKGROUND_FILE" ]]; then
	echo "$USAGE_STRING"
	log_ERR "expecting \"$POSITIVE_FILE\" and \"$BACKGROUND_FILE\""
	exit 1
fi

# 2) NON-EMPTY
if [[ `cat "$POSITIVE_FILE" | wc -l` < 1 || `cat "$BACKGROUND_FILE" | wc -l` < 1 ]]; then
	echo "$USAGE_STRING"
	log_ERR "expecting non-empty \"$POSITIVE_FILE\" and \"$BACKGROUND_FILE\""
	exit 1
fi

# 3) VIDEOS EXIST

VIDEOS=()

echo -n "" > "$WORK_DIR/_background.txt"
echo -n "" > "$WORK_DIR/_positive.txt"

if [[ $CHECK_MISSING == YES ]]; then
	NB_MISSING=0
	echo -n "" > missing_videos.txt
	
	while read -r video; do
		log_INFO "Checking ${video}"
		if [[ ! -e "${COLLECTION_DIR}/videos/$video" ]]; then
			echo "$video" >> missing_videos.txt
			NB_MISSING=$(( $NB_MISSING + 1 ))
		else
			VIDEOS+=("$video")
			echo "$video" >> "$WORK_DIR/_positive.txt"
		fi
	done < "$POSITIVE_FILE"
	while read -r video; do
		log_INFO "Checking ${video}"
		if [[ ! -e "${COLLECTION_DIR}/videos/$video" ]]; then
			echo "$video" >> missing_videos.txt
			NB_MISSING=$(( $NB_MISSING + 1 ))
		else
			VIDEOS+=("$video")
			echo "$video" >> "$WORK_DIR/_background.txt"
		fi
	done < "$BACKGROUND_FILE"
	if [[ $NB_MISSING > 0 ]]; then
		log_WARN "$NB_MISSING missing videos, see \"missing_videos.txt\" for the complete list."
	fi
	
else
	while read -r video; do
		VIDEOS+=("${video}");
	done < "${POSITIVE_FILE}"
	cp "${POSITIVE_FILE}" "$WORK_DIR/_positive.txt"
	
	while read -r video; do
		VIDEOS+=("${video}");
	done < "${BACKGROUND_FILE}"
	cp "${BACKGROUND_FILE}" "$WORK_DIR/_background.txt"
fi

log_INFO "Found ${#VIDEOS[@]} videos."
log_INFO ""

#
# CHECK CHANNELS REQUESTED
#
echo -n "" > "${WORK_DIR}/channels.list"
if [[ "${CHANNELS_FILE}" != "" ]]; then
	if [ ! -e "${CHANNELS_FILE}" ]; then
		log_ERR "Could not find channels file \"${CHANNELS_FILE}\""
		exit 1
	else
		# Check if all channels exist
		while read -r channel; do
			if [ ! -e "./processing/compute_descriptors/${channel}/${channel}_descriptors.list" ]; then
				log_ERR "Could not find channel \"${channel}\"."
				exit 1
			else
				log_INFO "Using channel ${channel}."
				echo "${channel}" >> "${WORK_DIR}/channels.list"
			fi
		done < "${CHANNELS_FILE}"
	fi
else
	# Default = denseTrack channel
	echo "denseTrack" > "${WORK_DIR}/channels.list"
	log_INFO "Using channel denseTrack (default)."
fi

#
# APPEND JOBS
#

if [[ -e "$COMP_DESC_QUEUE_FILE" ]]; then
	log_WARN "Processing queue already exists, overwriting content."
fi
(
	for (( i=0; i < ${#VIDEOS[@]}; i++ )); do
		echo "${VIDEOS[${i}]}"
	done
) > "$COMP_DESC_QUEUE_FILE"
log_INFO "Registered videos for processing in \"$COMP_DESC_QUEUE_FILE\"."
log_INFO ""

log_OK "Event \"${EVENT_NAME}\" initialized."
echo "initialized" > "$STATUS_FILE"

