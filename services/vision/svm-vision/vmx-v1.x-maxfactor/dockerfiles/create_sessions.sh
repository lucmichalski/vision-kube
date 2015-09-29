#!/bin/bash
#

#
# Create sub-workers and generate the mapping for reverse proxy
#
#The location of the VMX server
LICENSE_KEY="c334077f-c951-482d-8608-656fa2c7c2d0"
VMX_HOST=$(hostname -f)
VMX=http://$VMX_HOST:3000
WORKERS=5
COUNTER=0
echo $VMX

curl -X POST -d '{"email":"luc.michalski@blippar.com"}' $VMX/activate/c334077f-c951-482d-8608-656fa2c7c2d0

create_workers () {

echo ""
echo "======= Welcome to the worker factory ======"
echo ""

echo $1
NAME=$(echo $1|tr -d '\n')
RESULT=""

echo "====== Please welcome $NAME"

#Get the UUID for the desired model
UUID=`curl -s $VMX/model | jq -r '.data[] | select(.name=="'$NAME'") .uuid'`
echo "====== His Unique Identifier is $UUID"

if [ -z $UUID ]; then
    echo ""
    echo "Cannot find model named" $NAME
    echo "Available models:" `curl -s $VMX/model | jq -r '.data[] .name'`
    echo ""
else

    echo "====== List of sessions already there: $SESSIONID"
    # Start a new session if we didn't find one
    echo "curl -s -X POST -d '{"uuids":["'$UUID'"]}' $VMX/session | jq -r .data.id"
    SESSIONID=`curl -s -X POST -d "{\"uuids\":[\""$UUID"\"]}" $VMX/session | jq -r .data.id`
    echo "Generated $SESSIONID"
    echo -e "$NAME/$SESSIONID" >> /vmx/sessions/workers.log
fi
}

#Get the list of available models
MODELS=`curl -s $VMX/model | jq -r '.data[] .name'`
IFS=$'\n'
for MODEL in $MODELS; do
   while [  $COUNTER -lt $WORKERS ]; do
     create_workers $MODEL
     echo "Please wlecome the worker nb: $COUNTER/$MODEL"
     let COUNTER=COUNTER+1
   done
   COUNTER=0
done
echo ok
