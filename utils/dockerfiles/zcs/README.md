# Zadara Storage ZCS

The ZCS folder contains Dockerfiles which can be used as a baseline for your apps which can be deployed on a [Virtual Private Storage Array (VPSA)](http://www.zadarastorage.com).

## Getting Started

Since ZCS containers run inside a RAID controller, you will not have direct access to your app, thus, you will need to configure ssh in your docker file. An example on how to do this given in the dockerfiles/zcs/sshd folder.
