#/bin/bash

set -e

echo "creating es-master pod"
# ES cluster base
kubectl create -f ./kube-system-plugin/elk-components/kubernetes-elasticsearch-cluster/service-account.yaml
kubectl create -f ./kube-system-plugin/elk-components/kubernetes-elasticsearch-cluster/es-discovery-svc.yaml
kubectl create -f ./kube-system-plugin/elk-components/kubernetes-elasticsearch-cluster/es-svc.yaml
kubectl create -f ./kube-system-plugin/elk-components/kubernetes-elasticsearch-cluster/es-master-rc.yaml

provisioned=`kubectl get pods | grep es-master | awk '{print $2}' | awk -F '/' '{print $1}'`

while [ "$provisioned" -eq "0" ]
do
  echo "pod still not provisionned"
  sleep 30
done
echo "pod es-mater provisionned"

# Then client
echo "creating es-client pod"
kubectl create -f ./kube-system-plugin/elk-components/kubernetes-elasticsearch-cluster/es-client-rc.yaml

provisioned=`kubectl get pods | grep es-client | awk '{print $2}' | awk -F '/' '{print $1}'`

while [ "$provisioned" -eq "0" ]
do
  echo "pod still not provisionned"
  sleep 30
done
echo "pod es-client provisionned"

# Then data
echo "creating es-data pod"
kubectl create -f ./kube-system-plugin/elk-components/kubernetes-elasticsearch-cluster/es-data-rc.yaml

provisioned=`kubectl get pods | grep es-data | awk '{print $2}' | awk -F '/' '{print $1}'`

while [ "$provisioned" -eq "0" ]
do
  echo "pod still not provisionned"
  sleep 30
done
echo "pod es-data provisionned"

echo "creating logstash & kibana pods"
kubectl create -f ./kube-system-plugins/elk-cluster/
echo "done"
