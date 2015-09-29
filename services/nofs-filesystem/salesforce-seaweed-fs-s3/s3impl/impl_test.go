/*
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

package main

import (
	"bytes"
	"encoding/base64"
	"errors"
	"fmt"
	"github.com/tgulacsi/s3weed/s3impl/dirS3"
	"github.com/tgulacsi/s3weed/s3intf"
	"github.com/tgulacsi/s3weed/s3srv"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

var b64 = base64.StdEncoding

var backers = make([]s3intf.Storage, 0, 1)
var handlers = make([]http.Handler, 0, 1)
var serviceHost = "s3.test.org"
var Debug = false

func Test01ListBuckets(t *testing.T) {
	doReq(t, "GET", "/", nil, status200)
}

func Test02PutBucket(t *testing.T) {
	doReq(t, "PUT", "/test", nil, status200)
	doReq(t, "PUT", "/test2", nil, status200)
	doReq(t, "GET", "/", nil, func(r *httptest.ResponseRecorder) error {
		if err := status200(r); err != nil {
			return err
		}
		t.Logf("service list: %q", r.Body.Bytes())
		body := string(r.Body.Bytes())
		for _, bn := range []string{"test", "test2"} {
			i := strings.Index(body, "<Bucket><Name>"+bn+"</Name><CreationDate>")
			if i < 0 {
				return fmt.Errorf("bucket " + bn + " is missing from list after creation!")
			}
		}
		return nil
	})
	doReq(t, "DELETE", "/test2", nil, status200)
	doReq(t, "GET", "/", nil, func(r *httptest.ResponseRecorder) error {
		if err := status200(r); err != nil {
			return err
		}
		t.Logf("service list: %q", r.Body.Bytes())
		body := string(r.Body.Bytes())
		i := strings.Index(body, "<Bucket><Name>test</Name><CreationDate>")
		if i < 0 {
			return fmt.Errorf("bucket test is missing from list after creation!")
		}
		i = strings.Index(body, "<Bucket><Name>test2</Name><CreationDate>")
		if i >= 0 {
			return fmt.Errorf("bucket test2 is in the list after deletion!")
		}
		return nil
	})

}

func Test03PutObject(t *testing.T) {
	i := 0
	files := make([]string, 0, 100)
	filepath.Walk("/proc",
		func(path string, info os.FileInfo, err error) error {
			files = append(files, path)
			i++
			if i > 100 {
				return io.EOF
			}
			//log.Printf(files[len(files)-1])
			return nil
		})
	for _, fn := range files {
		doReq(t, "PUT", "/test"+fn, strings.NewReader(fn), status200)
		doReq(t, "GET", "/test"+fn, nil, func(r *httptest.ResponseRecorder) error {
			if err := status200(r); err != nil {
				return err
			}
			if !bytes.Equal(r.Body.Bytes(), []byte(fn)) {
				return fmt.Errorf("stored %q, got back %q!", fn, r.Body.Bytes())
			}
			return nil
		})
	}
	// list bucket
	doReq(t, "GET", "/test/?delimiter=/&prefix=proc", nil,
		func(r *httptest.ResponseRecorder) error {
			if err := status200(r); err != nil {
				return err
			}
			body := string(r.Body.Bytes())
			t.Logf("bucket list: %q", body)
			i := strings.Index(body, "<Key>"+files[0][1:]+"</Key>")
			if i < 0 {
				return errors.New("object (named " + files[0][1:] +
					") is missing from bucket 'test'")
			}
			return nil
		})
}

func Test99Delete(t *testing.T) {
	keyID := regexp.MustCompile("<Key>[^<]+</Key>")
	doReq(t, "GET", "/test/", nil, func(r *httptest.ResponseRecorder) error {
		if err := status200(r); err != nil {
			return err
		}
		for _, key := range keyID.FindAll(r.Body.Bytes(), -1) {
			key = key[5 : len(key)-6]
			t.Logf("deleting %s", key)
		}
		return nil
	})
}

func init() {
	s3srv.Debug = Debug
	s3intf.Debug = false
	backers = append(backers, dirS3.NewDirS3("/tmp"))

	for _, b := range backers {
		handlers = append(handlers, s3srv.NewService(serviceHost, b))
	}
}

type ResponseChecker func(r *httptest.ResponseRecorder) error

func doReq(t *testing.T, method, path string, body io.Reader, check ResponseChecker) {
	req, err := http.NewRequest(method, path, body)
	if err != nil {
		t.Fatalf("cannot create request: " + err.Error())
	}
	req.Host = serviceHost
	//req.URL.Host = req.Host
	var o s3intf.Owner
	for i, b := range backers {
		if o, err = b.GetOwner("test"); err != nil {
			t.Errorf("cannot get owner for test: %s", err)
			continue
		}
		if Debug {
			log.Printf("===")
			s3intf.Debug = Debug
		}
		bts := s3intf.GetBytesToSign(req, serviceHost)
		if Debug {
			log.Printf("bts: %q", bts)
			log.Printf("---")
		}
		t.Logf("owner: %s bts=%q", o, bts)
		actsign := b64.EncodeToString(o.CalcHash(bts))
		req.Header.Set("Authorization", "AWS test:"+actsign)

		rw := httptest.NewRecorder()
		handlers[i].ServeHTTP(rw, req)
		if check != nil {
			if err = check(rw); err != nil {
				t.Fatalf("bad response for %s %s: %s (body:%q)", method, path,
					err.Error(), rw.Body.Bytes())
			}
		}
	}
}

func status200(r *httptest.ResponseRecorder) error {
	if r.Code != 200 {
		return fmt.Errorf("bad response code: %d", r.Code)
	}
	return nil
}

//<?xml version="1.0" encoding="UTF-8"?>
//<Error>
//<Code>NoSuchKey</Code>
//<Message>The resource you requested does not exist</Message>
//<Resource>/mybucket/myfoto.jpg</Resource>
//<RequestId>4442587FB7D0A2F9</RequestId>
//</Error>
type AWSError struct {
	Error struct {
		Code, Message, Resource string
		RequestID               string `xml:"RequestId"`
	}
}
