#!/bin/bash



cd kubernetes
docker-compose up -d

cd ../scripts
./wait-for-kubernetes.sh
./activate-dns.sh $1
./activate-kube-ui.sh
