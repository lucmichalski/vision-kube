#!/usr/bin/env python
#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
import sys

from axeshome import start_server

if __name__ == '__main__':
    if len(sys.argv) > 1:
        port = int(sys.argv[1])
    else:
        port = 5002
    start_server(True, port=port)
    