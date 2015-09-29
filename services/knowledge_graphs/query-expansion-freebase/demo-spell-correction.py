import aspell
import sys

#from nltk.corpus import wordnet as wn
#print wn.synsets('dog') 

if len(sys.argv)>1:
  sentence = ' '.join(sys.argv[1:])
else:
  sentence = 'Hallo, give me the orange juice or maybe the oange jice'
spellchecker = aspell.aspell()

print "Original sentence:", sentence

scresult = spellchecker.suggest(sentence)
print "Spellchecker result:", scresult

print "Corrected sentence:", ' '.join(scresult)

