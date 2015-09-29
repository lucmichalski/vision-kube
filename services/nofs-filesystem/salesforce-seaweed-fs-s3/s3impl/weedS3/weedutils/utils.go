/*
Package weedutils defines some helper functions for kv used in s3impl/weedS3

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
package weedutils

import (
	"bytes"
	"encoding/gob"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/cznic/kv"
)

var kvOptions = new(kv.Options)

// OpenAllDb opens all files with the given suffix in the given dir
func OpenAllDb(dir, suffix string) (<-chan *kv.DB, <-chan error) {
	dest := make(chan *kv.DB)
	errch := make(chan error, 1)
	var (
		db  *kv.DB
		err error
		fn  string
	)
	go func() {
		if err = MapDirItems(dir,
			func(fi os.FileInfo) bool {
				return fi.Mode().IsRegular() && (suffix == "" || strings.HasSuffix(fi.Name(), suffix))
			},
			func(fi os.FileInfo) error {
				fn = filepath.Join(dir, fi.Name())
				if db, err = kv.Open(fn, kvOptions); err != nil {
					return err
				}
				dest <- db
				return nil
			}); err != nil {
			errch <- err
		}
		close(dest)
		close(errch)
	}()
	return dest, errch
}

// MapDirItems calls todo for every item in dir for which check returns true
func MapDirItems(dir string, check func(os.FileInfo) bool, todo func(os.FileInfo) error) (err error) {
	var (
		dh  *os.File
		fi  os.FileInfo
		fis []os.FileInfo
	)
	if dh, err = os.Open(dir); err != nil {
		return
	}
	defer dh.Close()
	for {
		if fis, err = dh.Readdir(1000); err != nil {
			if err == io.EOF {
				break
			}
			return
		}
		for _, fi = range fis {
			if check(fi) {
				if err = todo(fi); err != nil {
					return
				}
			}
		}
	}
	return nil
}

// ReadDirItems sends the fileinfo of every item in dir for which check returns true
func ReadDirItems(dir string, check func(os.FileInfo) bool, dest chan<- os.FileInfo) (err error) {
	defer close(dest)
	return MapDirItems(dir, check, func(fi os.FileInfo) error {
		dest <- fi
		return nil
	})
}

// ReadDirNames sends the full filename of every item in dir for which check returns true
func ReadDirNames(dir string, check func(os.FileInfo) bool, dest chan<- string) (err error) {
	defer close(dest)
	return MapDirItems(dir, check, func(fi os.FileInfo) error {
		dest <- filepath.Join(dir, fi.Name())
		return nil
	})
}

// ValInfo contains the required info about a stored file
type ValInfo struct {
	Filename    string `json:"filename"`
	ContentType string `json:"content-type"`
	//Fid is the file-id
	Fid     string    `json:"fid"`
	Created time.Time `json:"created"`
	Size    int64     `json:"size"`
	MD5     []byte    `json:"md5"`
}

// Decode decodes into the struct from bytes
func (v *ValInfo) Decode(val []byte) error {
	return gob.NewDecoder(bytes.NewReader(val)).Decode(v)
}

// Encode encodes the ValInfo's values into dst and returns the resulting slice.
// dst can be nil
func (v ValInfo) Encode(dst []byte) ([]byte, error) {
	if dst == nil {
		dst = make([]byte, 0, len(v.Filename)+len(v.ContentType)+len(v.Fid)+16+8+8+8)
	}
	buf := bytes.NewBuffer(dst)
	err := gob.NewEncoder(buf).Encode(v)
	return buf.Bytes(), err
}
