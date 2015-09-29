#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
from axeshome.api import app

def start_server(debug=False, port=5002):
    if debug:
        import logging
        logging.getLogger('axeshome').setLevel(logging.DEBUG)
    app.run(debug=debug, port=port)
    