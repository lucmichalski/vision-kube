// Here is a GO VMX driver which wraps the VMX process with an HTTP handler
// This file is part of VMX
// Author: Tomasz Malisiewicz
// Copyright vision.ai, LLC 2014-2015

package main
import "os/exec"
import "fmt"
import "io/ioutil"
import "log"
import "net/http"
import "os"
import "net"
import "strconv"

import "path/filepath"
import "math/rand"

func main() {

	if len(os.Args) != 3 {
		fmt.Printf("Usage: ./VMXcamserver 0_or_movie :port\n")
		fmt.Printf("       port can be something like :3001, :0, or localhost:3008, but :0 will take the first available port\n")
		os.Exit(1)
	}	

	cur_dir, err := filepath.Abs(filepath.Dir(os.Args[0]))
	if err != nil {
		log.Fatal(err)
	}	

        my_channel := make(chan int, 1)
	
        listener, err_listener := net.Listen("tcp4",os.Args[2]);
	if err_listener != nil {
		log.Fatal(err_listener)
	}
        addy := listener.Addr()
	fmt.Fprintf(os.Stderr,"Welcome to VMXcamserver, to get an image visit the following address:\n")
	fmt.Printf("%s\n",addy)
	
        cmd2 := exec.Command(cur_dir+"/.VMXwebcam",os.Args[1])
        stdin_pipe,_ := cmd2.StdinPipe()
        stdout_pipe,_ := cmd2.StdoutPipe()

	err2 := cmd2.Start()
	counter := 0
	if err2 != nil {
		log.Panic(err2)
	}

	go func() {
		eee := cmd2.Wait()
		log.Printf("Process returned with error=%v",eee)
		os.Exit(1)	
	}()

	b1 := make([]byte, 5000)
	_,_ = stdout_pipe.Read(b1)
        
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		log.Printf(r.RemoteAddr+" "+r.UserAgent()+"\n")
		my_channel <- 1
		result_file := "/tmp/VMXwebcam."+strconv.Itoa(rand.Intn(50000))+"_"+strconv.Itoa(counter)+".jpg"
		_,_ = stdin_pipe.Write([]byte(result_file+"\n"))
		_,_ = stdout_pipe.Read(b1)
		dat, _ := ioutil.ReadFile(result_file)

		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Allow","GET")
		w.Header().Set("Access-Control-Allow-Headers","Authorization,Content-Type")
		w.Header().Set("Access-Control-Allow-Methods","GET")

		fmt.Fprintf(w, "%s", dat)
		counter = counter + 1
		_ = os.Remove(result_file)
                <-my_channel                           

	})
	log.Fatal(http.Serve(listener, nil))
}
