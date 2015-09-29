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

package s3intf

import (
	"bufio"
	"bytes"
	"net/http"
	"strings"
	"testing"
)

func TestStringToSign(t *testing.T) {
	table := [][2]string{
		{`GET /photos/puppy.jpg HTTP/1.1
Host: johnsmith.s3.amazonaws.com
Date: Tue, 27 Mar 2007 19:36:42 +0000
Authorization: AWS AKIAIOSFODNN7EXAMPLE:bWq2s1WEIj+Ydj0vQ697zp+IXMU=`,
			`GET


Tue, 27 Mar 2007 19:36:42 +0000
/johnsmith/photos/puppy.jpg`},

		{`PUT /photos/puppy.jpg HTTP/1.1
Content-Type: image/jpeg
Content-Length: 94328
Host: johnsmith.s3.amazonaws.com
Date: Tue, 27 Mar 2007 21:15:45 +0000
Authorization: AWS AKIAIOSFODNN7EXAMPLE:MyyxeRY7whkBe+bq8fHCL/2kKUg=`,
			`PUT

image/jpeg
Tue, 27 Mar 2007 21:15:45 +0000
/johnsmith/photos/puppy.jpg`},
		{`GET /?prefix=photos&max-keys=50&marker=puppy HTTP/1.1
User-Agent: Mozilla/5.0
Host: johnsmith.s3.amazonaws.com
Date: Tue, 27 Mar 2007 19:42:41 +0000
Authorization: AWS AKIAIOSFODNN7EXAMPLE:htDYFYduRNen8P9ZfE/s9SuKy0U=`,
			`GET\n
\n
\n
Tue, 27 Mar 2007 19:42:41 +0000\n
/johnsmith/`},
		{`GET /?acl HTTP/1.1
Host: johnsmith.s3.amazonaws.com
Date: Tue, 27 Mar 2007 19:44:46 +0000

Authorization: AWS AKIAIOSFODNN7EXAMPLE:c2WLPFtWHVgbEmeEG93a4cG37dM=`,
			`GET\n
\n
\n
Tue, 27 Mar 2007 19:44:46 +0000\n
/johnsmith/?acl`},
		{`DELETE /johnsmith/photos/puppy.jpg HTTP/1.1
User-Agent: dotnet
Host: s3.amazonaws.com
Date: Tue, 27 Mar 2007 21:20:27 +0000
x-amz-date: Tue, 27 Mar 2007 21:20:26 +0000
Authorization: AWS AKIAIOSFODNN7EXAMPLE:lx3byBScXR6KzyMaifNkardMwNk=`,
			`DELETE\n
\n
\n
\n
x-amz-date:Tue, 27 Mar 2007 21:20:26 +0000\n
/johnsmith/photos/puppy.jpg`},
		{`PUT /db-backup.dat.gz HTTP/1.1
User-Agent: curl/7.15.5
Host: static.johnsmith.net:8080
Date: Tue, 27 Mar 2007 21:06:08 +0000
x-amz-acl: public-read
content-type: application/x-download
Content-MD5: 4gJE4saaMU4BqNR0kLY+lw==
X-Amz-Meta-ReviewedBy: joe@johnsmith.net
X-Amz-Meta-ReviewedBy: jane@johnsmith.net
X-Amz-Meta-FileChecksum: 0x02661779
X-Amz-Meta-ChecksumAlgorithm: crc32
Content-Disposition: attachment; filename=database.dat
Content-Encoding: gzip
Content-Length: 5913339

Authorization: AWS AKIAIOSFODNN7EXAMPLE:ilyl83RwaSoYIEdixDQcA4OnAnc=`,
			`PUT\n
4gJE4saaMU4BqNR0kLY+lw==\n
application/x-download\n
Tue, 27 Mar 2007 21:06:08 +0000\n
x-amz-acl:public-read\n
x-amz-meta-checksumalgorithm:crc32\n
x-amz-meta-filechecksum:0x02661779\n
x-amz-meta-reviewedby:joe@johnsmith.net,jane@johnsmith.net\n
/static.johnsmith.net/db-backup.dat.gz`},
		{`GET / HTTP/1.1
Host: s3.amazonaws.com
Date: Wed, 28 Mar 2007 01:29:59 +0000
Authorization: AWS AKIAIOSFODNN7EXAMPLE:qGdzdERIC03wnaRNKh6OqZehG9s=`,
			`GET\n
\n
\n
Wed, 28 Mar 2007 01:29:59 +0000\n
/`},
		{`GET /dictionary/fran%C3%A7ais/pr%c3%a9f%c3%a8re HTTP/1.1
Host: s3.amazonaws.com
Date: Wed, 28 Mar 2007 01:49:49 +0000
Authorization: AWS AKIAIOSFODNN7EXAMPLE:DNEZGsoieTZ92F3bUfSPQcbGmlM=`,
			`GET\n
\n
\n
Wed, 28 Mar 2007 01:49:49 +0000\n
/dictionary/fran%C3%A7ais/pr%c3%a9f%c3%a8re`},
		{`GET /photos/puppy.jpg?AWSAccessKeyId=AKIAIOSFODNN7EXAMPLE&Signature=NpgCjnDzrM%2BWFzoENXmpNDUsSn8%3D&Expires=1175139620 HTTP/1.1
Host: johnsmith.s3.amazonaws.com`,
			`GET\n
\n
\n
1175139620\n
/johnsmith/photos/puppy.jpg`},
	}

	var (
		got         []byte
		r           *http.Request
		err         error
		dedebug     bool
		serviceHost string
	)
	for i, row := range table {
		row = [2]string{stripspaces(row[0], false) + "\n\n", stripspaces(row[1], true)}
		r, err = http.ReadRequest(bufio.NewReader(strings.NewReader(row[0])))
		if err != nil {
			t.Fatalf("%d: bad request: %s", i, err)
			break
		}
		serviceHost = "s3.amazonaws.com"
		if strings.Index(r.Host, serviceHost) < 0 {
			serviceHost = ""
			//serviceHost = r.Host[strings.Index(r.Host, ".")+1:]
			//t.Logf("r.Host=%s => serviceHost=%s", r.Host, serviceHost)
		}
		//if strings.Index(row[0], "x-amz-date:") > 0 || serviceHost == "" {
		//	Debug, dedebug = true, !Debug
		//}
		got = GetBytesToSign(r, serviceHost)
		if string(got) != row[1] {
			d := getDiffPos(string(got), row[1])
			//log.Printf("d=%d", d)
			t.Errorf("%d. got {{{%s}}}\n awaited [[[%s]]] at pos %d (%s != %s)",
				i, string(got), row[1],
				d, string([]byte{got[d]}), string([]byte{row[1][d]}))
			t.Fail()
		}
		if dedebug {
			Debug, dedebug = false, false
		}
	}
}

func stripspaces(text string, stripbsn bool) string {
	res := make([]byte, 0, len(text))
	del := false
	for i := 0; i < len(text); i++ {
		if !del {
			if text[i] == byte(10) { //\n
				del = true
			}
		} else {
			if text[i] == byte(32) || text[i] == byte(9) { // space or tab
				continue
			}
			del = false
		}
		res = append(res, text[i])
	}
	if stripbsn {
		return string(bytes.Replace(res, []byte("\\n"), nil, -1))
	}
	return string(res)
}

func getDiffPos(a, b string) int {
	if a == "" || b == "" {
		return 0
	}
	n := len(a)
	if len(a) > len(b) {
		n = len(b)
	}
	//log.Printf("la=%d lb=%d n=%d", len(a), len(b), n)
	for i := 0; i < n; i++ {
		if a[i] != b[i] {
			return i
		}
	}
	if len(a) != len(b) {
		return n - 1
	}
	return -1
}
