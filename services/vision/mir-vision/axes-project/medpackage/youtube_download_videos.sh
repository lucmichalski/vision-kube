# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr
USAGE_STRING="
# This script queries youtube for a given event name,
# downloads N first results and learns a classifier from them.
#
# Options:
#
#   --query STRING
#       Youtube query.
#
#   --nb-videos NB
#		Number of positive videos to fetch from youtube.
#
#   --output-dir DIR
#       Where to save videos (default: videos/_youtube).
#
#   --use-any-size
#       By default, the search is limited to small videos (less than 4 minutes).
#
#   --use-any-license
#       Default: use only creative commons.
#		
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

log_TITLE "Automated class creator"

##
## PARSE ARGUMENTS
##
re='^[0-9]+$'

QUERY=""
NB_VIDEOS=""
OUTPUT_DIR="videos/_youtube/"

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
		--query)
		QUERY="$2"
		shift
		;;
		--nb-videos)
		NB_VIDEOS="$2"
		shift
		if ! [[ $NB_VIDEOS =~ $re ]] ; then
			echo "$USAGE_STRING"
			log_ERR "--nb-videos: Expecting an integer"
			exit 1
		fi
		;;
		--output-dir)
		OUTPUT_DIR="$2"
		shift
		;;
		--use-any-size)
		SIZE_APPEND=""
		;;
		--use-any-license)
		LICENSE_APPEND=""
		;;
		*)
		;;
	esac
	shift
done

if [[ $QUERY == "" ]]; then echo "$USAGE_STRING"; log_ERR "Need --query"; exit 1; fi
if [[ $NB_VIDEOS == "" ]]; then echo "$USAGE_STRING"; log_ERR "Need --nb-videos"; exit 1; fi
if [[ $OUTPUT_DIR == "" ]]; then echo "$USAGE_STRING"; log_ERR "Need --output-dir"; exit 1; fi

source processing/usr/scripts/youtube.sh

youtube_download_videos

echo "Videos: ${VIDEOS[@]}"

