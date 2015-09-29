# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from requests.auth import HTTPBasicAuth
import requests
import json

class RestApi(object):
    def __init__(self, base_url='http://localhost:8000/api/'):
        self.base_url = base_url
        if not self.base_url.endswith('/'):
            self.base_url = self.base_url + '/'
        self.authentication = HTTPBasicAuth('kevin', 'axes')
        
    def url(self, method, pk=None):
        target = self.base_url + method
        if not target.endswith('/'):
            target += '/'
        if pk:
            target += str(pk) + '/'
        return target
    
    def get(self, method, pk=None):
        headers = { 'Accept': 'application/json' }
        url = self.url(method, pk)
        print url
        response = requests.get(url, 
            auth=self.authentication,
            headers=headers)
        response.raise_for_status()
        return response.json()
    
    def post(self, method, data):
        headers = {
            'Content-type': 'application/json', 
            'Accept': 'application/json'
        }
        response = requests.post(self.url(method), 
            auth=self.authentication,
            data=json.dumps(data), headers=headers)
        response.raise_for_status()
        return response.json()
    
    def put(self, method, pk, data):
        headers = {
            'Content-type': 'application/json', 
            'Accept': 'application/json'
        }
        response = requests.put(self.url(method,pk), 
            auth=self.authentication,
            data=json.dumps(data), headers=headers)
        response.raise_for_status()
        return response.json()
        
    def delete(self, method, pk):
        headers = { 'Accept': 'application/json' }
        response = requests.delete(self.url(method, pk), 
            auth=self.authentication, headers=headers)
        response.raise_for_status()
        return response.json()

api = RestApi()
    