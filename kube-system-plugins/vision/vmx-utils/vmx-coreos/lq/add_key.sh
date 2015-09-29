usage () {
  echo "Add a valid key to the license queue"
  echo "Usage:./add_key LICENSEKEY_UUID"
  exit 1
}

[ $# -lt 1 ] && {
  usage
}

KEY=$1

REDIS_SERVER=$(etcdctl ls /services/redis | cut -d'/' -f4 | head -n 1)


docker run --rm redis redis-cli -h $REDIS_SERVER RPUSH vmxlicenses $KEY
