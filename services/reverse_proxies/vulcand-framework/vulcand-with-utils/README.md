# VMX Reverse_proxy with Vulcand/Etcd

Install [Docker Compose](http://docs.docker.com/compose/) on your system.

* Python/pip: `sudo pip install -U docker-compose`
* Other: ``curl -L https://github.com/docker/compose/releases/download/1.1.0/docker-compose-`uname -s`-`uname -m` > /usr/local/bin/docker-compose; chmod +x /usr/local/bin/docker-compose``

## Setup

```

git clone https://github.com/blippar/vmx-proxy
cd vmx-proxy
docker-compose build (wait for all local images to be built)

```

## Launch it
```

docker-compose up

```

Use with `-d` once you like what you're seeing.

