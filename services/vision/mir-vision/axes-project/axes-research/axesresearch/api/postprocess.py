import re

class RegexPostprocessor(object):
    """
    Limas response post processing using regular expression replacements
    on string fields.
    
    Requires a set of rules in the form::
    
    rules = {
        'fieldName': [
            ('pattern', 'replacement'),
            ('pattern', 'replacement'),
        ]
    }
    """
    
    def __init__(self, rules):
        self.rules = rules
    
    def process(self, value, name=None):
        if isinstance(value, dict):
            return self.process_dict(value, name)
        elif isinstance(value, list):
            return self.process_list(value, name)
        return self.process_entry(value, name)
    
    def process_dict(self, d, name):
        for k, v in d.iteritems():
            d[k] = self.process(v, k)
        return d
        
    def process_list(self, l, name):
        for i, v in enumerate(l):
            l[i] = self.process(v, name)
        return l
    
    def process_entry(self, value, name):
        if name in self.rules:
            rule = self.rules[name]
            for pattern, repl in rule:
                value = re.sub(pattern, repl, value)
        return value
                