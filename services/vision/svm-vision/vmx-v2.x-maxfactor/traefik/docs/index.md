![Træfɪk](http://traefik.github.io/traefik.logo.svg "Træfɪk")
___


# <a id="top"></a> Documentation

* [Basics](#basics)
* [Global configuration](#global)
* [File backend](#file)
* [API backend](#api)
* [Docker backend](#docker)
* [Mesos/Marathon backend](#marathon)
* [Consul backend](#consul)
* [Benchmarks](#benchmarks)


## <a id="basics"></a> Basics


Træfɪk is a modern HTTP reverse proxy and load balancer made to deploy microservices with ease.
It supports several backends ([Docker :whale:](https://www.docker.com/), [Mesos/Marathon](https://mesosphere.github.io/marathon/), [Consul](https://consul.io/), [Etcd](https://coreos.com/etcd/), Rest API, file...) to manage its configuration automatically and dynamically.

Basically, Træfɪk is a http router, which sends traffic from frontends to http backends, following rules you have configured.

### Frontends

Frontends can be defined using the following rules:

 * ```Headers```: Headers adds a matcher for request header values. It accepts a sequence of key/value pairs to be matched. For example: ```application/json```
 * ```HeadersRegexp```: Regular expressions can be used with headers as well. It accepts a sequence of key/value pairs, where the value has regex support. For example: ```application/(text|json)```
 * ```Host```: Host adds a matcher for the URL host. It accepts a template with zero or more URL variables enclosed by {}. Variables can define an optional regexp pattern to be matched: ```www.traefik.io```, ```{subdomain:[a-z]+}.traefik.io```
 * ```Methods```: Methods adds a matcher for HTTP methods. It accepts a sequence of one or more methods to be matched, e.g.: ```GET```, ```POST```, ```PUT```
 * ```Path```: Path adds a matcher for the URL path. It accepts a template with zero or more URL variables enclosed by {}. The template must start with a "/". For exemple ```/products/``` ```/articles/{category}/{id:[0-9]+}```
 * ```PathPrefix```: PathPrefix adds a matcher for the URL path prefix. This matches if the given template is a prefix of the full URL path.


 A frontend is a set of rules that forwards the incoming http traffic to a backend.

### HTTP Backends

A backend is responsible to load-balance the traffic coming from one or more frontends to a set of http servers.
Various methods of load-balancing is supported:

* ```wrr```: Weighted Round Robin
* ```drr```: Dynamic Round Robin: increases weights on servers that perform better than others. It also rolls back to original weights if the servers have changed.

A circuit breaker can also be applied to a backend, preventing high loads on failing servers.
It can be configured using:

* Methods: ```LatencyAtQuantileMS```, ```NetworkErrorRatio```, ```ResponseCodeRatio```
* Operators:  ```AND```, ```OR```, ```EQ```, ```NEQ```, ```LT```, ```LE```, ```GT```, ```GE```

For example:
* ```NetworkErrorRatio() > 0.5```
* ```LatencyAtQuantileMS(50.0) > 50```
* ```ResponseCodeRatio(500, 600, 0, 600) > 0.5```

## <a id="global"></a> Global configuration

```toml
# traefik.toml
################################################################
# Global configuration
################################################################

# Reverse proxy port
#
# Optional
# Default: ":80"
#
# port = ":80"

# Timeout in seconds.
# Duration to give active requests a chance to finish during hot-reloads
#
# Optional
# Default: 10
#
# graceTimeOut = 10

# Traefik logs file
# If not defined, logs to stdout
#
# Optional
#
# traefikLogsFile = "log/traefik.log"

# Access logs file
#
# Optional
#
# accessLogsFile = "log/access.log"

# Log level
#
# Optional
# Default: "ERROR"
#
# logLevel = "ERROR"

# SSL certificate and key used
#
# Optional
#
# CertFile = "traefik.crt"
# KeyFile = "traefik.key"
```


## <a id="file"></a> File backend

Like any other reverse proxy, Træfɪk can be configured with a file. You have two choices:

* simply add your configuration at the end of the global configuration file ```traefik.toml``` :

```toml
# traefik.toml
port = ":80"
graceTimeOut = 10
logLevel = "DEBUG"

[file]

# rules
[backends]
  [backends.backend1]
    [backends.backend1.circuitbreaker]
      expression = "NetworkErrorRatio() > 0.5"
    [backends.backend1.servers.server1]
    url = "http://172.17.0.2:80"
    weight = 10
    [backends.backend1.servers.server2]
    url = "http://172.17.0.3:80"
    weight = 1
  [backends.backend2]
    [backends.backend2.LoadBalancer]
      method = "drr"
    [backends.backend2.servers.server1]
    url = "http://172.17.0.4:80"
    weight = 1
    [backends.backend2.servers.server2]
    url = "http://172.17.0.5:80"
    weight = 2

[frontends]
  [frontends.frontend1]
  backend = "backend2"
    [frontends.frontend1.routes.test_1]
    rule = "Host"
    value = "test.localhost"
  [frontends.frontend2]
  backend = "backend1"
    [frontends.frontend2.routes.test_2]
    rule = "Path"
    value = "/test"


```

* or put your rules in a separate file, for example ```rules.tml```:

```toml
# traefik.toml
port = ":80"
graceTimeOut = 10
logLevel = "DEBUG"

[file]
filename = "rules.toml"
```

```toml
# rules.toml
[backends]
  [backends.backend1]
    [backends.backend1.circuitbreaker]
      expression = "NetworkErrorRatio() > 0.5"
    [backends.backend1.servers.server1]
    url = "http://172.17.0.2:80"
    weight = 10
    [backends.backend1.servers.server2]
    url = "http://172.17.0.3:80"
    weight = 1
  [backends.backend2]
    [backends.backend2.LoadBalancer]
      method = "drr"
    [backends.backend2.servers.server1]
    url = "http://172.17.0.4:80"
    weight = 1
    [backends.backend2.servers.server2]
    url = "http://172.17.0.5:80"
    weight = 2

[frontends]
  [frontends.frontend1]
  backend = "backend2"
    [frontends.frontend1.routes.test_1]
    rule = "Host"
    value = "test.localhost"
  [frontends.frontend2]
  backend = "backend1"
    [frontends.frontend2.routes.test_2]
    rule = "Path"
    value = "/test"

```

If you want Træfɪk to watch file changes automatically, just add:

```toml
[file]
watch = true
```

## <a id="api"></a> API backend

Træfik can be configured using a restful api.
To enable it:

```toml
[web]
address = ":8080"

# SSL certificate and key used
#
# Optional
#
# CertFile = "traefik.crt"
# KeyFile = "traefik.key"
```

* ```/```: provides a simple HTML frontend of Træfik

![HTML frontend](img/web.frontend.png)

* ```/health```: ```GET``` json metrics

```sh
$ curl -s "http://localhost:8080/health" | jq .
{
  "average_response_time_sec": 0,
  "average_response_time": "0",
  "total_response_time_sec": 0,
  "total_response_time": "0",
  "total_count": 0,
  "pid": 12861,
  "uptime": "7m12.80607635s",
  "uptime_sec": 432.80607635,
  "time": "2015-09-22 10:25:16.448023473 +0200 CEST",
  "unixtime": 1442910316,
  "status_code_count": {},
  "total_status_code_count": {},
  "count": 0
}
```

* ```/api```: ```GET``` configuration for all providers

```sh
$ curl -s "http://localhost:8082/api" | jq .
{
  "file": {
    "Frontends": {
      "frontend-traefik": {
        "Routes": {
          "route-host-traefik": {
            "Value": "traefik.docker.localhost",
            "Rule": "Host"
          }
        },
        "Backend": "backend-test2"
      },
      "frontend-test": {
        "Routes": {
          "route-host-test": {
            "Value": "test.docker.localhost",
            "Rule": "Host"
          }
        },
        "Backend": "backend-test1"
      }
    },
    "Backends": {
      "backend-test2": {
        "Servers": {
          "server-stoic_brattain": {
            "Weight": 0,
            "Url": "http://172.17.0.8:80"
          },
          "server-jovial_khorana": {
            "Weight": 0,
            "Url": "http://172.17.0.12:80"
          },
          "server-jovial_franklin": {
            "Weight": 0,
            "Url": "http://172.17.0.11:80"
          },
          "server-elegant_panini": {
            "Weight": 0,
            "Url": "http://172.17.0.9:80"
          },
          "server-adoring_elion": {
            "Weight": 0,
            "Url": "http://172.17.0.10:80"
          }
        }
      },
      "backend-test1": {
        "Servers": {
          "server-trusting_wozniak": {
            "Weight": 0,
            "Url": "http://172.17.0.5:80"
          },
          "server-sharp_jang": {
            "Weight": 0,
            "Url": "http://172.17.0.7:80"
          },
          "server-dreamy_feynman": {
            "Weight": 0,
            "Url": "http://172.17.0.6:80"
          }
        }
      }
    }
  },
  "marathon": {
    "Frontends": {
      "frontend-marathon": {
        "Routes": {
          "route-host-marathon": {
            "Value": "marathon.docker.localhost",
            "Rule": "Host"
          }
        },
        "Backend": "backend-marathon"
      },
    },
    "Backends": {
      "backend-marathon": {
        "Servers": {
          "server-marathon-1": {
            "Weight": 0,
            "Url": "http://172.17.0.8:802"
          },
        },
      },
    },
  },
}

```

* ```/api/{provider}```: ```GET``` or ```PUT``` provider
* ```/api/{provider}/backends```: ```GET``` backends
* ```/api/{provider}/backends/{backend}```: ```GET``` a backend
* ```/api/{provider}/backends/{backend}/servers```: ```GET``` servers in a backend
* ```/api/{provider}/backends/{backend}/servers/{server}```: ```GET``` a server in a backend
* ```/api/{provider}/frontends```: ```GET``` frontends
* ```/api/{provider}/frontends/{frontend}```: ```GET``` a frontend


## <a id="docker"></a> Docker backend

Træfɪk can be configured to use Docker as a backend configuration:

```toml
################################################################
# Docker configuration backend
################################################################

# Enable Docker configuration backend
#
# Optional
#
[docker]

# Docker server endpoint. Can be a tcp or a unix socket endpoint.
#
# Required
#
endpoint = "unix:///var/run/docker.sock"

# Default domain used.
# Can be overridden by setting the "traefik.domain" label on a container.
#
# Required
#
domain = "docker.localhost"

# Enable watch docker changes
#
# Optional
#
watch = true

# Override default configuration template. For advanced users :)
#
# Optional
#
# filename = "docker.tmpl"
```


Labels can be used on containers to override default behaviour:

* ```traefik.backend=foo```: assign the container to ```foo``` backend
* ```traefik.port=80```: register this port. Useful when the container exposes multiples ports.
* ```traefik.weight=10```: assign this weight to the container
* ```traefik.enable=false```: disable this container in Træfɪk
* ```traefik.host=bar```: override the default routing from {containerName}.{domain} to bar.{domain}

## <a id="marathon"></a> Marathon backend

Træfɪk can be configured to use Marathon as a backend configuration:


```toml
################################################################
# Mesos/Marathon configuration backend
################################################################

# Enable Marathon configuration backend
#
# Optional
#
[marathon]

# Marathon server endpoint.
# You can also specify multiple endpoint for Marathon:
# endpoint := "http://10.241.1.71:8080,10.241.1.72:8080,10.241.1.73:8080"
#
# Required
#
endpoint = "http://127.0.0.1:8080"

# Network interface used to call Marathon web services
# Optional
# Default: "eth0"
#
# networkInterface = "eth0"

# Enable watch Marathon changes
#
# Optional
#
watch = true

# Default domain used.
# Can be overridden by setting the "traefik.domain" label on an application.
#
# Required
#
domain = "marathon.localhost"

# Override default configuration template. For advanced users :)
#
# Optional
#
# filename = "marathon.tmpl"
```

Labels can be used on containers to override default behaviour:

* ```traefik.backend=foo```: assign the application to ```foo``` backend
* ```traefik.port=80```: register this port. Useful when the application exposes multiples ports.
* ```traefik.weight=10```: assign this weight to the application
* ```traefik.enable=false```: disable this application in Træfɪk
* ```traefik.host=bar```: override the default routing from {appName}.{domain} to bar.{domain}
* ```traefik.prefixes=pf1,pf2```: use PathPrefix(es) instead of hostname for routing, use filename="providerTemplates/marathon-prefix.tmpl" with this option

## <a id="consul"></a> Consul backend

Træfɪk can be configured to use Consul as a backend configuration:

```toml
################################################################
# Consul KV configuration backend
################################################################

# Enable Consul KV configuration backend
#
# Optional
#
[consul]

# Consul server endpoint
#
# Required
#
endpoint = "http://127.0.0.1:8500"

# Enable watch Consul changes
#
# Optional
#
watch = true

# Prefix used for KV store.
#
# Optional
#
prefix = "traefik"

# Override default configuration template. For advanced users :)
#
# Optional
#
# filename = "consul.tmpl"
```


## <a id="benchmarks"></a> Benchmarks

Here are some early Benchmarks between Nginx and Træfɪk acting as simple load balancers between two servers.

* Nginx:

```sh
$ docker run -d -e VIRTUAL_HOST=test1.localhost emilevauge/whoami
$ docker run -d -e VIRTUAL_HOST=test1.localhost emilevauge/whoami
$ docker run --log-driver=none -d -p 80:80 -v /var/run/docker.sock:/tmp/docker.sock:ro jwilder/nginx-proxy
$ ab -n 20000 -c 20  -r http://test1.localhost/
This is ApacheBench, Version 2.3 <$Revision: 1528965 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking test1.localhost (be patient)
Completed 2000 requests
Completed 4000 requests
Completed 6000 requests
Completed 8000 requests
Completed 10000 requests
Completed 12000 requests
Completed 14000 requests
Completed 16000 requests
Completed 18000 requests
Completed 20000 requests
Finished 20000 requests


Server Software:        nginx/1.9.2
Server Hostname:        test1.localhost
Server Port:            80

Document Path:          /
Document Length:        287 bytes

Concurrency Level:      20
Time taken for tests:   5.874 seconds
Complete requests:      20000
Failed requests:        0
Total transferred:      8900000 bytes
HTML transferred:       5740000 bytes
Requests per second:    3404.97 [#/sec] (mean)
Time per request:       5.874 [ms] (mean)
Time per request:       0.294 [ms] (mean, across all concurrent requests)
Transfer rate:          1479.70 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.1      0       2
Processing:     0    6   2.4      6      35
Waiting:        0    5   2.3      5      33
Total:          0    6   2.4      6      36

Percentage of the requests served within a certain time (ms)
  50%      6
  66%      6
  75%      7
  80%      7
  90%      9
  95%     10
  98%     12
  99%     13
 100%     36 (longest request)

```

* Træfɪk:

```sh
$ docker run -d -l traefik.backend=test1 -l traefik.host=test1 emilevauge/whoami
$ docker run -d -l traefik.backend=test1 -l traefik.host=test1 emilevauge/whoami
docker run -d -p 8080:8080 -p 80:80 -v $PWD/traefik.toml:/traefik.toml -v /var/run/docker.sock:/var/run/docker.sock emilevauge/traefik
$ ab -n 20000 -c 20  -r http://test1.docker.localhost/
This is ApacheBench, Version 2.3 <$Revision: 1528965 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking test1.docker.localhost (be patient)
Completed 2000 requests
Completed 4000 requests
Completed 6000 requests
Completed 8000 requests
Completed 10000 requests
Completed 12000 requests
Completed 14000 requests
Completed 16000 requests
Completed 18000 requests
Completed 20000 requests
Finished 20000 requests


Server Software:        .
Server Hostname:        test1.docker.localhost
Server Port:            80

Document Path:          /
Document Length:        312 bytes

Concurrency Level:      20
Time taken for tests:   6.545 seconds
Complete requests:      20000
Failed requests:        0
Total transferred:      8600000 bytes
HTML transferred:       6240000 bytes
Requests per second:    3055.60 [#/sec] (mean)
Time per request:       6.545 [ms] (mean)
Time per request:       0.327 [ms] (mean, across all concurrent requests)
Transfer rate:          1283.11 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.2      0       7
Processing:     1    6   2.2      6      22
Waiting:        1    6   2.1      6      21
Total:          1    7   2.2      6      22

Percentage of the requests served within a certain time (ms)
  50%      6
  66%      7
  75%      8
  80%      8
  90%      9
  95%     10
  98%     11
  99%     13
 100%     22 (longest request)

```
