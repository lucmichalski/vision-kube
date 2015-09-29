import json
import urllib
import sys
import re
import flexconfig

parser = flexconfig.get_parser('~/.freebase_config')
parser.add_argument('--apikey')
parser.add_argument('--query',nargs='+',required=True)
args = parser.parse_args()

service_search_url = 'https://www.googleapis.com/freebase/v1/search'
params = {
  'query': args.query,
  'filter': '(all type:/food/food)',
  'limit': 20,
#  'indent': true,
  'key': args.apikey
}
url = service_search_url + '?' + urllib.urlencode(params)
search_response = json.loads(urllib.urlopen(url).read())

if not 'result' in search_response:
  print "Error: freebase denied request"
  print search_response
  exit(-1)

if (len(search_response['result']) == 0):
  print "Error: phrase <", args.query, "> not found in freebase"
  exit(-1)

found_description = False
k = 0
while not found_description:
  if k >= len(search_response['result']):
    print "Error: phrase <", query, "> has no description in freebase"
    break


  firstresult = search_response['result'][k];
  freebase_id=firstresult['mid'];

  url_topic = 'https://www.googleapis.com/freebase/v1/topic' + freebase_id;
  response = json.loads(urllib.urlopen(url_topic).read())
  p = response['property']

  if '/common/topic/description' in p:
    descriptions = p['/common/topic/description']
    for i, desc in enumerate(descriptions['values']):
      print desc['value'].encode("ASCII", 'ignore')
      found_description = True

  k = k + 1
