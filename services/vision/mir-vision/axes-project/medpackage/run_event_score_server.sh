#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr
USAGE_STRING="
# Usage: run_event_score_server.sh --port INT --scores-dir DIR
#
# Runs the HTTP score server based on the scores present in \"results/scores/\".
# 
# Example requests with \"--port 12080\":
# curl http://localhost:12080/api_get_classifier_output?nb_results=10\&classifier_name=rock_climbing
"
# PARSE ARGUMENTS
re='^[0-9]+$'

SCORES_DIR=""
PORT=""

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
		--port)
		PORT="$2"
		shift
		if ! [[ $PORT =~ $re ]] ; then
			echo "$USAGE_STRING"
			log_ERR "--port: Expecting an integer"
			exit 1
		fi
		;;
		--scores-dir)
		SCORES_DIR="$2"
		shift
		;;
		*)
		;;
	esac
	shift
done

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

source processing/usr/scripts/bash_utils.sh

if [[ $PORT == "" || $SCORES_DIR == "" ]]; then
	echo "$USAGE_STRING"
	log_ERR "Expecting a port and a scores directory."
	exit 1
fi

log_TITLE "HTTP score server"

python processing/scores_server/scores_http_server.py --scoredir ${SCORES_DIR} --port ${PORT}
