import json
import urllib
import sys
import re
import flexconfig

# This is just a demo freebase script

parser = flexconfig.get_parser('~/.freebase_config')
parser.add_argument('--query',nargs='+',required=True)
parser.add_argument('--apikey')
parser.add_argument('--filter')

args = parser.parse_args()
print args

service_search_url = 'https://www.googleapis.com/freebase/v1/search'
params = {
  'query': args.query,
  'filter': args.filter,
  'limit': 20,
#  'indent': true,
  'key': args.apikey
}
url = service_search_url + '?' + urllib.urlencode(params)
print "Query URL: ", url
response = json.loads(urllib.urlopen(url).read())

if (len(response['result']) == 0):
  print "Error: phrase not found in freebase"
  exit(-1)

for i, result in enumerate(response['result']):
  print "freebase result", i+1, ":", result['score']," ",result['mid']," ",result['name']

print "number of semantic concepts received: ", len(response['result'])
print "# Processing the first result only"
firstresult = response['result'][0];
freebase_id=firstresult['mid'];

params = {
  'key': args.apikey,
}
url_topic = 'https://www.googleapis.com/freebase/v1/topic' + freebase_id + "?" + urllib.urlencode(params)
print "Freebase ID:", freebase_id, " ( ", url_topic, " )"
response = json.loads(urllib.urlopen(url_topic).read())

# print basically everything :)
#print "\n\n\n"
#print response
for result in response['property']:
  print result
# , "   ", response['property'][result]
#  print "\n"

print "# Types"
p = response['property']
if '/common/topic/notable_types' in p:
  ntypes = response['property']['/common/topic/notable_types'];
  for i, ntype in enumerate(ntypes['values']):
    print "type", i+1, ":", ntypes['values'][i]['text']

print "# Webpages"
if '/common/topic/topic_equivalent_webpage' in p:
  webpages = p['/common/topic/topic_equivalent_webpage']
  for i, webpage in enumerate(webpages['values']):
    # webpage['lang'] is available but not really set
    print "webpage", i+1, ":", webpage['value'] 

print "# Descriptions"
if '/common/topic/description' in p:
  descriptions = p['/common/topic/description']
  for i, desc in enumerate(descriptions['values']):
    print "description", i+1, ":", desc['value']
    firstSentence = re.search("^\s*([^\.]+)\s*\.", desc['value']).group()
    print "description first sentence", i+1, ":", firstSentence

print "# Texts"
if '/common/topic/article' in p:
  text = p['/common/topic/article']
  for i, t in enumerate(text['values']):
     print "text", i+1, ":", t['text']

