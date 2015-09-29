# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django.db import models

class UserImage(models.Model):
    """
    Model for a user uploaded image
    """
    image = models.ImageField(upload_to='user/%Y/%m/%d')
    uploaded = models.DateTimeField(auto_now_add=True)