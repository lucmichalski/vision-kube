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
	"io"
	"log"
	"strings"
)

// ListFilter is an interface for the list filtering and grouping
type ListFilter interface {
	// Check returns whether the name should be added to the list, and returns io.EOF on truncation (limit end)
	//The prefix and delimiter parameters limit the kind of results returned by a list operation.
	//Prefix limits results to only those keys that begin with the specified prefix,
	//and delimiter causes list to roll up all keys that share a common prefix
	//into a single summary list result.
	Check(name string) (bool, error)

	// Result returns the gathered common prefixes and whether the result is truncated
	Result() (commonprefixes []string, truncated bool)
}

// NewListFilter returns a new filter with the given prefix, delimiter, marker, limit and skip
func NewListFilter(prefix, delimiter, marker string, limit, skip int) ListFilter {
	f := &listFilter{prefix: prefix, delimiter: delimiter, marker: marker,
		limit: limit, skip: skip}
	if f.delimiter != "" {
		f.prefixes = make(map[string]bool, 4)
	}
	return f
}

type listFilter struct {
	prefix, delimiter, marker string
	limit, skip               int
	n                         int
	truncated                 bool
	prefixes                  map[string]bool
}

// Check implements ListFilter.Check
func (f *listFilter) Check(name string) (bool, error) {
	n := f.n
	f.n++
	if Debug {
		log.Printf("Check(%s) n=%d skip=%d limit=%d", name, n, f.skip, f.limit)
	}
	if n < f.skip {
		return false, nil
	}
	if n-f.skip > f.limit {
		f.truncated = true
		return false, io.EOF
	}

	//The prefix and delimiter parameters limit the kind of results returned by a list operation.
	//Prefix limits results to only those keys that begin with the specified prefix,
	//and delimiter causes list to roll up all keys that share a common prefix
	//into a single summary list result.
	var (
		i   int
		ok  bool
		dir string
	)
	if f.delimiter == "" {
		i = -1
	}

	if f.prefix == "" || strings.HasPrefix(name, f.prefix) {
		var base string
		if f.delimiter != "" {
			base = name[len(f.prefix):]
			i = strings.Index(base, f.delimiter)
		}
		if Debug {
			log.Printf("delim=%q name=%q => base=%q i=%d", f.delimiter, name, base, i)
		}
		if i < 0 {
			return true, nil
		} // delimiter != "" && delimiter in key[len(prefix):]
		dir = base[:i]
		if _, ok = f.prefixes[dir]; !ok {
			f.prefixes[dir] = true
		}
	}
	return false, nil
}

// Result implements ListFilter.Result
func (f *listFilter) Result() (commonprefixes []string, truncated bool) {
	truncated = f.truncated
	if len(f.prefixes) > 0 {
		commonprefixes = make([]string, 0, len(f.prefixes))
		for dir := range f.prefixes {
			commonprefixes = append(commonprefixes, dir)
		}
	}
	return
}
