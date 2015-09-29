from celery import task
from celery.utils.log import get_task_logger
from django.conf import settings

import os, subprocess, time


try:
    # Use settings if set
    ffmpeg = settings.FFMPEG
except AttributeError:
    # Assume ffmpeg is somewhere on standard path
    ffmpeg = 'ffmpeg'

log = get_task_logger(__name__)

@task
def cut_video(ifilename, ofilename, starttime, duration):
    args = [
        ffmpeg, 
        '-y',
        '-i', ifilename, 
        '-ss', str(starttime), 
        '-t', str(duration),
        '-acodec', 'copy',
        '-vcodec', 'copy',
        ofilename]
    log.info('Command %s', ' '.join(args))
    proc = subprocess.Popen(args, 
        stdout=subprocess.PIPE, 
        stderr=subprocess.PIPE)
    log.info('Started process PID: %d', proc.pid)
    stdout, stderr = proc.communicate()
    
    if proc.returncode != 0:
        log.error('Process %d terminated with return code %d', 
            proc.pid, proc.returncode)
        log.error(stderr)
    else:
        log.info('Process %d completed with return code %d', 
            proc.pid, proc.returncode)
    return proc.returncode, ifilename, ofilename
    
@task
def ffmpeg_version():
    try:
        proc = subprocess.Popen([ffmpeg], 
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout, stderr = proc.communicate()
    except (IOError, OSError), e:
        return '{} not found: {}'.format(ffmpeg, e)
    lines = stderr.split('\n')
    return lines[0]
