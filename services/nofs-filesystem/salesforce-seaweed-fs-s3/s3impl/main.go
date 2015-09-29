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
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/tgulacsi/s3weed/s3impl/dirS3"
	"github.com/tgulacsi/s3weed/s3impl/weedS3"
	"github.com/tgulacsi/s3weed/s3impl/weedS3/weedutils"
	"github.com/tgulacsi/s3weed/s3intf"
	"github.com/tgulacsi/s3weed/s3srv"

	"github.com/cznic/kv"
)

var (
	dir      = flag.String("dir", "", "use dirS3 with the given dir as base (i.e. -dir=/tmp)")
	weed     = flag.String("weed", "", "use weedS3 with the given master url (i.e. -weed=localhost:9333)")
	weedDb   = flag.String("db", "", "weedS3's db dir")
	hostPort = flag.String("http", ":8080", "host:port to listen on")
)

func main() {
	flag.Parse()
	cmd := "server"
	if flag.NArg() > 0 {
		cmd = flag.Arg(0)
	}
	switch cmd {
	case "dump":
		if *weedDb == "" {
			log.Fatalf("-db is needed to know what to dump!")
		}
		if err := dumpAll(*weedDb); err != nil {
			log.Fatalf("error dumping %s: %s", *weedDb, err)
		}

	default: //server
		s3srv.Debug = true
		s3intf.Debug = true
		var (
			impl s3intf.Storage
			err  error
		)
		if *dir != "" {
			impl = dirS3.NewDirS3(*dir)
		} else if *weed != "" && *weedDb != "" {
			if impl, err = weedS3.NewWeedS3(*weed, *weedDb); err != nil {
				log.Fatalf("cannot create WeedS3(%s, %s): %s", *weed, *weedDb, err)
			}
		} else {
			log.Fatalf("dir OR weed AND db is required!")
		}
		srvc := s3srv.NewService(*hostPort, impl)
		log.Fatal(http.ListenAndServe(*hostPort, srvc))
	}
}

func dumpAll(dbdir string) (err error) {
	dirs := make(chan string)
	go func() {
		if err = weedutils.ReadDirNames(dbdir,
			func(fi os.FileInfo) bool {
				return fi.IsDir()
			}, dirs); err != nil {
			log.Printf("error reading %s: %s", dbdir, err)
			return
		}
	}()

	var dn string
	bw := bufio.NewWriter(os.Stdout)
	defer bw.Flush()
	bw.WriteString("[")

	for dn = range dirs {
		dbs, errch := weedutils.OpenAllDb(dn, ".kv")
		select {
		case err = <-errch:
			log.Printf("error opening db: %s", err)
			continue
		default:
			err = nil
		}
		bw.WriteString(`{"owner": "` + filepath.Base(dn) + `", `)
		if err = dumpBuckets(bw, dbs); err != nil {
			log.Printf("error dumping buckets: %s", err)
		}
		select {
		case err = <-errch:
			if err != nil {
				log.Printf("error opening dbs: %s", err)
			}
		default:
			err = nil
		}
		bw.Flush()
	}
	os.Stdout.Close()
	return err
}

func dumpBuckets(w io.Writer, dbs <-chan *kv.DB) (err error) {
	io.WriteString(w, `"buckets": [`)
	//log.Printf("listening on %s", dbs)
	for db := range dbs {
		//log.Printf("calling dumpBucket(%s, %s)", w, db)
		if err = dumpBucket(w, db); err != nil {
			log.Printf("error dumping %s: %s", db, err)
			db.Close()
			return
		}
		db.Close()
	}
	io.WriteString(w, "],\n")
	return nil
}

func dumpBucket(w io.Writer, db *kv.DB) (err error) {
	//log.Printf("dumping %s", db)
	io.WriteString(w, `{"name": "`+db.Name()+`", "records": [`)
	enc := json.NewEncoder(w)
	enum, e := db.SeekFirst()
	if e != nil {
		if e != io.EOF {
			err = e
			log.Printf("error getting first: %s", err)
		}
		return
	}
	vi := new(weedutils.ValInfo)
	for {
		k, v, err := enum.Next()
		if err != nil {
			if err != io.EOF {
				log.Printf("error getting next: %s", err)
			}
			break
		}
		if err = vi.Decode(v); err != nil {
			log.Printf("error decoding %s: %s", v, err)
			continue
		}
		fmt.Fprintf(w, `{"object": %q, "value": `, k)
		if err = enc.Encode(vi); err != nil {
			log.Printf("error printing %v to json: %s", vi, err)
			continue
		}
		io.WriteString(w, "},\n")

	}
	io.WriteString(w, "]},\n")
	io.WriteString(w, "]},\n")
	return nil
}
