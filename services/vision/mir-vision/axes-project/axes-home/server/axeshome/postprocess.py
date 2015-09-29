#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Response post processors
"""
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
        self._name_stack = []
    
    def process(self, value, name=None):
        if name is not None:
            self._name_stack.append(name)
        if isinstance(value, dict):
            result = self.process_dict(value, name)
        elif isinstance(value, list):
            result = self.process_list(value, name)
        else:
            result = self.process_entry(value, name)
        if name is not None:
            self._name_stack.pop()
        return result
    
    def process_dict(self, d, name):
        for k, v in d.iteritems():
            d[k] = self.process(v, k)
        return d
        
    def process_list(self, l, name):
        for i, v in enumerate(l):
            l[i] = self.process(v, name)
        return l
    
    def process_entry(self, value, name):
        for key, rule in self.rules.iteritems():
            if self.name_stack_matches_rule(key):
                value = self.apply_rule(rule, name, value)
        return value
    
    def name_stack_matches_rule(self, key):
        parts = key.split('.')
        stack = self._name_stack[-len(parts):]
        return parts == stack
    
    def apply_rule(self, rule, name, value):
        for pattern, repl in rule:
            value = re.sub(pattern, repl, value)
        return value
        
                