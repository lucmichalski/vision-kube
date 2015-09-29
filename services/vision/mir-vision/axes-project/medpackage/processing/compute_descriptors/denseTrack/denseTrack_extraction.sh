#!/bin/bash
# INRIA LEAR team, 2015
# Xavier Martin xavier.martin@inria.fr
USAGE_STRING="
# Usage: denseTrack_extraction.sh VIDNAME --scenecutfile FILE  --collection-dir DIR
#
# The path of the video is COLLECTION_DIR/videos if set, else it is assumed to start from the MED_BASEDIR/../videos/ directory.
#
# Requires all components to be compiled.
"

source "${MED_BASEDIR}usr/scripts/bash_utils.sh"

SCENECUTFILE=""
COLLECTION_DIR="./"
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
		exit 0
		;;
		--scenecutfile)
		SCENECUTFILE="$2"
		if [ ! -e "${SCENECUTFILE}" ]; then
			log_ERR "Scenecut file \"${SCENECUTFILE}\" does not exist."
			exit 1
		fi
		shift
		;;
		--collection-dir)
		COLLECTION_DIR="$2"
		if [ ! -e "${COLLECTION_DIR}" ]; then
			log_ERR "Collection directory \"${COLLECTION_DIR}\" does not exist."
			exit 1
		fi
		shift
		;;
		*)
		# Video name here
		VIDNAME="$key"
		;;
	esac
	shift
done


VIDEOS_DIR="${COLLECTION_DIR}/videos/"
VIDEOS_WORKDIR="${COLLECTION_DIR}/processing/videos_workdir/"


# Check that video exists
if [ ! -e "${VIDEOS_DIR}${VIDNAME}" ]; then
	log_ERR "Video \"${VIDEOS_DIR}${VIDNAME}\" does not exist"
	exit 1
fi

# Check that DenseTrackStab exists
which DenseTrackStab 1>/dev/null 2>/dev/null
if [ $? -ne 0 ]; then
	log_ERR "Couldn't find DenseTrackStab in PATH. Compile the dense trajectories executable and make sure it is available in your PATH environment variable."
	exit 1
fi

if [ "${SCENECUTFILE}" == "" ]; then
	python "${MED_BASEDIR}/compute_descriptors/denseTrack/densetrack_to_fisher_shot_errorprotect.py" --video "${VIDNAME}" --videodir "${VIDEOS_DIR}" --split train -k 256 --redo --slice 1 --save slice --featurepath "${VIDEOS_WORKDIR}${VIDNAME}"
else
	python "${MED_BASEDIR}/compute_descriptors/denseTrack/densetrack_to_fisher_shot_errorprotect.py" --video "${VIDNAME}" --videodir "${VIDEOS_DIR}" --split train -k 256 --redo --slice 1 --save slice --scenecut "${SCENECUTFILE}" --featurepath "${VIDEOS_WORKDIR}${VIDNAME}/shots"
fi

# CHECK RESULTS
#set -e
for i in `cat "${MED_BASEDIR}compute_descriptors/denseTrack/denseTrack_descriptors.list"`;
do
	if [[ "${SCENECUTFILE}" == "" && ! -e "${COLLECTION_DIR}/processing/videos_workdir/${VIDNAME}/${i}.fvecs" ]]; then
		log_ERR "Couldn't find ${COLLECTION_DIR}/processing/videos_workdir/${VIDNAME}/${i}.fvecs"
		exit 1
	elif [[ ! "${SCENECUTFILE}" == "" && ! -e "${COLLECTION_DIR}/processing/videos_workdir/${VIDNAME}/shots/${i}.fvecs" ]]; then
		log_ERR "Couldn't find ${COLLECTION_DIR}/processing/videos_workdir/${VIDNAME}/shots/${i}.fvecs"
		exit 1
	fi
done

exit 0

