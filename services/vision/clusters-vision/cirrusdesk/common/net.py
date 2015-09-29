""" Commonly used network operations. """

from common.log import *
from common import hash
import requests
import tempfile

def download_url(url, filename):
    handle, tmp_filename = tempfile.mkstemp()
    exists = False
    r = requests.get(url, stream=True, timeout=5.0)
    print 'downloading %s -> %s' % (url, filename)

    if not r.ok:
        raise RuntimeError('Download url failed: %s' % (r))

    f = open(tmp_filename, 'wb')
    for chunk in r.iter_content(chunk_size=1024000):
        if chunk:  # filter out keep-alive new chunks
            f.write(chunk)
            sys.stdout.write('.')
            sys.stdout.flush()
    f.close()
    sys.stdout.write('\n')
    os.rename(tmp_filename, filename)
    return


def fetch_tarball(url, md5):
    handle, tmp_filename = tempfile.mkstemp()
    download_url(url, tmp_filename)
    computed_md5 = hash.md5sum(tmp_filename)
    CHECK_EQ(md5, computed_md5)
    return tmp_filename


def fetch_and_unarchive(archive_url, md5):
    tarball_file = fetch_tarball(archive_url, md5)
    LOG(FATAL, 'Not yet implemented')
    return
