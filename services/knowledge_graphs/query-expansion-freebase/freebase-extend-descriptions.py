import json
import sys
from freebase import freebase
import re
from pprint import pprint
import flexconfig

parser = flexconfig.get_parser('~/.freebase_config')
parser.add_argument('--apikey')
parser.add_argument('--filter', help='restriction to a certain branch in freebase')
parser.add_argument('--src', required=True, help='json file with annotations for a set of images')
parser.add_argument('--dst', required=True, help='output json file')
parser.add_argument('--mode', default='EXPAND_NOUN_GROUPS', help='EXPAND_NOUN_GROUPS uses NLP parsing to substitute noun groups only, EXPAND_ALL_WORDS substitutes all words not in the ignore list')
parser.add_argument('--verbose', help='print debug statements', action='store_true')


args = parser.parse_args()

annofile = args.src
newannofile = args.dst

if args.verbose:
  print "Parsing", annofile

with open(annofile) as f:
  annodata = json.load(f)

fb = freebase(args.apikey, args.filter)
fb.addIgnoreList ( 'embedding_vocab.txt', args.verbose )
fb.addIgnoreList ( 'imagenet_vocab.txt', args.verbose )

params = {
  'mode': args.mode,
  'use_only_first_sentence': True
}


newannodata = {}
for index,imgfile in enumerate(annodata):
  
  if args.verbose:
    print "== ", imgfile, '%5.2f %%'%(100*index/len(annodata))
  descriptions = annodata[imgfile]
  newannodata[imgfile] = []
  for d in descriptions:
    new_d = fb.expandSentence ( d, params, args.verbose )
    newannodata[imgfile].append ( new_d )

if args.verbose:
  print "Saving everything to", newannofile
with open(newannofile,'w') as fout:
  json.dump ( newannodata, fout, sort_keys=True, indent=4)


