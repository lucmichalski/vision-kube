#!/bin/bash


function install_yum() {
	set -e
	# Dependencies for parallel scoring (binary "lockfile")
	sudo yum install -y procmail

	# Dependencies for youtube automatic scraper
	sudo yum install -y youtube-dl elinks

}

function install_aptget() {
	set -e
	apt-get install procmail
	apt-get install youtube-dl elinks
}

which yum 2>/dev/null
WHICH_YUM=$?

which apt-get 2>/dev/null
WHICH_APTGET=$?

if [ $WHICH_YUM -eq 0 ]; then
	echo "Detected yum, running install for Fedora (and other RedHat derivatives).";
	install_yum
elif [ $WHICH_APTGET -eq 0 ]; then
	echo "Detected apt-get, running install for Debian and derivatives."
	install_aptget
else
	echo "Your system doesn't have yum or apt-get."
	echo "You will have to install the dependencies manually."
fi

echo ""
echo "OK."
echo "Left to install:"
echo "  - yael"
echo ""
echo "and:"
echo "  1) FFMpeg (devel)       http://ffmpeg.org/download.html"
echo "  2) OpenCV (devel)       http://opencv.org/"
echo "  3) Dense trajectories   http://lear.inrialpes.fr/people/wang/download/improved_trajectory_release.tar.gz"
echo ""
echo "Installation order is important on these."
