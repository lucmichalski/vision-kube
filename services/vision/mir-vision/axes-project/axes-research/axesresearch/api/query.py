# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
__all__ = ['QueryEncoder']

def dict_get(d, names):
    if isinstance(names, str):
        names = names.split(' ')
    return tuple(d[name] for name in names)      

def escape_parens(value):
    return value.replace('(', '\\(').replace(')', '\\)')
    
def escape_quotes(value):
    return value.replace('"', '\\"')

def format_selection(selection):
    return '{x1}:{y1}:{x2}:{y2}'.format(selection)

def format_image(image):
    selection = image.get('selection', None)
    keyframe = image['imageUrl']
    if selection:
        return keyframe + u' ' + format_selection(selection)
    return keyframe

def format_image_list(images):
    return ' '.join(map(format_image, images))
    
class QueryEncoderBase(object):
    
    def __init__(self):
        self.parts = []
        self.text = []
        
    def add_part(self, name, value, required=False):
        prefix = u'+#' if required else u'#'
        value = escape_parens(value)
        part = u'{}{}({})'.format(prefix, name, value)
        self.parts.append(part)
        
    def add_text(self, text):
        text = text.strip()
        if text:
            self.text.append(text)
        
    @property
    def encoded_query(self):
        return u' '.join(self.parts)
        
    @property
    def query_text(self):
        return u' '.join(self.text)

class QueryEncoder(QueryEncoderBase):
    """
    Take query JSON and encode it in the mini-language understood by
    the link management and structured search engine (limas).
    """
        
    def encode(self, query):
        self.encode_query_text(query['textQueryType'], query['text'])
        if 'imageQueryType' in query:
            self.encode_query_images(
                query['imageQueryType'], query['queryImages'])
        return self.encoded_query
        
    def encode_query_text(self, type, text):
        if text:
            # FIXME: Work around here to fix bug in limas query parser
            text = text.replace('+', ' ').replace('#', '')
            for query_type in type.split(' '):
                self.add_part(query_type, text)
        self.add_text(text)
        
    def encode_query_images(self, type, images):
        if images:
            for query_type in type.split(' '):
                self.add_part(query_type, format_image_list(images))

class AdvancedQueryEncoder(QueryEncoderBase):
    """
    Take query JSON and encode it in the mini-language understood by
    the link management and structured search engine (limas).
    """
    
    def encode(self, query):
        for clause in query['clauses']:
            self.encode_clause(clause)
        return self.encoded_query
    
    def encode_clause(self, clause):
        text = clause['text']
        type = clause['type']
        if not type.endswith("-i"):
            text = text.replace('+', ' ').replace('#', '')
            self.add_text(text)
        for query_type in type.split(' '):
            self.add_part(query_type, text)

def encode_query(query):
    if query.get('type') == 'advanced':
        encoder = AdvancedQueryEncoder()
    else:
        encoder = QueryEncoder()
    encoder.encode(query)
    return encoder
