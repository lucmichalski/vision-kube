# SMART VISUAL ROUTING PROXY STEP2

VINCENT:
- Launch a cluster of 1 master and 4 Minions on AWS Cloud Formation

Notes: 
Tried the following JSON  from this Googler, release a month ago:
https://github.com/thesamet/kubernetes-aws-coreos
Cannot get back a public IP from Kube Proxy of slaves, without restarting some services on the master. He wanted to solve a huger scope of problems like ELB dynamic creations.

How to reproduce ? Just follow the setps
https://github.com/kubernetes/kubernetes/blob/release-1.0/examples/https-nginx/README.md

The goal:
- A perfect launch in any case of empty instances; I need to focus only on model building not devops.
- Fix problems related to VPC and self-signed SSL certificates.
- Being able to restart the master and get back a communication with minions.
- We need to use m4.10x.larger for any SVM reco based solutions.

## Launching the Cloud Formation

```
$ aws cloudformation create-stack --stack-name vmx2-kube \
    --region eu-central-1 \
    --template-body file://cloudformation-template.json \
    --capabilities CAPABILITY_IAM \
    --parameters \
    ParameterKey=ClusterSize,ParameterValue=4 \
    ParameterKey=VpcId,ParameterValue=vpc-517b9c38 \
    ParameterKey=SubnetId,ParameterValue=subnet-01dcdd79 \
    ParameterKey=SubnetAZ,ParameterValue=eu-central-1 \
    ParameterKey=KeyPair,ParameterValue=vmx-eu
```

## Steps

1. Launch the cloudformation and wait to get the instances up

- Dowload AMAZON AWS Cli (https://github.com/aws/aws-cli)
- Download the JSON File for the last attempt, it will create services for docker with a HVM AMI (for having the m4.10x.large instances).

2. Launch Vulcand Proxy
- Goal: Reverse proxy any services from kubernetes with middlewares and real time configuration changes
- Launched on port 0.0.0.0:80 and 0.0.0.0:443
- $ docker run -d -p 8182:8182 -p 8181:8181 mailgun/vulcand:v0.8.0-beta.3 /go/bin/vulcand -apiInterface=0.0.0.0 --etcd=http://[PRIVATE_IPV4_MASTER]:4001

3. Launch Romulus
- Goal: Register any frontends, backends created in Kubernetes through YAML or JSON files.
- $ docker run exoplay/romulusd --etcd-timeout=2s --etcd=http:[PRIVATE_IPV4]:2379 --kube=http://[KUBE_API_IP]:8080 --kube-api="v1" -d --debug-etcd
- Repo: https://github.com/timelinelabs/romulus

4. Create 2 new service from the examples below and

### Credentials

SubnetAZ: eu-central-1b or eu-central-1a
VPC: vpc-517b9c38 (can be something else)


aws cloudformation create-stack --stack-name vmx2-kube \
    --region eu-central-1 \
    --template-body file://cloudformation-template.json \
    --capabilities CAPABILITY_IAM \
    --parameters \
    ParameterKey=ClusterSize,ParameterValue=4 \
    ParameterKey=VpcId,ParameterValue=vpc-517b9c38 \
    ParameterKey=SubnetId,ParameterValue=subnet-01dcdd79 \
    ParameterKey=SubnetAZ,ParameterValue=eu-central-1b \
    ParameterKey=KeyPair,ParameterValue=vmx-eu

# romulusd

Automagically register your kubernetes services in vulcan proxy!

## Usage

```
$ romulusd --help
usage: romulusd [<flags>]

A utility for automatically registering Kubernetes services in Vulcand

Flags:
  --help           Show help (also see --help-long and --help-man).
  --vulcan-key="vulcand"
                   default vulcand etcd key
  -e, --etcd=http://127.0.0.1:2379
                   etcd peers
  -t, --etcd-timeout=5s
                   etcd request timeout
  -k, --kube=http://127.0.0.1:8080
                   kubernetes endpoint
  -U, --kube-user=KUBE-USER
                   kubernetes username
  -P, --kube-pass=KUBE-PASS
                   kubernetes password
  --kube-api="v1"  kubernetes api version
  -C, --kubecfg=/path/to/.kubecfg
                   path to kubernetes cfg file
  -s, --svc-selector=key=value[,key=value]
                   service selectors. Leave blank for Everything(). Form: key=value
  -d, --debug      Enable debug logging. e.g. --log-level debug
  -l, --log-level=info
                   log level. One of: fatal, error, warn, info, debug
  --debug-etcd     Enable cURL debug logging for etcd
```

Set up your kubernetes service with a label and some options annotations:

*NOTE*: all labels and annotations are under the prefix `romulus/`
*NOTE 2*: set the etcd vulcand prefix with the label `romulus/vulcanKey`. If not set, then the default key is used (see flag `--vulcan-key`)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: example
  annotations:
    romulus/host: 'www.example.com'
    romulus/pathRegexp: '/guestbook/.*'
    romulus/frontendSettings: '{"FailoverPredicate":"(IsNetworkError() || ResponseCode() == 503) && Attempts() <= 2"}}'
    romulus/backendSettings: '{"KeepAlive": {"MaxIdleConnsPerHost": 128, "Period": "4s"}}'
  labels:
    name: example
    romulus/vulcanKey: 'vulcand-test'
    romulus/type: external # <-- Will ensure SVC-SELECTORs specified (e.g. 'type=external') are present in Labels.
spec: 
...
```

When you create the service, romulusd will create keys in etcd for vulcan!

*NOTE*: IDs for backends and frontends are constructed as follows: `[<port name>.]<kube resource name>.<namespace>`

```
$ kubectl.sh get svc,endpoints -l romulus/type=external
NAME           LABELS                            SELECTOR            IP(S)           PORT(S)
frontend       name=frontend,type=external       name=frontend       10.247.242.50   80/TCP
NAME           ENDPOINTS
frontend       10.246.1.7:80,10.246.1.8:80,10.246.1.9:80

$ etcdctl get /vulcand-test/backends/example.default/backend
{"Id":"example.default","Type":"http","Settings":{"KeepAlive":{"MaxIdleConnsPerHost":128,"Period": "4s"}}}

$ etcdctl get /vulcand-test/frontends/example.default/frontend
{"Id": "example.default","Type":"http","BackendId":"example.default","Route":"Host(`www.example.com`) && PathRegexp(`/guestbook/.*`)","Settings":{"FailoverPredicate":"(IsNetworkError() || ResponseCode() == 503) && Attempts() <= 2"}}

$ etcdctl ls /vulcand-test/backends/example.default/servers
/vulcand-test/backends/example.default/servers/10.246.1.8
/vulcand-test/backends/example.default/servers/10.246.1.9
/vulcand-test/backends/example.default/servers/10.246.1.7
```

## Multi Port Services

If your service has multiple ports, romulusd will create a frontend for each.

Separate options by appending the port name as a suffix (e.g. `romulus/path.api`). If no matching `romulus/<opt>.<port_name>` option exists, then the `romulus/<opt>` option will be used.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: example
  annotations:
    romulus/host: 'www.example.com'
    romulus/path.api: '/api'
    romulus/path.web: '/web'
  labels:
    name: example
    romulus/type: external
spec:
  ports:
  - port: 80
    name: web
  - port: 8888
    name: api
...
```

```
$ etcdctl ls /vulcand/backends
/vulcand/backends/api.example.default
/vulcand/backends/web.example.default

$ etcdctl ls /vulcand/frontends
/vulcand/frontends/web.example.default
/vulcand/frontends/api.example.default

$ etcdctl get /vulcand/frontends/api.example.default/frontend
{"Id":"api.example.default","Type":"http","BackendId":"api.example.default","Route":"Host(`www.example.com`) && Path(`/api`)"}
```

Kubernetes on AWS with CoreOS through CloudFormations
-----------------------------------------------------

This repository provides an easy way to set up a fully working Kubernetes
cluster on EC2 machines running CoreOS.

*Highlights (things that are actually working out of the box):*

- Uses an existing provided VPC instead of creating a new one.
- Secure token authentication
- EBS volume mounts
- ELB creation works (if you have just one subnet, upsteam bug)
- SkyDNS works

*Lowlights:*

- Custom Kubernetes binaries are used instead of the official ones. It was built from master,
  and cherry-picked [this PR](https://github.com/GoogleCloudPlatform/kubernetes/pull/8530)
  since otherwise EBS mounting will not work. However, you can customize where
  the release is downloaded from by passing a parameter to the template (see
  the template for more information)

*Note:*

- All EC2 instances created will have only private IPs. You will need to have
  some way of connecting to them (some other machine in your VPC with a
  public IP)

Getting Started
---------------

Download the template:

```
curl https://raw.githubusercontent.com/thesamet/kubernetes-aws-coreos/master/output/cloudformation-template.json -o cloudformation-template.json
```

Launch it with this command (don't forget to replace <things> with actual
values):

```
aws cloudformation create-stack --stack-name kubernetes \
    --region us-west-2 \
    --template-body file://cloudformation-template.json \
    --capabilities CAPABILITY_IAM \
    --parameters \
    ParameterKey=VpcId,ParameterValue=<vpc-id> \
    ParameterKey=SubnetId,ParameterValue=<subnet-id> \
    ParameterKey=SubnetAZ,ParameterValue=<subnet-az> \
    ParameterKey=KeyPair,ParameterValue=<keypair>
```

On the machine you intend to access your cluster from (your computer, if it
can reached the master through its private IP).

1. Download kubectl (this version of kubectl matches the default
version being installed by the CloudFormation template):

```
curl http://nadavsr-kubernetes-build.s3-website-us-west-2.amazonaws.com/kubectl -o
    kubectl && chmod +x ./kubectl
```

2. Find the Kubernetes master IP (look for it in the EC2 console). Wait for it to be up.
Then, copy your kubeconfig from it:

```
scp core@<master_ip>:kubeconfig ~/.kube/config
```

3. Try it out:

`./kubectl version`

`./kubectl get nodes`


