#!/bin/bash

function youtube_download_videos {
	SIZE_APPEND=${SIZE_APPEND:='&filters=short'}
	LICENSE_APPEND=${LICENSE_APPEND:=',creativecommons'}

	# CHECK ELINKS IS AVAILABLE
	ELINKS=$( which elinks )
	if [ "${ELINKS}" == "" ]; then
		log_ERR "Package \"elinks\" is required to fetch the youtube result list."
		exit 1
	fi

	# CHECK YOUTUBE-DL IS AVAILABLE
	YOUTUBE_DL=$( which youtube-dl )
	if [ "${YOUTUBE_DL}" == "" ]; then
		log_ERR "Package \"youtube-dl\" is required to download the videos."
		exit 1
	fi

	NB_PAGES=`echo "${NB_VIDEOS} / 20.0" | bc -l`
	NB_PAGES=`echo "(${NB_PAGES}+1) / 1" | bc`
	
	log_INFO "Fetching list of ${NB_VIDEOS} youtube hits over ${NB_PAGES} page(s)..."
	#elinks -dump 1 -no-numbering "https://www.youtube.com/results?lclk=short&search_query=${EVENT_NAME}&filters=short" > elinks_out.tmp

	UUID=`uuidgen`
	TMPFILE="elinks_out_${UUID}.tmp"
	echo -n "" > ${TMPFILE}
	for i in $( seq 1 ${NB_PAGES} ); do
		elinks -source 1 -no-numbering "https://www.youtube.com/results?lclk=short&search_query=${QUERY}${LICENSE_APPEND}${SIZE_APPEND}&page=${i}" >> ${TMPFILE}
	done

	#VIDEOS=( $( cat elinks_out.tmp | sed -n -e '/References/,$p' | grep watch | awk -F "watch\\\\?v=" '{ print $2 }' | uniq ) )
	VIDEOS=( $( cat ${TMPFILE} | grep "watch?v=" | awk -F "watch\\\\?v=" '{ print $2 }' | awk -F "\"" '{ print $1 }' | uniq | head -n ${NB_VIDEOS} ) )
	rm ${TMPFILE}

	log_OK "Found ${#VIDEOS[@]} videos."


	for (( i = 0; i < ${#VIDEOS[@]}; i++ )); do
		log_IMPORTANT "Downloading video ${VIDEOS[${i}]} ($(( ${i} + 1 ))/${#VIDEOS[@]})."
		youtube-dl -f best -o "${OUTPUT_DIR}/${VIDEOS[${i}]}" "https://www.youtube.com/watch?v=${VIDEOS[${i}]}";
	done

}

