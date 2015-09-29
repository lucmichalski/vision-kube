# Cloud Scalable services and micro-services framework

- AWS Cloud formation for production launch of the master and minions 
- Merging several backends Computer Vision frameworks inside the Kubernetes System API service
- Merging key metrics tools for false/positive mining inside a local or production clusters of coreos instances

## Using the following tools:
Container Management:
- [Docker](https://github.com/docker/docker)
- [Docker Compose](https://www.docker.com/docker-compose)

Operating System:
- [Coreos](https://coreos.com)
- Running CoreOS on EC2: https://coreos.com/os/docs/latest/booting-on-ec2.html

Amazon AWS
- [AWS CLI]()
- [AWS Cloudformation]()

Proxy and services management
- [Kubernetes](https://github.com/kubernetes/kubernetes) Container Cluster Manager from Google
- [Vulcand](https://github.com/mailgun/vulcand) Programmatic load balancer backed by Etcd
- [Romulusd](https://github.com/timelinelabs/romulus) utility to register kubernetes endpoints in vulcand

# Kube-UI

![Screenshot](http://kubernetes.io/v1.0/docs/user-guide/k8s-ui-overview.png)

# Graph Component for Kubernetes WebUI

This is the Graph component for the Kubernetes UI. It uses the [d3 Force Layout](https://github.com/mbostock/d3/wiki/Force-Layout) to expose the structure and organization of the cluster, creating renderings like this one:

![Screenshot](https://raw.githubusercontent.com/kubernetes-ui/graph/master/GraphTab.png)

It contains a legend that lets the user filter the types of objects displayed. Modifier keys let the user zoom the graph, and select or pin individual objects. Objects can also be inspected to display their available properties.

## Data Source
By default, the data displayed by the Graph tab is collected from the Kubernetes api server and the Docker daemons, and assembled into a single JSON document exposed on a REST endpoint by the cluster-insight container available [here](https://registry.hub.docker.com/u/kubernetes/cluster-insight/) on DockerHub. Installation and usage instructions for the cotainer are provided [here](https://github.com/google/cluster-insight) on GitHub.

The data are cached by the container and refreshed periodically to throttle the load on the cluster. The application can poll the container for the document continuously or on demand. When new contents are retrieved from the container, the application transforms them into the shape displayed on the canvas using a pluggable transform engine that loads transforms from the assets folder. The default transform is declarative; it interprets JSON documents loaded from the same location.

Canned data is also available for use without cluster-insight. It's selectable using the 'cloud' button located above the canvas. The canned data is served from a file in the assets folder.


[![Analytics](https://kubernetes-site.appspot.com/UA-36037335-10/GitHub/www/master/components/graph/GraphTab.png?pixel)]()


[![Analytics](https://kubernetes-site.appspot.com/UA-36037335-10/GitHub/www/master/components/graph/README.md?pixel)]()
