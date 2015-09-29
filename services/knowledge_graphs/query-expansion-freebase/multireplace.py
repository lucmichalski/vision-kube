import re

""" Code obtained from http://stackoverflow.com/questions/6116978/python-replace-multiple-strings """

def mreplacer(replace_dict):
    replacement_function = lambda match: replace_dict[match.group(0)]
    pattern = re.compile("|".join([re.escape(k) for k, v in replace_dict.iteritems()]), re.M)
    return lambda string: pattern.sub(replacement_function, string)

def mreplace(string, key_values):
    return mreplacer(key_values)(string)
