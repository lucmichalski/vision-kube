/*
Package s3intf defines an interface for an S3 server

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
package s3intf

import (
	"bytes"
	//"hash"
	"errors"
	"io"
	"time"
)

// NotFound prints Not Found
var NotFound = errors.New("Not Found")

// Bucket is a holder for objects
type Bucket struct {
	Name    string
	Created time.Time
}

// Object represents a file
type Object struct {
	Key          string
	LastModified time.Time
	ETag         string
	Size         int64
	Owner        Owner
}

// Hasher is an interface for hashign for authorization (checking)
type Hasher interface {
	// CalcHash calculates the hash of bytesToSign
	CalcHash(bytesToSign []byte) []byte
}

// Check checks the validity of the authorization,
func Check(prov Hasher, bytesToSign []byte, challenge []byte) bool {
	if challenge == nil {
		return false
	}
	return bytes.Equal(prov.CalcHash(bytesToSign), challenge)
}

// Owner is the object's owner
type Owner interface {
	// ID returns the ID of this owner
	ID() string
	// Name returns then name of this owner
	Name() string
	// an Owner must implement the Authorizer interface (Check method)
	Hasher
}

// Storage is an interface for what is needed for S3
// You must implement this, and than s3srv can use this Storage to implement
// the server
type Storage interface {
	// ListBuckets list all buckets owned by the given owner
	ListBuckets(owner Owner) ([]Bucket, error)
	// CreateBucket creates a new bucket
	CreateBucket(owner Owner, bucket string) error
	// CheckBucket returns whether the owner has a bucket named as given
	CheckBucket(owner Owner, bucket string) bool
	// DelBucket deletes a bucket
	DelBucket(owner Owner, bucket string) error
	// List lists a bucket, all objects Key starts with prefix, delimiter segments
	// Key, thus the returned commonprefixes (think a generalized filepath
	// structure, where / is the delimiter, a commonprefix is a subdir)
	//The prefix and delimiter parameters limit the kind of results returned by a list operation.
	//Prefix limits results to only those keys that begin with the specified prefix,
	//and delimiter causes list to roll up all keys that share a common prefix
	//into a single summary list result.
	List(owner Owner, bucket, prefix, delimiter, marker string, limit, skip int) (
		objects []Object, commonprefixes []string, truncated bool, err error)
	// Put puts a file as a new object into the bucket
	Put(owner Owner, bucket, object, filename, media string, body io.Reader, size int64, md5hash []byte) error
	// Get retrieves an object from the bucket
	Get(owner Owner, bucket, object string) (filename, media string, body io.ReadCloser, size int64, md5hash []byte, err error)
	// Del deletes the object from the bucket
	Del(owner Owner, bucket, object string) error
	// GetOwner returns the Owner for the accessKey - or an error
	GetOwner(accessKey string) (Owner, error)
}
