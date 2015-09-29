#
# Django utility template tags and filters
#
# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django import template

register = template.Library()

@register.filter
def key(dict, k):
    return dict[k]