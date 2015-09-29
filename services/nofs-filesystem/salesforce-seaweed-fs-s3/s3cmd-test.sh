#!/bin/sh
S3CMD=s3cmd
S3PORT=8081
S3HOST=s3.localhost:$S3PORT
BUCKET=proba
WEED=$(dirname $0)/weed
WEED_MASTER_PORT=9333

CFG="$(dirname $0)/s3cmd-weedS3.cfg"
if [ ! -e $CFG ]; then
    cat >$CFG <<EOF
[default]
access_key = AAA
bucket_location = US
#cloudfront_host = cloudfront.amazonaws.com
default_mime_type = binary/octet-stream
delete_removed = False
dry_run = False
enable_multipart = True
encoding = UTF-8
encrypt = False
follow_symlinks = False
force = False
get_continue = False
gpg_command = /usr/bin/gpg
gpg_decrypt = %(gpg_command)s -d --verbose --no-use-agent --batch --yes --passphrase-fd %(passphrase_fd)s -o %(output_file)s %(input_file)s
gpg_encrypt = %(gpg_command)s -c --verbose --no-use-agent --batch --yes --passphrase-fd %(passphrase_fd)s -o %(output_file)s %(input_file)s
gpg_passphrase =
guess_mime_type = True
host_base = $S3HOST
host_bucket = %(bucket)s.$S3HOST
human_readable_sizes = False
invalidate_on_cf = False
list_md5 = False
log_target_prefix =
mime_type =
multipart_chunk_size_mb = 15
preserve_attrs = True
progress_meter = True
proxy_host =
proxy_port = 0
recursive = False
recv_chunk = 4096
reduced_redundancy = False
secret_key =
send_chunk = 4096
#simpledb_host = sdb.amazonaws.com
skip_existing = False
socket_timeout = 300
urlencoding_mode = normal
use_https = False
verbosity = DEBUG
#website_endpoint = http://%(bucket)s.s3-website-%(location)s.amazonaws.com/
website_error =
website_index = index.html
EOF
fi
S3CMD="$S3CMD --config=$CFG"
S3WPID=
WMPID=
WVPID=

ping -Anq -W1 -c1 $S3HOST
ping -Anq -W1 -c1 ${BUCKET}.${S3HOST}

if nc -z localhost $S3PORT; then
    echo "port $S3PORT is open"
    #S3WPID=$(pgrep s3impl)
else
    mkdir -p /tmp/weedS3/AAA
    rm -f /tmp/weedS3/*/.a?????*
    $(dirname $0)/s3impl/s3impl -weed=http://localhost:$WEED_MASTER_PORT -db=/tmp/weedS3 -http=$S3HOST &
    S3WPID=$!
fi

atexit () {
    if [ -n "$WVPID" ]; then
        echo "killing weed volume $WVPID"
        kill $WVPID
    fi
    if [ -n "$WMPID" ]; then
        echo "killing weed master $WMPID"
        kill $WMPID
    fi
    if [ -n "$S3WPID" ]; then
        echo "killing s3impl $S3WPID"
        sudo kill -9 $S3WPID
    fi
}
trap atexit ERR

set -e
if nc -z localhost $WEED_MASTER_PORT; then
    echo "weed master is there"
    #WMPID=$(pgrep -f 'weed master')
else
    mkdir -p /tmp/weed
    $WEED master -mdir=/tmp/weed &
    WMPID=$!
fi
if wget -q -O- http://localhost:9333/dir/status | grep -q PublicUrl; then
    echo "weed volume is there"
    #WVPID=$(pgrep -f 'weed volume'
else
    mkdir -p /tmp/weed
    $WEED volume -dir=/tmp/weed &
    WVPID=$!
fi



if $S3CMD ls | grep -q "s3://$BUCKET"; then
    $S3CMD ls
else
    $S3CMD mb s3://$BUCKET
fi
$S3CMD put $(dirname $0)/LICENSE s3://$BUCKET/a/b/c
$S3CMD ls s3://$BUCKET | grep -q LICENSE
$S3CMD get s3://$BUCKET/a/b/c --force
$S3CMD del s3://$BUCKET/a/b/c

find /usr/share/doc -type f -readable -size +1b 2>/dev/null | head -n 10 \
    | while read fn; do
    $S3CMD put $fn s3://${BUCKET}$fn
done

atexit

#  sudo ./s3impl/s3impl -weed=http://localhost:9333 -db=/tmp/weedS3 -http=s3.localhost:80

