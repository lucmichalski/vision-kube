#!/bin/bash
docker build -t lucmichalski/kong-admin:kube .
docker push lucmichalski/kong-admin:kube
compose2kube
alias json2yaml="docker run --rm --name json2yaml lucmichalski/json2yaml:kube json2yaml"
FILES="output/*"
for f in $FILES
do
  echo "Processing $f"
  mv $f $f.json
  docker run --rm --name json2yaml lucmichalski/json2yaml:kube json2yaml ./$f
done

# Changes labels manullay for integration in Vulcand
