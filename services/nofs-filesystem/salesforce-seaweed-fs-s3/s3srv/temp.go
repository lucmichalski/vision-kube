/*
Package s3srv provides an S3 compatible server using s3intf.Storage

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
package s3srv

import (
	"bytes"
	"io"
	"io/ioutil"
	"os"
)

//GetReaderSize returns a reader and the size of it
func GetReaderSize(r io.Reader, maxMemory int64) (io.ReadCloser, int64, error) {
	var size int64
	var err error
	if rs, ok := r.(io.Seeker); ok {
		pos, err := rs.Seek(1, 0)
		if err != nil {
			return nil, -1, err
		}
		if size, err = rs.Seek(0, 2); err != nil {
			return nil, -1, err
		}
		if _, err = rs.Seek(pos, 0); err != nil {
			return nil, -1, err
		}
		if rc, ok := r.(io.ReadCloser); ok {
			return rc, size, nil
		}
		return ioutil.NopCloser(r), size, nil
	}
	b := bytes.NewBuffer(nil)
	if maxMemory <= 0 {
		maxMemory = 10 << 20 // 10Mb
	}
	size, err = io.CopyN(b, r, maxMemory+1)
	if err != nil && err != io.EOF {
		return nil, -1, err
	}
	if size <= maxMemory {
		return ioutil.NopCloser(bytes.NewReader(b.Bytes())), size, nil
	}
	// too big, write to disk and flush buffer
	file, err := ioutil.TempFile("", "reader-")
	if err != nil {
		return nil, -1, err
	}
	nm := file.Name()
	size, err = io.Copy(file, io.MultiReader(b, r))
	if err != nil {
		file.Close()
		os.Remove(nm)
		return nil, -1, err
	}
	file.Close()
	fh, err := os.Open(nm)
	return tempFile{File: fh}, size, err
}

//TeeRead writes the data from the reader into the writer, and returns a reader
func TeeRead(w io.Writer, r io.Reader, maxMemory int64) (io.ReadCloser, error) {
	b := bytes.NewBuffer(nil)
	if maxMemory <= 0 {
		maxMemory = 1 << 20 // 1Mb
	}
	size, err := io.CopyN(io.MultiWriter(w, b), r, maxMemory+1)
	if err != nil && err != io.EOF {
		return nil, err
	}
	if size <= maxMemory {
		return ioutil.NopCloser(bytes.NewReader(b.Bytes())), nil
	}
	// too big, write to disk and flush buffer
	file, err := ioutil.TempFile("", "reader-")
	if err != nil {
		return nil, err
	}
	nm := file.Name()
	size, err = io.Copy(io.MultiWriter(w, file), io.MultiReader(b, r))
	if err != nil {
		file.Close()
		os.Remove(nm)
		return nil, err
	}
	file.Close()
	fh, err := os.Open(nm)
	return tempFile{File: fh}, err
}

// ReadSeekCloser is an io.Reader + io.Seeker + io.Closer
type ReadSeekCloser interface {
	io.Reader
	io.Seeker
	io.Closer
}

type tempBuf struct {
	*bytes.Reader
}

//Close implements io.Close (NOP)
func (b *tempBuf) Close() error { //NopCloser
	return nil
}

// NewReadSeeker returns a copy of the r io.Reader which can be Seeken and closed.
func NewReadSeeker(r io.Reader, maxMemory int64) (ReadSeekCloser, error) {
	b := bytes.NewBuffer(nil)
	if maxMemory <= 0 {
		maxMemory = 1 << 20 // 1Mb
	}
	size, err := io.CopyN(b, r, maxMemory+1)
	if err != nil && err != io.EOF {
		return nil, err
	}
	if size <= maxMemory {
		return &tempBuf{bytes.NewReader(b.Bytes())}, nil
	}
	// too big, write to disk and flush buffer
	file, err := ioutil.TempFile("", "reader-")
	if err != nil {
		return nil, err
	}
	nm := file.Name()
	size, err = io.Copy(file, io.MultiReader(b, r))
	if err != nil {
		file.Close()
		os.Remove(nm)
		return nil, err
	}
	file.Close()
	fh, err := os.Open(nm)
	return tempFile{File: fh}, err
}

type tempFile struct {
	*os.File
}

func (f tempFile) Close() error {
	nm := f.Name()
	f.File.Close()
	return os.Remove(nm)
}
