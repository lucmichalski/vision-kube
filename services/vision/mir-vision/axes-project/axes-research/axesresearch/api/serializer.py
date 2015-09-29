# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
# -*- coding: utf-8 -*-
"""
JSON Data serialization routines
"""

import re
import json
import bson

from datetime import datetime

try:
    from django.db import models
    from django.db.models import Model
    from django.db.models.query import QuerySet
    from django.utils.encoding import smart_unicode, is_protected_type
    has_django = True
except Exception, e:
    has_django = False
    
try:
    from bson.objectid import ObjectId
    has_objectid = True
except ImportError:
    has_objectid = False

try:
    import mongoengine
    has_mongoengine = True
except ImportError:
    has_mongoengine = False

__all__ = ('serialize',)

class SerializedObject(object):

    def __init__(self, obj, **options):
        self.obj = obj
        self.options = options

    def to_python(self):
        return self.serialize(self.obj)
        
    def serialize(self, obj):
        
        if isinstance(obj, dict):
            return self.serialize_dict(obj)
        elif isinstance(obj, (list, tuple)):
            return self.serialize_list(obj)
        elif isinstance(obj, datetime):
            return self.serialize_datetime(obj)
        elif has_django and isinstance(obj, Model):
            return self.serialize_django_model(obj)
        elif has_django and isinstance(obj, QuerySet):
            return self.serialize_django_query_set(obj)
        elif has_mongoengine and isinstance(obj, \
            mongoengine.document.BaseDocument):
            return self.serialize_mongoengine_document(obj)
        elif has_mongoengine and isinstance(obj, \
            mongoengine.queryset.QuerySet):
            return self.serialize_mongoengine_queryset(obj)
        elif has_objectid and isinstance(obj, ObjectId):
            return str(obj)
        else:
            return obj
            
    def serialize_datetime(self, obj):
        dateformat = self.options.get('dateformat', '%d-%m-%Y')
        return obj.strftime(dateformat)
        
    def serialize_django_model(self, obj):
        return DjangoModelSerializer(obj, **self.options).serialize()
        
    def serialize_django_query_set(self, obj):
        return DjangoQuerySetSerializer(obj, **self.options).serialize()
        
    def serialize_mongoengine_document(self, obj):
        return MongoDocumentSerializer(obj, **self.options).serialize()
        
    def serialize_mongoengine_queryset(self, obj):
        return MongoQuerySetSerializer(obj, **self.options).serialize()
        
    def serialize_dict(self, obj):
        result = {}
        for k, v in obj.iteritems():
            result[k] = self.serialize(v)
        return result
        
    def serialize_list(self, obj):
        return [self.serialize(v) for v in obj]
        
def serialize(obj, **options):
    py_obj = SerializedObject(obj, **options).to_python()
    return json.dumps(py_obj)

class SerializerList(list):
    def __contains__(self, value):
        for item in self:
            if hasattr(item, 'match'):
                # regular expression
                if item.match(value):
                    return True
            else:
                if item == value:
                    return True
        return False

class Serializer(object):
    def __init__(self, obj, **options):
        self.obj = obj
        self.options = options
        self.fields = SerializerList(options.get('fields', []))
        self.excludes = SerializerList(options.get('excludes', []))

        assert isinstance(self.fields, (tuple, list))
        assert isinstance(self.excludes, (tuple, list))

class DjangoModelSerializer(Serializer):

    def __init__(self, *args, **kwargs):
        self.use_natural_keys = True
        super(DjangoModelSerializer, self).__init__(*args, **kwargs)

    def serialize(self):
        assert isinstance(self.obj, Model)

        self.result = {}
        for field in self.obj._meta.local_fields:
            if field.serialize:
                if field.rel is None:
                    if (not self.fields or field.attname in self.fields) and \
                        not field.attname in self.excludes:
                        self.handle_field(field)
                else:
                    if (not self.fields or field.attname[:-3] in self.fields) \
                        and not field.attname in self.excludes:
                        self.handle_fk_field(field)
        for field in self.obj._meta.many_to_many:
            if field.serialize:
                if (not self.fields or field.attname in self.fields) \
                    and not field.attname in self.excludes:
                    self.handle_m2m_field(field)

        if self.options.get('include_pk'):
            self.result['pk'] = self.obj.pk
        return self.result

    def handle_field(self, field):
        value = field._get_val_from_obj(self.obj)
        if is_protected_type(value) and not isinstance(field, models.DateTimeField):
            self.result[field.name] = value
        else:
            self.result[field.name] = field.value_to_string(self.obj)

    def handle_fk_field(self, field):
        related = getattr(self.obj, field.name)
        if related is not None:
            if self.use_natural_keys and hasattr(related, 'natural_key'):
                related = related.natural_key()
            else:
                if field.rel.field_name == related._meta.pk.name:
                    # Related to remote object via primary key
                    related = related._get_pk_val()
                else:
                    # Related to remote object via other field
                    related = smart_unicode(getattr(related, field.rel.field_name),
                         strings_only=True)
        self.result[field.name] = related

    def handle_m2m_field(self, field):
        if field.rel.through._meta.auto_created:
            if self.use_natural_keys and hasattr(field.rel.to, 'natural_key'):
                m2m_value = lambda value: value.natural_key()
            else:
                m2m_value = lambda value: smart_unicode(value._get_pk_val(), 
                    strings_only=True)
            self.result[field.name] = [m2m_value(related)
                for related in getattr(self.obj, field.name).iterator()]

class DjangoQuerySetSerializer(Serializer):
    def serialize(self):
        assert isinstance(self.obj, QuerySet)

        result = []
        for obj in self.obj:
            model_serializer = DjangoModelSerializer(obj, **self.options)
            result.append(model_serializer.serialize())
        return result

class MongoDocumentSerializer(Serializer):

    def serialize(self):
        assert isinstance(self.obj, mongoengine.document.BaseDocument)

        result = {}
        self.handle_document(self.obj, result)
        return result

    def handle_field(self, doc, field, scope):
        value = getattr(doc, field)
        if isinstance(value, bson.objectid.ObjectId):
            scope[field] = unicode(value)
        elif isinstance(value, mongoengine.document.BaseDocument):
            scope[field] = {}
            self.handle_document(value, scope[field])
        elif isinstance(value, list):
            scope[field] = []
            for item in value:
                scope[field] = []
                self.handle_list_field(value, scope[field])
        else:
            scope[field] = value

    def handle_list_field(self, items, scope=[]):
        for i, item in enumerate(items):
            if isinstance(item, mongoengine.document.BaseDocument):
                scope.append({})
                self.handle_document(item, scope[i])
            else:
                scope.append(item)

    def handle_document(self, doc, scope):
        for field in doc._fields:
            if (not self.fields or field in self.fields) and \
               not field in self.excludes:
                self.handle_field(doc, field, scope)

class MongoQuerySetSerializer(Serializer):

    def serialize(self):
        assert isinstance(self.obj, mongoengine.queryset.QuerySet)

        result = []
        for obj in self.obj:
            model_serializer = MongoDocumentSerializer(obj, **self.options)
            result.append(model_serializer.serialize())
        return result
