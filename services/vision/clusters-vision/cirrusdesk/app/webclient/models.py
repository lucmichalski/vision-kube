from django.db import models
from django.contrib.auth.models import User

class IamCredentials(models.Model):
    user = models.OneToOneField(User)
    iam_key_id = models.CharField(max_length=20)
    iam_key_secret = models.CharField(max_length=40)