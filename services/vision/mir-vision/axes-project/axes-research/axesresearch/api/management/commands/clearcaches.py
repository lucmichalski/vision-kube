from django.core.management.base import BaseCommand
from axesresearch.api.backend import CachedLimasService

class Command(BaseCommand):
    help = "Clear mongodb caches for the limas backend"
    
    def handle(self, *args, **options):
        limas = CachedLimasService()
        caches = limas.clear_caches()
        self.stdout.write('Cleared caches:')
        for cache in caches:
            self.stdout.write("  " + cache)
