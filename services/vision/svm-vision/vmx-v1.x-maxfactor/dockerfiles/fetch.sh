#!/bin/bash

set -e -o pipefail

function die() {
  echo >&2 "$@"
  exit 1
}

test -n "$BUCKET_NAME" || die "Please set BUCKET_NAME environment variable"
test -n "$ACCESS_KEY" -a -n "$SECRET_KEY" || die "Please set ACCESS_KEY and SECRET_KEY environment variables"

echo Executing in $(pwd)
echo "Access Key: $ACCESS_KEY"
echo "Secret Key: $SECRET_KEY"
echo "Bucket Name: ${BUCKET_NAME}"
echo "Path on Bucket: s3://${BUCKET_NAME}/datasets/models/vmx-v1.-x-maxfactor/"
cd /vmx/models
s3cmd --config=/.s3cfg --access_key="$ACCESS_KEY" --secret_key="$SECRET_KEY" sync s3://${BUCKET_NAME}/datasets/models/vmx-v1.-x-maxfactor/ /vmx/models/
ls -l /vmx/models/
pwd
FILES=/vmx/models/*
cd /vmx/models/
for f in $FILES
do
  echo "Processing $f file..."
  # take action on each file. $f store current file name
  tar -xvf $f
done
cd /vmx
