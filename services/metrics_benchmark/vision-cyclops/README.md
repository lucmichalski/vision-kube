ELK stack with docker-compose for mining performances of Computer Vision recognition frameworks
===

All with logstash-forwarder, secured with nginx, and gift wrapped with docker-compose.

Performances Benchmark: Elasticsearch. Logstash. Kibana. Nginx. Docker.
False/Positive Mining: Spark. Neo4j. Mazerunner
Logs parser available for: Ltu 7.6, VMX v1.0, VMX v2.0

To do: 
- Import automatically CSV to Neo4J
- NGINX Reverse Proxy on VMX Web UI port 3000
- NGINX Reverse Proxy on Neo4J Web UI on 7474

## Grab it
```
git clone https://github.com/blippar/vmx-metrics.git
cd vmx-metrics
./start.sh

In the browser:
Kibana 4.1: https://1.2.3.4:1443/
Elastic Rest API: https://1.2.3.4:1443/elasticsearch/
Elastic Plugins:
- https://1.2.3.4:1443/elasticsearch/_plugin/HQ/
- https://1.2.3.4:1443/elasticsearch/_plugin/gui/

```

## Configure it

#### Create an htpasswd file for nginx/kibana
```
htpasswd -c nginx/conf/htpasswd username
```
Add the `htpasswd` file to the `conf` folder.

### Configure logstash for greatness

Add your filters in `logstash/conf.d`, which get linked as a volume in the logstash container to `/etc/logstash/conf.d`. Patterns can be added in `logstash/patterns` and can be used with `patterns_dir => '/opt/logstash/patterns_extra'` in `grok` sections of your filters.

### Install logstash-forwarder everywhere

Keep the certificate and key you created earlier handy, you'll need those.

On every machine you need to send logs from, install logstash-forwarder:
```
wget -O - http://packages.elasticsearch.org/GPG-KEY-elasticsearch | sudo apt-key add -
echo "deb http://packages.elasticsearch.org/logstashforwarder/debian stable main" | sudo tee -a /etc/apt/sources.list.d/elasticsearch.list
sudo apt-get update && sudo apt-get install logstash-forwarder
```

## Launch it
```
docker-compose up
```
Use with `-d` once you like what you're seeing.

All data and indices get stored in `/opt/data/elasticsearch`, also mounted as a volume.
