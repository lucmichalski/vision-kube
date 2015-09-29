cd shots

for i in `ls`; do
	echo $i;
	fullfilename=$i
	filename=$(basename "$fullfilename")
	VIDEXT="${filename##*.}"
	VIDEXT=.${VIDEXT}
	VIDNAME="${filename%.*.*}"
	VIDNAME=$VIDNAME.scenecut

	echo $VIDNAME

	mv $i $VIDNAME
done

