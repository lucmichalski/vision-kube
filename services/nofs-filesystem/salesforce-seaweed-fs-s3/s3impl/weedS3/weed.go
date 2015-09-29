/*
Package weedS3 program implements s3intf.Backer as a simple directory hierarchy
This is NOT for production use, only an experiment for testing the usability
of the s3intf API!

Copyright 2013 Tamás Gulácsi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package weedS3

import (
	"crypto/hmac"
	"crypto/md5"
	"crypto/sha1"
	"encoding/hex"
	"errors"
	"fmt"
	"hash"
	"io"
	//"log"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/cznic/kv"
	"github.com/tgulacsi/s3weed/s3impl/weedS3/weedutils"
	"github.com/tgulacsi/s3weed/s3intf"
	"github.com/tgulacsi/weed-client"
)

// kvOptions returns the usable kv.Options - according to cnic, reuse may be unsafe
func kvOptions() *kv.Options {
	return new(kv.Options)
}

type wBucket struct {
	filename string
	created  time.Time
	db       *kv.DB
}

type wOwner struct {
	dir     string
	buckets map[string]wBucket
	sync.Mutex
}

func (o wOwner) ID() string {
	return filepath.Base(o.dir)
}

// Name returns then name of this owner
func (o wOwner) Name() string {
	return filepath.Base(o.dir)
}

// GetHMAC returns a HMAC initialized with the secret key
func (o wOwner) GetHMAC(h func() hash.Hash) hash.Hash {
	return hmac.New(h, nil)
}

// Check checks the validity of the authorization
func (o wOwner) CalcHash(bytesToSign []byte) []byte {
	return s3intf.CalcHash(hmac.New(sha1.New, nil), bytesToSign)
}

type master struct {
	wm      weed.WeedClient // master weed node's URL
	baseDir string
	owners  map[string]wOwner
	sync.Mutex
}

// GetOwner returns the Owner for the accessKey - or an error
func (m master) GetOwner(accessKey string) (s3intf.Owner, error) {
	m.Lock()
	defer m.Unlock()
	if o, ok := m.owners[accessKey]; ok {
		return o, nil
	}
	return nil, errors.New("owner " + accessKey + " not found")
}

// NewWeedS3 stores everything in the given master Weed-FS node
// buckets are stored
func NewWeedS3(masterURL, dbdir string) (s3intf.Storage, error) {
	m := master{wm: weed.NewWeedClient(masterURL), baseDir: dbdir,
		owners: make(map[string]wOwner, 4)}
	dh, err := os.Open(dbdir)
	if err != nil {
		if _, ok := err.(*os.PathError); !ok {
			return nil, err
		}
		os.MkdirAll(dbdir, 0750)
	}
	defer dh.Close()
	var nm string
	err = weedutils.MapDirItems(dbdir,
		func(fi os.FileInfo) bool {
			return fi.Mode().IsDir()
		},
		func(fi os.FileInfo) error {
			nm = fi.Name()
			m.owners[nm], err = openOwner(filepath.Join(dbdir, nm))
			return err
		})
	return m, err
}

func openOwner(dir string) (o wOwner, err error) {
	o.dir = dir
	o.buckets = make(map[string]wBucket, 4)

	var k, nm string

	err = weedutils.MapDirItems(dir,
		func(fi os.FileInfo) bool {
			return fi.Mode().IsRegular() && strings.HasSuffix(fi.Name(), ".kv")
		},
		func(fi os.FileInfo) error {
			nm = fi.Name()
			k = nm[:len(nm)-3]
			o.buckets[k], err = openBucket(filepath.Join(dir, nm))
			return err
		})
	return
}

func openBucket(filename string) (b wBucket, err error) {
	fh, e := os.Open(filename)
	if e != nil {
		err = e
		return
	}
	fi, e := fh.Stat()
	fh.Close()
	if e != nil {
		err = e
		return
	}
	//b = bucket{filename: filename, created: fi.ModTime()}
	b.filename, b.created = filename, fi.ModTime()
	b.db, err = kv.Open(filename, kvOptions())
	if err != nil {
		err = fmt.Errorf("error opening buckets db %s: %s", filename, err)
		return
	}
	return
}

// ListBuckets list all buckets owned by the given owner
func (m master) ListBuckets(owner s3intf.Owner) ([]s3intf.Bucket, error) {
	m.Lock()
	o, ok := m.owners[owner.ID()]
	m.Unlock()
	if !ok {
		return nil, fmt.Errorf("unkown owner %s", owner.ID())
	}
	buckets := make([]s3intf.Bucket, len(o.buckets))
	i := 0
	for k, b := range o.buckets {
		buckets[i].Name = k
		buckets[i].Created = b.created
		i++
	}
	return buckets, nil
}

// CreateBucket creates a new bucket
func (m master) CreateBucket(owner s3intf.Owner, bucket string) error {
	m.Lock()
	defer m.Unlock()
	o, ok := m.owners[owner.ID()]
	if !ok {
		dir := filepath.Join(m.baseDir, owner.ID())
		err := os.MkdirAll(dir, 0750)
		if err != nil {
			return err
		}
		o = wOwner{dir: dir, buckets: make(map[string]wBucket, 1)}
		m.owners[owner.ID()] = o
		//} else if o.buckets == nil {
		//	o.buckets = make(map[string]wBucket, 1)
	}
	o.Lock()
	defer o.Unlock()
	_, ok = o.buckets[bucket]
	if ok {
		return nil //AlreadyExists ?
	}
	b := wBucket{filename: filepath.Join(o.dir, bucket+".kv"), created: time.Now()}
	var err error
	if b.db, err = kv.Create(b.filename, kvOptions()); err != nil {
		return err
	}
	o.buckets[bucket] = b
	return nil
}

// CheckBucket returns whether the owner has a bucket named as given
func (m master) CheckBucket(owner s3intf.Owner, bucket string) bool {
	m.Lock()
	defer m.Unlock()
	if o, ok := m.owners[owner.ID()]; ok {
		o.Lock()
		_, ok = o.buckets[bucket]
		o.Unlock()
		return ok
	}
	return false
}

// DelBucket deletes a bucket
func (m master) DelBucket(owner s3intf.Owner, bucket string) error {
	m.Lock()
	defer m.Unlock()
	o, ok := m.owners[owner.ID()]
	if !ok {
		return fmt.Errorf("unknown owner %s", owner.ID())
	}
	o.Lock()
	defer o.Unlock()
	b, ok := o.buckets[bucket]
	if !ok {
		return fmt.Errorf("bucket %s not exists!", bucket)
	}
	if k, v, err := b.db.First(); err != nil {
		return err
	} else if k != nil || v != nil {
		return errors.New("cannot delete non-empty bucket")
	}
	b.db.Close()
	b.db = nil
	delete(o.buckets, bucket)
	return os.Remove(b.filename)
}

// List lists a bucket, all objects Key starts with prefix, delimiter segments
// Key, thus the returned commonprefixes (think a generalized filepath
// structure, where / is the delimiter, a commonprefix is a subdir)
func (m master) List(owner s3intf.Owner, bucket, prefix, delimiter, marker string,
	limit, skip int) (
	objects []s3intf.Object, commonprefixes []string,
	truncated bool, err error) {

	m.Lock()
	o, ok := m.owners[owner.ID()]
	if !ok {
		m.Unlock()
		err = fmt.Errorf("unknown owner %s", owner.ID())
		return
	}
	o.Lock()
	b, ok := o.buckets[bucket]
	o.Unlock()
	m.Unlock()
	if !ok {
		err = fmt.Errorf("unknown bucket %s", bucket)
		return
	}

	err = nil
	enum, e := b.db.SeekFirst()
	if e != nil {
		if e == io.EOF { //empty
			return
		}
		err = fmt.Errorf("error getting first: %s", e)
		return
	}
	var (
		key, val []byte
		vi       = new(weedutils.ValInfo)
		etag     string
	)
	objects = make([]s3intf.Object, 0, 64)
	f := s3intf.NewListFilter(prefix, delimiter, marker, limit, skip)
	for {
		if key, val, e = enum.Next(); e != nil {
			if e == io.EOF {
				break
			}
			err = fmt.Errorf("error seeking next: %s", e)
			return
		}
		//log.Printf("key=%q", key)
		if ok, e = f.Check(string(key)); e != nil {
			if e == io.EOF {
				break
			}
			err = fmt.Errorf("error checking %s: %s", key, e)
			return
		} else if ok {
			if err = vi.Decode(val); err != nil {
				return
			}
			if vi.MD5 != nil && len(vi.MD5) == 16 {
				etag = hex.EncodeToString(vi.MD5)
			} else {
				etag = ""
			}
			objects = append(objects,
				s3intf.Object{Key: string(key), Owner: owner,
					ETag: etag, LastModified: vi.Created, Size: vi.Size})
		}
	}
	commonprefixes, truncated = f.Result()
	return
}

// Put puts a file as a new object into the bucket
func (m master) Put(owner s3intf.Owner, bucket, object, filename, media string,
	body io.Reader, size int64, md5hash []byte) (
	err error) {

	m.Lock()
	o, ok := m.owners[owner.ID()]
	m.Unlock()
	if !ok {
		err = errors.New("cannot find owner " + owner.ID())
		return
	}
	o.Lock()
	b, ok := o.buckets[bucket]
	o.Unlock()
	if !ok {
		err = errors.New("cannot find bucket " + bucket)
		return
	}

	if err = b.db.BeginTransaction(); err != nil {
		return fmt.Errorf("cannot start transaction: %s", err)
	}
	defer func() {
		if err != nil {
			b.db.Rollback()
		}
	}()
	//upload
	fid, publicURL, err := m.wm.AssignFid()
	if err != nil {
		err = fmt.Errorf("error getting fid: %s", err)
		return
	}
	vi := weedutils.ValInfo{Filename: filename, ContentType: media,
		Fid: fid, Created: time.Now(), Size: size, MD5: md5hash}
	val, err := vi.Encode(nil)
	if err != nil {
		err = fmt.Errorf("error serializing %v: %s", vi, err)
		return
	}
	if err = b.db.Set([]byte(object), val); err != nil {
		err = fmt.Errorf("error storing key in db: %s", err)
		return
	}
	//log.Printf("filename=%q", filename)
	var hsh hash.Hash
	if vi.MD5 == nil {
		hsh = md5.New()
		body = io.TeeReader(body, hsh)
	}
	if _, err = m.wm.UploadAssigned(fid, publicURL, filename, media, body); err != nil {
		b.db.Rollback()
		err = fmt.Errorf("error uploading to %s: %s", fid, err)
		return
	}
	if vi.MD5 == nil {
		vi.MD5 = hsh.Sum(nil)
		if val, err = vi.Encode(nil); err != nil {
			err = fmt.Errorf("error serializing %v: %s", vi, err)
			return
		}
		if err = b.db.Set([]byte(object), val); err != nil {
			err = fmt.Errorf("error storing key in db: %s", err)
			return
		}
	}

	//log.Printf("uploading %s [%d] resulted in %s", filename, size, resp)
	err = nil
	return b.db.Commit()
}

// Get retrieves an object from the bucket
func (m master) Get(owner s3intf.Owner, bucket, object string) (
	filename, media string, body io.ReadCloser, size int64, md5 []byte, err error) {

	m.Lock()
	o, ok := m.owners[owner.ID()]
	m.Unlock()
	if !ok {
		err = errors.New("cannot find owner " + owner.ID())
		return
	}
	o.Lock()
	b, ok := o.buckets[bucket]
	o.Unlock()
	if !ok {
		err = errors.New("cannot find bucket " + bucket)
		return
	}

	val, e := b.db.Get(nil, []byte(object))
	if e != nil {
		err = fmt.Errorf("cannot get %s object: %s", object, e)
		return
	}
	if val == nil {
		err = s3intf.NotFound
		return
	}
	vi := new(weedutils.ValInfo)
	if err = vi.Decode(val); err != nil {
		err = fmt.Errorf("error deserializing %s: %s", val, err)
		return
	}
	filename, media, size, md5 = vi.Filename, vi.ContentType, vi.Size, vi.MD5

	body, err = m.wm.Download(vi.Fid)
	return
}

// Del deletes the object from the bucket
func (m master) Del(owner s3intf.Owner, bucket, object string) (err error) {
	m.Lock()
	o, ok := m.owners[owner.ID()]
	m.Unlock()
	if !ok {
		return errors.New("cannot find owner " + owner.ID())
	}
	o.Lock()
	b, ok := o.buckets[bucket]
	o.Unlock()
	if !ok {
		return errors.New("cannot find bucket " + bucket)
	}

	if err = b.db.BeginTransaction(); err != nil {
		return fmt.Errorf("cannot start transaction: %s", err)
	}
	defer func() {
		if err != nil {
			b.db.Rollback()
		}
	}()

	val, e := b.db.Extract(nil, []byte(object))
	if e != nil {
		err = fmt.Errorf("cannot get %s object: %s", object, e)
		return
	}
	if val == nil {
		err = s3intf.NotFound
		return
	}
	vi := new(weedutils.ValInfo)
	if err = vi.Decode(val); err != nil {
		err = fmt.Errorf("error deserializing %s: %s", val, err)
		return
	}
	if err = m.wm.Delete(vi.Fid); err != nil {
		return
	}
	err = nil
	return b.db.Commit()
}
