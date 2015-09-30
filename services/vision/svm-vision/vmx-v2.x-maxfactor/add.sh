#!/bin/bash

cd dockerfiles
echo "=== Build the container"
docker build -t lucmichalski/vmx-server-v2:latest .
echo ""

echo "=== Push the container"
docker push lucmichalski/vmx-server-v2
echo ""

echo "=== Create the controller"
kubectl create -f vmx-v2-controller.yaml
echo ""

echo "=== Create the service"
kubectl create -f vmx-v2-service.yaml
echo ""

echo "==== Get Nodes available"
kubectl get nodes
echo ""

echo "==== Get Pods available"
kubectl get pods
echo ""

echo "==== Get Services available"
kubectl get services
echo ""

echo "==== Get ReplicationsServices available"
kubectl get rc
echo ""

echo "==== Get Pods for the Kube System "
kubectl get pods --namespace=kube-system 
echo ""

echo "==== Get the replica Controller for the Kube System "
kubectl get rc --namespace=default
echo ""

echo "==== Get list of Services for the Kube System "
kubectl get services --namespace=default
echo ""

echo "==== Routes available for the Kube-Apiservice "
curl http://127.0.0.1:8080 | jq .

echo "### Get external links to the global dashboard"
kubectl cluster-info
