#!/bin/bash

echo "====== START ======"
echo "Preparing new SSL certificates..."
openssl req -x509  -batch -nodes -newkey rsa:2048 \
-keyout logstash/conf/logstash-forwarder.key \
-out logstash/conf/logstash-forwarder.crt \
-subj /CN=logstash
cp logstash/conf/logstash-forwarder.crt forwarder/ssl/logstash-forwarder.crt
cp logstash/conf/logstash-forwarder.key forwarder/ssl/logstash-forwarder.key

echo "Building and pushing latest containers for the vision cyclops..."
for d in * ; do
    if [[ -d $d ]]; then
      echo "I am in:"
      pwd
      echo "$d"
      cd $d
      echo "WORKING DIR:"
      pwd
      docker build -t lucmichalski/$d:kube-vs-metrics .
      docker push lucmichalski/$d:kube-vs-metrics
      cd ..
    fi
done

pwd
echo "Preparing $f services and creplication controllers..."
compose2kube

FILES=.output/*
for f in $FILES
do
  echo "Processing $f file..."
  # take action on each file. $f store current file name
  cat $f
done
