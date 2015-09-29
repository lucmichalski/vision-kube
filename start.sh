#!/bin/bash

#
# Luc Michalski - 2015
# Zeus & Kube Project
#

# Load PUBLIC and PRIVATE IPs from the instance
echo "- Load network IPs variables..."
source /etc/environment

echo "Private IP: $COREOS_PRIVATE_IPV4"
echo "Public IP: $COREOS_PUBLIC_IPV4"

echo "Mount the Amazon S3 buket with Models and Dataset"
sudo mkdir /mnt/s3-blippar
docker run --privileged=true -e AWS_BUCKET=bucket-test-markers -e AWSACCESSKEYID=z6oyko0TrdRX+FTLjQIDYOarL9WgbJEsWXPNVBYd -e AWSSECRETACCESSKEY=AKIAJX5PMOG2GMVC3RUA lucmichalski/s3fs ls /mnt/s3-blippar

docker info

# Start Vulcand
echo "- Run the Main Reverse Proxy in the background"
docker stop main.proxy.vulcan
docker rm main.proxy.vulcan
docker run -d --name main.proxy.vulcan -p 0.0.0.0:80:80 -p 8182:8182 -p 8181:8181 mailgun/vulcand:latest vulcand -apiInterface=0.0.0.0 -interface=0.0.0.0 -etcd=http://$COREOS_PRIVATE_IPV4:2379 -etcdKey=vulcand -port=80 -apiPort=8182
docker logs main.proxy.vulcan

echo "- Output logs from the proxy container"
#docker logs 

# Start Romulus
docker stop proxy.discovery.romulus
docker rm -f proxy.discovery.romulus
docker run -d --name proxy.discovery.romulus quay.io/timeline_labs/romulusd -d --debug-etcd --etcd=http://$COREOS_PRIVATE_IPV4:2379 --kube=http://${COREOS_PRIVATE_IPV4}:8080 --kube-api="v1" --svc-selector=type=external
docker logs proxy.discovery.romulus

# Start ETCD-Browser
docker stop etcd.browser
docker rm -f etcd.browser
docker run -d --name etcd.browser -p 0.0.0.0:8000:8000 --env ETCD_HOST=172.31.0.246 --env ETCD_PORT=2379 -t -i dreampuf/etcd-browser
docker logs etcd.browser

#kubectl create -f ./kube-system-plugins/vision/vmx-v1.x-backend-ui/
#kubectl create -f ./kube-system-plugins/vision/vmx-v2.x-backend-ui/
#kubectl create -f ./kube-system-plugins/vision/vmx-metrics/
kubectl replace -f ./services/vision/svm-vision/vmx-v2.x-maxfactor/
kubectl replace -f ./services/vision/svm-vision/vmx-v1.x-maxfactor/
kubectl replace -f ./services/vision/opencv-vision/d-colors/
kubectl replace -f ./services/vision/proprietary-vision/ltuengin76/
kubectl replace -f ./services/vision/opencv-vision/libccv/
#kubectl replace -f ./services/vision/opencv-vision/find-object/
#kubectl replace -f ./services/metrics/vision-cyclops/

#echo "Mount the S3 buckets in order to get access to datasets or pictures"
#s3fs mybucket /path/to/mountpoint -o passwd_file=/path/to/passwd


 curl -X POST -H "Content-Type: application/json" http://172.31.0.246:8182/v2/frontends/api.libccv-rest.default/middlewares\
     -d '{"Middleware": {
         "Id":"api.libccv-rest.default",
         "Priority":1,
         "Type":"rewrite",
         "Middleware":{
            "Regexp":"/api/vision/libccv/(.*)",
            "Replacement":"/$1",
            "RewriteBody":false,
            "Redirect":false}}}'

 curl -X POST -H "Content-Type: application/json" http://172.31.0.246:8182/v2/frontends/api.vmx2-maxfactor-rest.default/middlewares\
     -d '{"Middleware": {
         "Id":"api.vmx2-maxfactor-rest.default",
         "Priority":1,
         "Type":"rewrite",
         "Middleware":{
            "Regexp":"/api/vision/vmx2/maxfactor/(.*)",
            "Replacement":"/$1",
            "RewriteBody":false,
            "Redirect":false}}}'

 curl -X POST -H "Content-Type: application/json" http://172.31.0.246:8182/v2/frontends/api.vmx1-maxfactor-rest.default/middlewares\
     -d '{"Middleware": {
         "Id":"api.vmx1-maxfactor-rest.default",
         "Priority":1,
         "Type":"rewrite",
         "Middleware":{
            "Regexp":"/api/vision/vmx1/maxfactor/(.*)",
            "Replacement":"/$1",
            "RewriteBody":false,
            "Redirect":false}}}'           

 curl -X POST -H "Content-Type: application/json" http://172.31.0.246:8182/v2/frontends/api.dcolors-rest.default/middlewares\
     -d '{"Middleware": {
         "Id":"api.dcolors-rest.default",
         "Priority":1,
         "Type":"rewrite",
         "Middleware":{
            "Regexp":"/api/vision/d-colors/(.*)",
            "Replacement":"/$1",
            "RewriteBody":false,
            "Redirect":false}}}'

# Check active containers
docker ps
