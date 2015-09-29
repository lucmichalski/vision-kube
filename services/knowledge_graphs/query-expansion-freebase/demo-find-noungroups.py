import nltk
import re
import pprint
import sys

#sentence = 'Please give me the Odwalla juice box and the fancy green box of Cap\'n crunch'
sentence = ' '.join(sys.argv[1:])

tokens = nltk.word_tokenize(sentence)

print "== Tokens obtained using nltk.word_tokenize(sentence):"
print tokens
print 

print "== We are now using the following tagger: ", nltk.tag._POS_TAGGER
tagged_tokens = nltk.pos_tag( tokens )

print "== Tags obtained using nltk.pos_tag()"
print tagged_tokens
print 

grammar = "NP: {(<NN>|<NNP>)+}"
cp = nltk.RegexpParser(grammar)
parsed_sentence = cp.parse(tagged_tokens)

print "== Parsing result using the grammar:", grammar
print parsed_sentence
print 

print "== Groups of nouns"
for e in parsed_sentence:
  if not isinstance(e,tuple):
    terms = []
    for term in e:
      terms.append( term[0] )
    print ' '.join(terms)
