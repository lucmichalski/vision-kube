# INRIA LEAR team, 2015
# Xavier Martin xavier.martin@inria.fr
USAGE_STRING="
# This script queries youtube for a given event name,
# downloads N first results and learns a classifier from them.
#
# Options:
#
#	--event-name EVENT_NAME
#
# 	--nb-positive NB
#		Number of positive videos to fetch from youtube.
#
#	--background-vids LIST_FILENAME
#		List of videos to use as background.
#
#	-c|--channel CHANNEL
#		Add a descriptor channel.
#
#		
#	--parallel NB_PARALLEL
#		Instances of descriptor extraction (default: 4).
#
#	--overwrite
#		Existing event with the same name will be overwritten.
#
#   --use-any-size
#       By default, the search is limited to small videos (less than 4 minutes).
#
#   --use-any-license
#       Default: use only creative commons.
#		
"

set -u
source processing/usr/scripts/bash_utils.sh

log_TITLE "Automated class creator"

##
## PARSE ARGUMENTS
##
re='^[0-9]+$'

OVERWRITE=""
SHOT_SEPARATION=NO
CHANNELS=()
BG_VIDEOS=()

NB_POSITIVE=""

NB_INSTANCES=$(( `cat /proc/cpuinfo | grep processor | wc -l` ))
if ! [[ "$NB_INSTANCES" =~ $re ]] ; then
	log_WARN "Couldn't parse '/proc/cpuinfo', launching 4 threads."
	NB_INSTANCES=4
fi

# hidden option
EVENT_NAME=""

SIZE_APPEND="&filters=short"
LICENSE_APPEND=",creativecommons"

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
		--nb-positive)
		NB_POSITIVE="$2"
		shift
		if ! [[ $NB_POSITIVE =~ $re ]] ; then
			echo "$USAGE_STRING"
			log_ERR "--nb-positive: Expecting an integer"
			exit 1
		fi
		;;
		--background-vids)
		while read -r video; do
			BG_VIDEOS+=( "$video" )
		done < "$2"
		shift
		;;
		-c|--channel)
		log_INFO "Using channel $2"
		CHANNELS+=( "$2" )
		shift
		;;
		--parallel)
		NB_INSTANCES="$2"
		if ! [[ $NB_INSTANCES =~ $re ]] ; then
			echo "$USAGE_STRING"
			log_ERR "--parallel: Expecting an integer"
			exit 1
		fi
		shift
		;;
		--overwrite)
		OVERWRITE="--overwrite"
		;;
		--use-any-size)
		SIZE_APPEND=""
		;;
		--use-any-license)
		LICENSE_APPEND=""
		;;
		--event-name)
		# hidden option
		EVENT_NAME="$2"
		shift
		;;
		*)
		# Event name here
		#EVENT_NAME="$key"
		;;
	esac
	shift
done

if [ "${EVENT_NAME}" == "" ]; then
	echo "$USAGE_STRING"
	log_ERR "Need to specify an event name"
	exit 1
fi

# NEED AT LEAST ONE CHANNEL
if [[ ${#CHANNELS[@]} -eq 0 ]]; then
	echo "$USAGE_STRING"
	log_ERR "Need at least one channel, see option -c|--channel."
	exit 1
fi

if [[ ${#BG_VIDEOS[@]} -eq 0 ]]; then
	echo "$USAGE_STRING"
	log_ERR "Need at least one background video, see option --background-vids."
	exit 1
fi

if [ "$NB_POSITIVE" == "" ]; then
	echo "$USAGE_STRING"
	log_ERR "Need to specify amount of positive training videos, see option --nb-positive."
	exit 1
elif [ $NB_POSITIVE -lt 4 ]; then
	echo "$USAGE_STRING"
	log_ERR "The amount of positive training videos needs to be an integer above or equal to 4, see --nb-positive."
	exit 1
fi

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


source processing/usr/scripts/youtube.sh

QUERY="${EVENT_NAME}"
NB_VIDEOS=${NB_POSITIVE}
OUTPUT_DIR="${COLLECTION_DIR:='.'}/videos/_youtube"

youtube_download_videos

log_OK "Videos downloaded. Initializing event..."

set -e

EVENT_DIR="${COLLECTION_DIR:='.'}/events/${EVENT_NAME}"
mkdir -p "${EVENT_DIR}"
( for (( i = 0; i < ${#VIDEOS[@]}; i++ )); do
	echo "_youtube/${VIDEOS[${i}]}"
done ) > "${EVENT_DIR}/positive.txt"

( for (( i = 0; i < ${#BG_VIDEOS[@]}; i++ )); do
	echo "${BG_VIDEOS[${i}]}"
done ) > "${EVENT_DIR}/background.txt"

./event_init.sh "${EVENT_NAME}" ${OVERWRITE} --channels <( for (( i = 0; i < ${#CHANNELS[@]}; i++ )); do echo ${CHANNELS[${i}]}; done ) --check-missing

./event_run_descriptor_extraction.sh "${EVENT_NAME}" --parallel ${NB_INSTANCES}

./event_run_training.sh "${EVENT_NAME}"

