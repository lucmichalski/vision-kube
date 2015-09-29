#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin>at<inria.fr

USAGE_STRING="
# Usage: video_run_descriptor_extraction.sh
#
# --video-list FILENAME
#
# --channel CHANNEL
#     -> add a channel to extract.
#
# --shot-separation YES|NO
#     -> default NO
#     -> if YES, looks for a .scenecut file at the following locations:
#           ./shots/VIDEO_NAME.scenecut
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
source processing/config_MED.sh

##
## PARSE ARGUMENTS
##
re='^[0-9]+$'

FORCE_START=NO
OVERWRITE_ALL=NO
CLEAN_STATE_RUNNING=NO
NB_INSTANCES=1
SHOT_SEPARATION=NO
VIDEOS=()
CHANNELS=()

# hidden option
EVENT_NAME=""

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
		--video-list)
		echo "Reading vids from $2"
		while read -r video; do
			VIDEOS+=( "$video" )
		done < "$2"
		shift
		;;
		-c|--channel)
		log_INFO "Using channel $2"
		CHANNELS+=( "$2" )
		shift
		;;
		--shot-separation)
		if [[ "$2" == "NO" || "$2" == "YES" ]]; then
			SHOT_SEPARATION=$2
		else
			echo "$USAGE_STRING"
			log_ERR "Invalid value for --shot-separation (expecting YES or NO)."
		fi
		shift
		;;
		--parallel)
		NB_INSTANCES="$2"
		shift
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
		--event-name)
		# hidden option
		EVENT_NAME="$2"
		shift
		;;
		--collection-dir)
		COLLECTION_DIR="$2"
		shift
		;;
		*)
		;;
	esac
	shift
done

# NEED AT LEAST ONE CHANNEL

if [[ ${#VIDEOS[@]} -eq 0 ]]; then
	echo "$USAGE_STRING"
	log_ERR "Need at least one video, see option --video-list."
	exit 1
elif [[ ${#CHANNELS[@]} -eq 0 ]]; then
	echo "$USAGE_STRING"
	log_ERR "Need at least one channel, see option -c|--channel."
	exit 1
fi

VIDS_WORK_DIR="${COLLECTION_DIR}/processing/videos_workdir/"


echo      "--------------------"
log_TITLE "Descriptor extraction"

##########
# PARALLEL 
##########

function killChildProcesses {
	echo "Signal received - killing spawned processes."
	for i in ${PIDS[@]}; do
		#CPIDS=$(pgrep -P ${i})
		#echo "Killing $CPIDS"
		#kill -9 ${i} ${CPIDS};
		#killtree ${i} 9
		kill -9 "${i}"
	done
	exit 1
}

if [ $NB_INSTANCES -gt 1 ]; then
	TMP_VIDEOLIST="${COLLECTION_DIR}/processing/tmp_videolist_`uuidgen`"
	# This instance acts as the master.
	( for (( i = 0; i < ${#VIDEOS[@]}; i++ )); do
		echo ${VIDEOS[${i}]}
	done ) > "${TMP_VIDEOLIST}"
	
	CHANNELS_STR=""
	for i in ${CHANNELS[@]}; do 
		CHANNELS_STR="${CHANNELS_STR} -c ${i}"
	done
	
	PIDS=()
	# Spawn instances
	for (( i = 0; i < ${NB_INSTANCES}; i++ )); do
		#xterm -e "./video_run_descriptor_extraction.sh ${CHANNELS_STR} --video-list processing/tmp_videolist" &
		bash -c "./video_run_descriptor_extraction.sh ${CHANNELS_STR} --video-list \"${TMP_VIDEOLIST}\"" &
		PIDS+=($!)
		log_OK "Spawned PID $!"
	done
	
	RETVAL=0
	# Wait for instances to finish
	trap killChildProcesses TERM HUP KILL INT
	for pid in ${PIDS[@]}; do
		log_INFO "Waiting for $pid.."
		wait $pid
		#RETVAL=$(( $RETVAL + $? ))
	done
	
	trap - TERM HUP KILL INT
	
	# Return value == add all instances' return values
	#if [ $RETVAL -eq 0 ]; then
	#	log_OK "Missing $RETVAL descriptors."
	#else
	#	log_ERR "Missing $RETVAL descriptors."
	#fi
	
	rm -f "${TMP_VIDEOLIST}"
	exit 0
fi


########################
# Function declaration #
########################

function cleanup {
	echo "Emergency exit on ${CHANNEL} ${video}, cleaning up lock and status."
	rm -f "${LOCKFILE}"
	rm -f "${VID_CHANNEL_STATUS_FILE}"
	exit 1
}

function run_job_sequential {
	# In case job is interrupted
	trap cleanup TERM HUP KILL INT
	
	echo "running" > "$VID_CHANNEL_STATUS_FILE"
	
	log_INFO "Launching job for \"$video\"."
	
	if [ "${SHOT_SEPARATION}" == "NO" ]; then
		./processing/compute_descriptors/${CHANNEL}/${CHANNEL}_extraction.sh "${video}" --collection-dir "${COLLECTION_DIR}"
	else
		./processing/compute_descriptors/${CHANNEL}/${CHANNEL}_extraction.sh "${video}" --scenecutfile "${COLLECTION_DIR}/shots/${video}.scenecut" --collection-dir "${COLLECTION_DIR}"
	fi
	
	if [[ $? == 0 ]]; then
	## "done" message must be written when you've checked the results are those expected
		echo "done" > "$VID_CHANNEL_STATUS_FILE"
		log_OK "Job successful for \"$video\"."
	else
		rm -f "$VID_CHANNEL_STATUS_FILE"
		log_ERR "Job failed for \"${video}\"."
	fi
	
	rm -f "${LOCKFILE}"
	
	trap - INT HUP TERM HUP
	
	return 0
}

#### RUN JOBS

log_INFO "${TXT_BOLD}Checking all videos sequentially${TXT_RESET}"
log_TODO "Implement remote job handler, OAR handler, etc."

LOCK_STRING="none"
if [ ! "${EVENT_NAME}" == "" ]; then
	LOCK_STRING="${EVENT_NAME}"
fi

# FOR EVERY CHANNEL
for CHANNEL in ${CHANNELS[@]}; do
	log_INFO ""
	log_IMPORTANT "Computing descriptors for ${CHANNEL}."

	NB_DESCRIPTORS_MISSING=${#VIDEOS[@]}
	
	for (( i = 0; i < ${#VIDEOS[@]}; i++ )); do
		video="${VIDEOS[${i}]}"
		
		if [[ $SHOT_SEPARATION == YES ]]; then
			mkdir -p "${VIDS_WORK_DIR}${video}/shots"
			VID_CHANNEL_STATUS_FILE="${VIDS_WORK_DIR}${video}/shots/${CHANNEL}_extraction_status"
		else
			mkdir -p "${VIDS_WORK_DIR}${video}"
			VID_CHANNEL_STATUS_FILE="${VIDS_WORK_DIR}${video}/${CHANNEL}_extraction_status"
		fi
		LOCKFILE="${VID_CHANNEL_STATUS_FILE}.lock"
		VID_CHANNEL_STATUS=`cat "${VID_CHANNEL_STATUS_FILE}" 2> /dev/null`
	
		if [[ ${CLEAN_STATE_RUNNING} == "YES" ]]; then
			if [[ ${VID_CHANNEL_STATUS} == "running" ]]; then
				rm -f "${LOCKFILE}"
				rm -f "${VID_CHANNEL_STATUS_FILE}"
				log_OK "Cleared 'running' state for \"${video}\""
			fi
			continue
		fi
	
		# Quick checks to avoid locking unnecessarily
	
		if [[ "${VID_CHANNEL_STATUS}" == "done" && ${OVERWRITE_ALL} == NO ]]; then
			log_OK "${CHANNEL} \"${video}\" already marked as done."
			NB_DESCRIPTORS_MISSING=$(( ${NB_DESCRIPTORS_MISSING} - 1 ))
			continue
		elif [[ "${VID_CHANNEL_STATUS}" == "running" && ${FORCE_START} == NO ]]; then
			log_WARN "${CHANNEL} job already registered for \"${video}\". Owner: \"`cat "$LOCKFILE"`\". See option \"--force-start\"."
			NB_DESCRIPTORS_MISSING=$(( ${NB_DESCRIPTORS_MISSING} - 1 ))
			continue
		fi
	
		# START BY REGULAR MEANS, LOCKING
		if ( set -o noclobber; echo "${LOCK_STRING}" > "$LOCKFILE") 2> /dev/null ; then
			# Lock acquired, re-check
			# The status can't be marked as "running" if lock was acquired
			VID_CHANNEL_STATUS=`cat "${VID_CHANNEL_STATUS_FILE}" 2> /dev/null`
			if [[ "${VID_CHANNEL_STATUS}" == "done" && ${OVERWRITE_ALL} == NO ]]; then
				log_OK "${CHANNEL} \"${video}\" already marked as done."
				NB_DESCRIPTORS_MISSING=$(( ${NB_DESCRIPTORS_MISSING} - 1 ))
				rm -f "${LOCKFILE}"
				continue
			fi
		
			# Launch job
			run_job_sequential
			# {Lock released}
		
			if [[ $? == 0 ]]; then
				NB_DESCRIPTORS_MISSING=$(( ${NB_DESCRIPTORS_MISSING} - 1 ))
			fi
		else
			if [[ ${FORCE_START} == NO ]]; then
				log_WARN "${CHANNEL} job already registered for \"${video}\". Owner: \"`cat "$LOCKFILE"`\". See option \"--force-start\"."
				NB_DESCRIPTORS_MISSING=$(( ${NB_DESCRIPTORS_MISSING} - 1 ))
			else
				# Force start
				log_WARN "Force start on ${CHANNEL} \"${video}\". Original owner: \"`cat "$LOCKFILE"`\""
				run_job_sequential
				if [[ $? == 0 ]]; then
					NB_DESCRIPTORS_MISSING=$(( ${NB_DESCRIPTORS_MISSING} - 1 ))
				fi
			fi
		fi
	done
	log_INFO "Missing ${NB_DESCRIPTORS_MISSING} descriptors."
	
done
	
exit ${NB_DESCRIPTORS_MISSING}




