#!/bin/bash 
cd /ccv/lib
./configure
make
cd /ccv/bin
make
cd /ccv/serve
make
