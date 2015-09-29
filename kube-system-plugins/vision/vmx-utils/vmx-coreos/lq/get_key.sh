usage () {
  echo "Get a valid key from the license queue"
  echo "Usage:./get_key"
  exit 1
}

[ $# -gt 0 ] && {
  usage
}


REDIS_SERVER=$(etcdctl ls /services/redis | cut -d'/' -f4 | head -n 1)


docker run --rm redis redis-cli -h $REDIS_SERVER RPOP vmxlicenses
