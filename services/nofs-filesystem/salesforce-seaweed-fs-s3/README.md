# S3weed
S3-like proxy for Weed-FS

# Status

*Alpha*
It needs some more work, test cases, and so on, and maybe the interface will change too!

[![Build Status](https://travis-ci.org/tgulacsi/s3weed.png)](https://travis-ci.org/tgulacsi/s3weed)

# Goals

Provide a proxy for Weed-FS (and possibly for other stores) which makes
it usable with a general S3 client.

# Code structure
The interface definition is under `s3intf`:

* `Storage` is the interface for the storage (store and retrieve),
* `Owner` is the object's owner (authentication)

`s3srv.Service` is an implementation of the HTTP server which acts as an S3 server;
it requires the host:port to listen on, and an implementation of `s3intf.Storage`.

See [s3impl/main.go](s3impl/main.go).

At the moment, there are two implementations are in this repo of `s3intf.Storage`:

## `s3impl/dirS3`
is a very-very simple implementation which stores everything
in files under the given root. The first level of subdirectories are the `Owner`s,
the next are the buckets, and under this are the files -
another level of subdirectories should be implemented for smaller directory
sizes, if this would be a proper implementation, not just a toy!

## `s3impl/weedS3`
is an implementation which stores metadata
(object name - file id (and file name, size and md5)) locally in
`basedir/owner/bucket.kv` files (using [kv](https://github.com/cznic/kv) for database),
and uses [Weed-FS](https://code.google.com/p/weed-fs) for file data storage.

This does not have any authentication (**uses empty password**) ATM.

### Dump
Some functions are lift up to [weedutils](s3impl/weedS3/weedutils) to be able
to dump the metadata:

    s3impl -db=/tmp/weedS3 dump

Example:

    find /tmp/weedS3 -ls
```
1684908    0 drwxr-xr-x   3 gthomas  gthomas        60 júl 31 17:29 /tmp/weedS3
1775871    0 drwxr-xr-x   2 gthomas  gthomas        80 aug  4 08:13 /tmp/weedS3/AAA
4036548    0 -rw-r--r--   1 gthomas  root            0 aug  3 21:34 /tmp/weedS3/AAA/.ce7e88cc0bb1b088176800ac97526bfa6fb01ec8
4036546    4 -rw-r--r--   1 gthomas  root         2688 aug  3 21:34 /tmp/weedS3/AAA/proba.kv
```

    ./s3impl/s3impl -db=/tmp/weedS3 dump
Output:

```javascript
[{"owner": "AAA", "buckets": [{"name": "/tmp/weedS3/AAA/proba.kv", "records": [{"object": "LICENSE", "value": {"filename":"","content-type":"binary/octet-stream","fid":"4,2722ed69c86a","created":"2013-08-03T07:36:38.108498712+02:00","size":1289,"md5":null}
},
{"object": "README.md", "value": {"filename":"","content-type":"binary/octet-stream","fid":"7,2723a1d7810f","created":"2013-08-03T10:08:52.744667642+02:00","size":830,"md5":null}
},
{"object": "proc/asound/card0/oss_mixer", "value": {"filename":"d41d8cd98f00b204e9800998ecf8427e","content-type":"binary/octet-stream","fid":"3,c35443c5be00","created":"2013-08-04T08:27:05.607300512+02:00","size":830,"md5":"1B2M2Y8AsgTpgAmY7PhCfg=="}
},
{"object": "usr/share/doc/libaudiofile1/ACKNOWLEDGEMENTS", "value": {"filename":"ee367eb080d6c52af0de2c2f2996dcf9","content-type":"binary/octet-stream","fid":"4,c3e3e53d845c","created":"2013-08-04T08:30:00.448380137+02:00","size":631,"md5":"7jZ+sIDWxSrw3iwvKZbc+Q=="}
},
{"object": "usr/share/doc/libaudiofile1/NEWS.gz", "value": {"filename":"c3aabdd7b37ca18993a3129666be3902","content-type":"binary/octet-stream","fid":"5,c3de130a5414","created":"2013-08-04T08:29:59.367915855+02:00","size":835,"md5":"w6q917N8oYmToxKWZr45Ag=="}
},
{"object": "usr/share/doc/libaudiofile1/NOTES", "value": {"filename":"254d7ea4d66701481e7064464c1f7af5","content-type":"binary/octet-stream","fid":"3,c3e222956fd1","created":"2013-08-04T08:30:00.235051529+02:00","size":1371,"md5":"JU1+pNZnAUgecGRGTB969Q=="}
},
]},
]},
]
```

# Usage

    go build github.com/tgulacsi/s3weed/s3impl
    mkdir -p /tmp/weedS3/anowner
    s3impl -db=/tmp/weedS3 -weed=http://localhost:9333 -http=s3.localhost:80

  Some testing with [s3cmd](http://s3tools.org/s3cmd) is in
  [s3cmd-test.sh](s3cmd-test.sh)

## Caveeats
I've tested with s3cmd, but that seems to implement only the DNS-named buckets
(you set the bucket name in the server name: testbucket.s3.localhost).
Thus you need to resolve the "bucketname".fqdn to fqdn - add

    127.0.0.1   localhost   s3.localhost    testbucket.s3.localhost

to your /etc/hosts file, for example.

I couldn't find (yet) why no filename is transferred with s3cmd.

# TODO

  * authorization - `.pwd` file under `basedir/owner`? Or in `basedir/auth.kv`?
  * calculate how much overhead does this `kv` database imposes
  * testing with real-world S3 clients
  * tests
  * more tests

# Contributing

Pull requests are welcomed!
Tests are needed! (See and extend s3impl/impl_test.go)

# Credits
  * [Chris Lu](http://code.google.com/u/114794436895060361581/)

# License

BSD 2 clause - see LICENSE for more details
