# Docker image with Bombard stress testing tool

This repo contain .Dockerfile for automatic build, configure and run of server stress testing tool - "Bombard"

http://blog.modsaid.com/2014/04/stress-testing-with-siege-and-bombard.html

It generally uses provided file with testing URLs and tries to connect to these URLs and measures response time etc.
It automatically creates graphs from results.

The command to build this container:
```
docker build -t bombard .
```

The command to run this container looks like this:
```
docker run --rm -t -i -v $(pwd):/data bombard
```
Some folder is needed to be mounted inside the container to `/data` folder and this folder must contain `test_urls.txt` with one URL per line.
Created graphs and outputs are located in this folder too.

Default configuration of bombard is and can be changed in the docker run command:
`bombard  -f /data/test_urls.txt -i50 -r10 -s100 -t10`

* start with 100 concurrent users
* runs for 10 times
* 50 concurrent users are added every run
* one test run takes 10 minutes


#Siege testing tool & info from TileServer tests
The Siege is a backend for bombard and can be used directly from this docker image too. We use it to estimate maximal throughput of the tested system.

In the throughput testing scenario, we need to use burst or benchmark mode (-b parameter). It runs the test without any delay. Without this option, each simulated user is invoked with at least one second delay. It is probably better to use it with delays, because it is closer to real world usage, but in our system's tests we would need to use tens of thousands of concurrent users to see any peaks or bottlenecks. But the test with this amount of concurrency would consume system resources for the testing tool only.
Testing in benchmark mode has definitely some influence on the tested system too, a cleaner way would be a test from a different server (ideally in the same datacenter).

The command to run Siege can look like this:
```
docker run --rm -t -i -v $(pwd):/data bombard siege -t1m -i -c10 -q -b -f /data/test_urls.txt
```
Some folder is needed to be mounted inside the container to `/data` folder and this folder must contain `test_urls.txt` with one URL per line. Or one (or more URLs) can be passed without `-f`
* 10 concurrent users
* runs for 1 minute
* benchmark mode (runs test without delays)
* internet mode (uses random URLs from source file)
