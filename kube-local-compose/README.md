# Launch [Kubernetes](http://kubernetes.io) using Docker via [Docker Compose](https://www.docker.com/docker-compose)

The following will also be set up for you:

 * The Kubernetes [DNS addon](https://github.com/kubernetes/kubernetes/tree/master/cluster/addons/dns)
 * [Kube UI](http://kubernetes.io/v1.0/docs/user-guide/ui.html)

## Starting Kubernetes on Linux

On Linux we'll run Kubernetes using a local Docker Engine. You will also need a local installation of Docker Compose. To launch the cluster:

```sh
./up.sh
```

## Kube UI

You can access Kube UI at http://localhost:8080/ui.

## To destroy the cluster

```sh
./down.sh
```

This will also remove any services, replication controllers and pods that are running in the cluster.
