# docker-kibana

This is [Kibana 4.1.1](https://github.com/elastic/kibana) in a minimal 47MB Docker image. Images are tagged by Kibana versions.

[![Docker Repository on Quay.io](https://quay.io/repository/pires/docker-kibana/status "Docker Repository on Quay.io")](https://quay.io/repository/pires/docker-kibana)

## Running

```
docker run --name kibana \
           --detach \
           --publish 5601:5601 \
           -e KIBANA_ES_URL=<elasticsearch url> \
           quay.io/pires/docker-kibana:4.1.1
```

You could set `KIBANA_INDEX` env variable to set an index for Kibana's data.
