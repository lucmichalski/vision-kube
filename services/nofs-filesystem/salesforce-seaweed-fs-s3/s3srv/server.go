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
	"github.com/tgulacsi/s3weed/s3intf"

	"bufio"
	"bytes"
	"crypto"
	_ "crypto/md5" // for crypto.MD5
	"encoding/hex"
	"fmt"
	"io"
	"log"
	"mime"
	"net/http"
	"net/textproto"
	"strconv"
	"strings"
)

// S3Date is a format for S3
const S3Date = "2006-01-02T15:04:05.007Z" //%Y-%m-%dT%H:%M:%S.000Z"

// Debug prints
var Debug bool

type service struct {
	fqdn string
	s3intf.Storage
}

// NewService returns a new service
func NewService(fqdn string, provider s3intf.Storage) *service {
	log.Printf("Service on %q with %s", fqdn, provider)
	return &service{fqdn: fqdn, Storage: provider}
}

func (s *service) Host() string {
	return s.fqdn
}

func (host *service) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if Debug {
		log.Printf("%s.ServeHTTP %s %s", host.fqdn, r.Method, r.RequestURI)
	}
	if r.RequestURI == "*" || r.Host == "" || r.URL == nil || r.URL.Path == "" {
		writeError(w, &HTTPError{Code: 1, HTTPCode: http.StatusBadRequest,
			Message: "bad URI"})
		return
	}
	if stripPort(host.fqdn) == stripPort(r.Host) { //Service level
		log.Printf("service level request, path: %s", r.URL.Path)
		if r.URL.Path != "/" {
			segments := strings.SplitN(r.URL.Path[1:], "/", 2)
			bucketHandler{Name: segments[0], Service: host}.ServeHTTP(w, r)
			return
		}
		if r.Method != "GET" {
			writeError(w, &HTTPError{Code: 2, HTTPCode: http.StatusBadRequest,
				Message: "only GET allowed at service level"})
			return
		}
		host.serviceGet(w, r)
		return
	}

	bn := r.Host[:len(stripPort(r.Host))-len(stripPort(host.fqdn))-1]
	bucketHandler{Name: bn, Service: host, VirtualHost: true}.ServeHTTP(w, r)
}

type bucketHandler struct {
	Name        string
	Service     *service
	VirtualHost bool
}

func (bucket bucketHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if Debug {
		log.Printf("bucket %s", bucket.Name)
	}
	path := r.URL.Path
	if !bucket.VirtualHost {
		//log.Printf("bucket.Name=%s path=%s", bucket.Name, path)
		path = path[len(bucket.Name)+1:]
	}
	//log.Printf("path=%s", path)
	if !(path == "" || path == "/") || r.Method == "POST" {
		if path[0] == byte('/') {
			path = path[1:]
		}
		objectHandler{Bucket: bucket, object: path}.ServeHTTP(w, r)
		return
	}
	switch r.Method {
	case "DELETE":
		bucket.del(w, r)
	case "GET":
		bucket.list(w, r)
	case "HEAD":
		bucket.check(w, r)
	case "PUT":
		bucket.put(w, r)
	default:
		writeError(w, &HTTPError{Code: 3, HTTPCode: http.StatusBadRequest,
			Message:  "only DELETE, GET, PUT and POST allowed at bucket level",
			Resource: "/" + bucket.Name})
	}
}

type objectHandler struct {
	Bucket bucketHandler
	object string
}

func (obj objectHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if Debug {
		log.Printf("object %s", obj)
	}
	switch r.Method {
	case "DELETE":
		obj.del(w, r)
	case "GET":
		obj.get(w, r)
	case "PUT", "POST":
		obj.put(w, r)
	default:
		writeError(w, &HTTPError{Code: 4, HTTPCode: http.StatusBadRequest,
			Message:  "only DELETE, GET, PUT and POST allowed at object level",
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
	}
}

func writeError(w http.ResponseWriter, err error) {
	w.Header().Set("Connection", "close")
	w.Header().Set("Content-Type", "text/xml")
	var (
		he *HTTPError
		ok bool
	)
	if he, ok = err.(*HTTPError); !ok {
		he.Code, he.Message = 1, err.Error()
	}
	if he.HTTPCode <= 0 {
		he.HTTPCode = http.StatusInternalServerError
	}
	w.WriteHeader(he.HTTPCode)
	io.WriteString(w, `<?xml version="1.0" encoding="UTF-8"?><Error><Code>`)
	w.Write(strconv.AppendInt(make([]byte, 0, 3), int64(he.Code), 10))
	io.WriteString(w, `</Code><Message>`)
	io.WriteString(w, he.Message)
	io.WriteString(w, "</Message><Resource>")
	io.WriteString(w, he.Resource)
	io.WriteString(w, "</Resource></Error>")
}

// HTTPError is an error which contains the Resource and HTTPCode, too
type HTTPError struct {
	Code     int
	HTTPCode int
	Message  string
	Resource string
}

// Error implements error.Error (returns the string representation)
func (he *HTTPError) Error() string {
	return fmt.Sprintf("(%d) %s @%s", he.Code, he.Message, he.Resource)
}

// ValidBucketName returns whether name is a valid bucket name.
// Here are the rules, from:
// http://docs.amazonwebservices.com/AmazonS3/2006-03-01/dev/BucketRestrictions.html
//
// Can contain lowercase letters, numbers, periods (.), underscores (_),
// and dashes (-). You can use uppercase letters for buckets only in the
// US Standard region.
//
// Must start with a number or letter
//
// Must be between 3 and 255 characters long
//
// There's one extra rule (Must not be formatted as an IP address (e.g., 192.168.5.4)
// but the real S3 server does not seem to check that rule, so we will not
// check it either.
//
func ValidBucketName(name string) bool {
	if len(name) < 3 || len(name) > 255 {
		return false
	}
	r := name[0]
	if !(r >= '0' && r <= '9' || r >= 'a' && r <= 'z') {
		return false
	}
	for _, r := range name {
		switch {
		case r >= '0' && r <= '9':
		case r >= 'a' && r <= 'z':
		case r == '_' || r == '-':
		case r == '.':
		default:
			return false
		}
	}
	return true
}

//This implementation of the GET operation returns a list of all buckets owned by the authenticated sender of the request.
func (s *service) serviceGet(w http.ResponseWriter, r *http.Request) {
	owner, err := s3intf.GetOwner(s, r, s.fqdn)
	log.Printf("%#v.serviceGet owner=%s err=%s", s, owner.ID(), err)
	if err != nil {
		writeError(w, &HTTPError{Code: 5, Message: "error getting owner: " + err.Error()})
		return
	} else if owner == nil {
		writeError(w, &HTTPError{Code: 6, HTTPCode: 403, Message: "no owner"})
		return
	}
	buckets, err := s.ListBuckets(owner)
	if err != nil {
		writeError(w, &HTTPError{Code: 7, Message: err.Error()})
		return
	}
	w.Header().Set("Content-Type", "text/xml")
	bw := bufio.NewWriter(w)
	bw.WriteString(`<?xml version="1.0" encoding="UTF-8"?>
<ListAllMyBucketsResult xmlns="http://doc.s3.amazonaws.com/2006-03-01">
  <Owner><ID>` + owner.ID() + "</ID><DisplayName>" + owner.Name() + "</DisplayName></Owner><Buckets>")
	for _, bucket := range buckets {
		bw.WriteString("<Bucket><Name>" + bucket.Name + "</Name>")
		bw.WriteString("<CreationDate>" + bucket.Created.Format(S3Date) +
			"</CreationDate></Bucket>")
	}
	bw.WriteString("</Buckets></ListAllMyBucketsResult>")
	bw.Flush()
}

//This implementation of the DELETE operation deletes the bucket named in the URI.
//All objects (including all object versions and Delete Markers) in the bucket
//must be deleted before the bucket itself can be deleted.
func (bucket bucketHandler) del(w http.ResponseWriter, r *http.Request) {
	owner, err := s3intf.GetOwner(bucket.Service, r, bucket.Service.fqdn)
	if err != nil {
		writeError(w, &HTTPError{Code: 8, Message: "error getting owner: " + err.Error()})
		return
	}
	if err := bucket.Service.DelBucket(owner, bucket.Name); err != nil {
		if err == s3intf.NotFound {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		writeError(w, &HTTPError{Code: 9, Message: err.Error(), Resource: "/" + bucket.Name})
		return
	}
}

//This implementation of the GET operation returns some or all (up to 1000)
//of the objects in a bucket.
//You can use the request parameters as selection criteria to return a subset
//of the objects in a bucket.
//
//To use this implementation of the operation, you must have READ access to the bucket.
func (bucket bucketHandler) list(w http.ResponseWriter, r *http.Request) {
	err := r.ParseForm()
	if err != nil {
		writeError(w, &HTTPError{Code: 10, HTTPCode: http.StatusBadRequest,
			Message:  "cannot parse form values: " + err.Error(),
			Resource: "/" + bucket.Name})
		return
	}
	delimiter := r.Form.Get("delimiter")
	marker := r.Form.Get("marker")
	limit := 1000
	maxkeys := r.Form.Get("max-keys")
	if maxkeys != "" {
		if limit, err = strconv.Atoi(maxkeys); err != nil {
			writeError(w, &HTTPError{Code: 11, HTTPCode: http.StatusBadRequest,
				Message:  "cannot parse max-keys value: " + err.Error(),
				Resource: "/" + bucket.Name})
			return
		}
	}
	skip := 0
	skipkeys := r.Form.Get("skip-keys")
	if skipkeys != "" {
		if skip, err = strconv.Atoi(skipkeys); err != nil {
			writeError(w, &HTTPError{Code: 12, HTTPCode: http.StatusBadRequest,
				Message:  "cannot parse skip-keys value: " + err.Error(),
				Resource: "/" + bucket.Name})
			return
		}
	}
	prefix := r.Form.Get("prefix")

	owner, err := s3intf.GetOwner(bucket.Service, r, bucket.Service.fqdn)
	if err != nil {
		writeError(w, &HTTPError{Code: 13,
			Message:  "error getting owner: " + err.Error(),
			Resource: "/" + bucket.Name})
		return
	}
	if Debug {
		log.Printf("listing bucket %s/%s", owner.ID(), bucket.Name)
	}
	objects, commonprefixes, truncated, err := bucket.Service.List(owner,
		bucket.Name, prefix, delimiter, marker, limit, skip)
	if err != nil {
		log.Printf("error with bucket.Service.List(%s, %s, %q, %q, %q, %d, %d): %s",
			owner.ID(), bucket.Name, prefix, delimiter, marker, limit, skip, err)
		if err == s3intf.NotFound {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		writeError(w, &HTTPError{Code: 14, Resource: "/" + bucket.Name,
			Message: "error getting list: " + err.Error()})
		return
	}
	isTruncated := "false"
	if truncated {
		isTruncated = "true"
	}

	w.Header().Set("Content-Type", "text/xml")
	var (
		logw *bytes.Buffer
		bw   *bufio.Writer
		etag string
	)
	if Debug {
		logw = bytes.NewBuffer(nil)
		bw = bufio.NewWriter(io.MultiWriter(w, logw))
	} else {
		bw = bufio.NewWriter(w)
	}
	bw.WriteString(`<?xml version="1.0" encoding="UTF-8"?>
<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/"><Name>` +
		bucket.Name + "</Name><Prefix>" + prefix + "</Prefix><Marker>" + marker +
		"</Marker><MaxKeys>" + strconv.Itoa(limit) + "</MaxKeys><IsTruncated>" +
		isTruncated + "</IsTruncated>")
	for _, object := range objects {
		etag = object.ETag
		if etag != "" {
			etag = "quot;" + etag + "quot;"
		}
		bw.WriteString("<Contents><Key>" + object.Key + "</Key><Size>" +
			strconv.FormatInt(object.Size, 10) + "</Size><Owner><ID>" + object.Owner.ID() +
			"</ID><DisplayName>" + object.Owner.Name() +
			"</DisplayName></Owner>" +
			"<LastModified>" + object.LastModified.Format(S3Date) + "</LastModified>" +
			"<ETag>" + etag + "</ETag>" +
			"</Contents>")
	}
	for _, cp := range commonprefixes {
		bw.WriteString("<CommonPrefixes><Prefix>" + cp + "</Prefix></CommonPrefixes>")
	}
	bw.WriteString("</ListBucketResult>")
	bw.Flush()

	if logw != nil {
		log.Printf("sent %q", logw.Bytes())
	}
}

//This operation is useful to determine if a bucket exists and you have permission to access it.
//The operation returns a 200 OK if the bucket exists and you have permission to access it.
//Otherwise, the operation might return responses such as 404 Not Found and 403 Forbidden.
func (bucket bucketHandler) check(w http.ResponseWriter, r *http.Request) {
	owner, err := s3intf.GetOwner(bucket.Service, r, bucket.Service.Host())
	if err != nil {
		writeError(w, &HTTPError{Code: 15, HTTPCode: http.StatusBadRequest,
			Message:  "error getting owner: " + err.Error(),
			Resource: "/" + bucket.Name})
		return
	}
	if bucket.Service.CheckBucket(owner, bucket.Name) {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.WriteHeader(http.StatusNotFound)
	return
}

//This implementation of the PUT operation creates a new bucket.
//Anonymous requests are never allowed to create buckets.
//By creating the bucket, you become the bucket owner.
//
//Not every string is an acceptable bucket name. For information on bucket naming restrictions, see Working with Amazon S3 Buckets.
//DNS name constraints -> max length is 63
func (bucket bucketHandler) put(w http.ResponseWriter, r *http.Request) {
	log.Printf("%s.put", bucket)
	owner, err := s3intf.GetOwner(bucket.Service, r, bucket.Service.Host())
	if err != nil {
		writeError(w, &HTTPError{Code: 16, HTTPCode: http.StatusBadRequest,
			Message:  "error getting owner: " + err.Error(),
			Resource: "/" + bucket.Name})
		return
	}
	log.Printf("creating bucket %s for %s", bucket.Name, owner.ID())
	if err := bucket.Service.CreateBucket(owner, bucket.Name); err != nil {
		writeError(w, &HTTPError{Code: 17,
			Message:  "error creating bucket: " + err.Error(),
			Resource: "/" + bucket.Name})
		return
	}
	w.WriteHeader(http.StatusOK)
	return
}

func (obj objectHandler) del(w http.ResponseWriter, r *http.Request) {
	owner, err := s3intf.GetOwner(obj.Bucket.Service, r, obj.Bucket.Service.Host())
	if err != nil {
		writeError(w, &HTTPError{Code: 18, HTTPCode: http.StatusBadRequest,
			Message:  "error getting owner: " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	if err := obj.Bucket.Service.Del(owner, obj.Bucket.Name, obj.object); err != nil {
		he := &HTTPError{Code: 19,
			Message:  "error deleting " + obj.Bucket.Name + "/" + obj.object + ": " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object}

		if err == s3intf.NotFound {
			he.HTTPCode = http.StatusNotFound
		}
		writeError(w, he)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (obj objectHandler) get(w http.ResponseWriter, r *http.Request) {
	owner, err := s3intf.GetOwner(obj.Bucket.Service, r, obj.Bucket.Service.Host())
	if err != nil {
		writeError(w, &HTTPError{Code: 20, HTTPCode: http.StatusBadRequest,
			Message:  "error getting owner: " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	fn, media, body, size, md5, err := obj.Bucket.Service.Get(owner, obj.Bucket.Name, obj.object)
	log.Printf("GETing %s/%s: %q %s", obj.Bucket.Name, obj.object, fn, err)
	if err != nil {
		if err == s3intf.NotFound {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		writeError(w, &HTTPError{Code: 21,
			Message:  "error getting " + obj.Bucket.Name + "/" + obj.object + ": " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	if err = r.ParseForm(); err != nil {
		writeError(w, &HTTPError{Code: 22, HTTPCode: http.StatusBadRequest,
			Message:  "cannot parse form values: " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	w.Header().Set("Content-Type", media)
	w.Header().Set("Content-Disposition", "inline; filename=\""+fn+"\"")
	w.Header().Set("Content-Length", strconv.Itoa(int(size)))
	w.Header().Set("ETag", hex.EncodeToString(md5))
	for k, v := range r.Form {
		k = textproto.CanonicalMIMEHeaderKey(k)
		switch k {
		case "Content-Type", "Content-Language", "Expires", "Cache-Control",
			"Content-Disposition", "Content-Encoding", "Content-Length":
			(map[string][]string(w.Header()))[k] = v
		}
	}
	if Debug {
		log.Printf("headers: %s", w.Header())
	}
	io.Copy(w, body)
}

func (obj objectHandler) put(w http.ResponseWriter, r *http.Request) {
	if r.Body == nil {
		writeError(w, &HTTPError{Code: 23, HTTPCode: http.StatusBadRequest,
			Message:  "nil body",
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	defer r.Body.Close()
	owner, err := s3intf.GetOwner(obj.Bucket.Service, r, obj.Bucket.Service.Host())
	if err != nil {
		writeError(w, &HTTPError{Code: 24,
			Message:  "error getting owner: " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	var (
		fn, media string
		body      io.Reader
		size      int64
	)
	if r.Method == "POST" {
		mpf, mph, err := r.FormFile("file")
		if err != nil {
			return
		}
		fn = mph.Filename
		media = mph.Header.Get("Content-Type")
		size, _ = mpf.Seek(0, 2)
		mpf.Seek(0, 0)
		body = mpf
	} else {
		media = r.Header.Get("Content-Type")
		if disp := r.Header.Get("Content-Disposition"); disp != "" {
			if _, params, err := mime.ParseMediaType(disp); err == nil {
				fn = params["filename"]
			} else {
				writeError(w, &HTTPError{Code: 25, HTTPCode: http.StatusBadRequest,
					Message:  "cannot parse Content-Disposition " + disp + ": " + err.Error(),
					Resource: "/" + obj.Bucket.Name + "/" + obj.object})
				return
			}
		}
		if body, size, err = GetReaderSize(r.Body, 1<<20); err != nil {
			writeError(w, &HTTPError{Code: 28,
				Message:  "error reading request body: " + err.Error(),
				Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		}
	}
	hsh := crypto.MD5.New()
	if body, err = TeeRead(hsh, body, 1<<20); err != nil {
		writeError(w, &HTTPError{Code: 29,
			Message:  "error reading request body: " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
	}
	md5Computed := hex.EncodeToString(hsh.Sum(nil))
	md5Given := r.Header.Get("Content-MD5")
	if md5Given != "" && md5Computed != md5Given {
		writeError(w, &HTTPError{Code: 27, HTTPCode: http.StatusBadRequest,
			Message:  fmt.Sprintf("got MD5=%q computed=%q", md5Given, md5Computed),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
	}

	if fn == "" {
		log.Printf("no filename in %s", r.Header)
		fn = md5Computed
	}
	if err := obj.Bucket.Service.Put(owner, obj.Bucket.Name, obj.object,
		fn, media, body, size, hsh.Sum(nil)); err != nil {
		if err == s3intf.NotFound {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		writeError(w, &HTTPError{Code: 26, HTTPCode: http.StatusBadRequest,
			Message:  "error while storing " + fn + " in " + obj.Bucket.Name + "/" + obj.object + ": " + err.Error(),
			Resource: "/" + obj.Bucket.Name + "/" + obj.object})
		return
	}
	w.Header().Set("ETag", md5Computed)
	w.WriteHeader(http.StatusOK)
}

func stripPort(text string) string {
	return s3intf.StripPort(text)
}
