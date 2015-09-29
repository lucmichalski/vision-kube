# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django.contrib.auth.models import User
from django.db import models
from datetime import datetime

class VideoStats(models.Model):
    user = models.ForeignKey(User, related_name="stats")
    videoUri = models.CharField(max_length=50)
    favorite = models.BooleanField(default=False)
    likes = models.BooleanField(default=False)
    dislikes = models.BooleanField(default=False)
    views = models.IntegerField(default=0)
    downloads = models.IntegerField(default=0)
    lastViewed = models.DateTimeField(blank=True, null=True)
    
    @classmethod
    def summary_for_video(cls, video_uri):
        from django.db.models import Count, Sum, Max
        stats = cls.objects.filter(videoUri=video_uri)
        if stats.count() > 0:
            return stats.aggregate(
                favorites=Sum('favorite'),
                likes=Sum('likes'),
                dislikes=Sum('dislikes'),
                views=Sum('views'),
                downloads=Sum('downloads'),
                lastViewed=Max('lastViewed'))
        return {
            'favorites': 0, 
            'likes': 0, 
            'dislikes': 0, 
            'views': 0, 
            'downloads': 0,
            'lastViewed': None
        }
    
    @classmethod 
    def increment_view_count(cls, user, video_uri):
        stats, created = cls.objects.get_or_create(user=user, videoUri=video_uri)
        stats.views += 1
        stats.lastViewed = datetime.now()
        stats.save()
        
    @classmethod
    def increment_download_count(cls, user, video_uri):
        stats, created = cls.objects.get_or_create(user=user, videoUri=video_uri)
        stats.downloads += 1
        stats.save()
        
    @classmethod
    def fetch_popular(cls, order_by, first=0, count=None):
        from django.db.models import Sum
        results = cls.objects.values('videoUri') \
            .annotate(amount=Sum(order_by)) \
            .order_by('-amount')
        return results[first:count]
     