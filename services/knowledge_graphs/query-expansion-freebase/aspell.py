import popen2

# adapted from http://blog.quibb.org/2009/04/spell-checking-in-python/
 
class aspell:
    def __init__(self):
        self._f = popen2.Popen3("aspell -a")
        self._f.fromchild.readline() #skip the credit line
    
    def suggest(self, words):
        words = words.split(' ')
        output = []
        for word in words:
            self._f.tochild.write(word+'\n')
            self._f.tochild.flush()
            s = self._f.fromchild.readline().strip()
            self._f.fromchild.readline() #skip the blank line
            if s == "*":
                # not an error
                output.append(word)
            elif s[0] == '#':
                # error, but no idea how to correct it
                output.append(word)
            else:
                suggestions = s.split(':')[1].strip().split(', ')
                # we have no idea, so we take the first one
                output.append(suggestions[0])
        return output
