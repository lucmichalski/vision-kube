#!/bin/sh

DOCKER=`which docker`
GIT=`which git`
CURL=`which curl`
LFSOURCE=lfsource

echo "Building logstash-forwarder.."
rm -rf $LFSOURCE
$GIT clone git://github.com/elasticsearch/logstash-forwarder.git $LFSOURCE
cd $LFSOURCE
$DOCKER run --rm \
           --volume $PWD:/src \
           --workdir /src \
           quay.io/pires/alpine-linux-build \
           go build -o logstash-forwarder

echo "Copying binaries"
cd ..
cp $LFSOURCE/logstash-forwarder binaries/

echo "Cleaning sources.."
rm -rf $LFSOURCE

echo "Done"
