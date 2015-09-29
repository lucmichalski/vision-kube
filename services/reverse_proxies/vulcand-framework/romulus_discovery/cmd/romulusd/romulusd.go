package main

import (
	"io/ioutil"
	"os"
	"os/signal"
	"syscall"
	"time"

	l "github.com/Sirupsen/logrus"
	"github.com/ghodss/yaml"
	"github.com/timelinelabs/romulus/romulus"
	"golang.org/x/net/context"
	"gopkg.in/alecthomas/kingpin.v2"
)

var (
	logLevels = []string{"fatal", "error", "warn", "info", "debug"}

	vk = kingpin.Flag("vulcand-key", "vulcand etcd key").Short('v').Default("vulcand").OverrideDefaultFromEnvar("VULCAND_KEY").String()
	ep = kingpin.Flag("etcd", "etcd peers").Short('e').Default("http://127.0.0.1:2379").OverrideDefaultFromEnvar("ETCD_PEERS").URLList()
	et = kingpin.Flag("etcd-timeout", "etcd request timeout").Short('t').Default("5s").OverrideDefaultFromEnvar("ETCD_TIMEOUT").Duration()
	km = kingpin.Flag("kube", "kubernetes endpoint").Short('k').Default("http://127.0.0.1:8080").OverrideDefaultFromEnvar("KUBE_MASTER").URL()
	ku = kingpin.Flag("kube-user", "kubernetes username").Short('U').Default("").OverrideDefaultFromEnvar("KUBE_USER").String()
	kp = kingpin.Flag("kube-pass", "kubernetes password").Short('P').Default("").OverrideDefaultFromEnvar("KUBE_PASS").String()
	kv = kingpin.Flag("kube-api", "kubernetes api version").Default("v1").OverrideDefaultFromEnvar("KUBE_API_VER").String()
	kc = kingpin.Flag("kubecfg", "path to kubernetes cfg file").Short('C').PlaceHolder("/path/to/.kubecfg").ExistingFile()
	sl = kingpin.Flag("svc-selector", "service selectors. Leave blank for Everything(). Form: key=value").Short('s').PlaceHolder("key=value[,key=value]").OverrideDefaultFromEnvar("SVC_SELECTOR").StringMap()
	db = kingpin.Flag("debug", "Enable debug logging. e.g. --log-level debug").Short('d').Bool()
	lv = kingpin.Flag("log-level", "log level. One of: fatal, error, warn, info, debug").Short('l').Default("info").OverrideDefaultFromEnvar("LOG_LEVEL").Enum(logLevels...)
	ed = kingpin.Flag("debug-etcd", "Enable cURL debug logging for etcd").Bool()
)

func main() {
	kingpin.Version(romulus.Version())
	kingpin.Parse()
	if *db {
		LogLevel("debug")
	} else {
		LogLevel(*lv)
	}
	if *ed {
		romulus.DebugEtcd()
	}

	eps := []string{}
	kcc := romulus.KubeClientConfig{
		Host:     (*km).String(),
		Username: *ku,
		Password: *kp,
		Insecure: true,
	}
	for _, e := range *ep {
		eps = append(eps, e.String())
	}
	if *kc != "" {
		b, _ := ioutil.ReadFile(*kc)
		yaml.Unmarshal(b, &kcc)
		if kcc.CAFile != "" || kcc.CertFile != "" {
			kcc.Insecure = false
		}
	}

	log().Info("Starting up romulusd")
	r, e := romulus.NewRegistrar(&romulus.Config{
		PeerList:            eps,
		EtcdTimeout:         *et,
		APIVersion:          *kv,
		KubeConfig:          kcc,
		Selector:            *sl,
		VulcanEtcdNamespace: *vk,
	})
	if e != nil {
		logf(fi{"err": e}).Error("Configuration Error!")
		os.Exit(2)
	}

	ctx, cancel := context.WithCancel(context.Background())
	if e := romulus.Start(r, ctx); e != nil {
		cancel()
		logf(fi{"err": e}).Fatal("Runtime Error!")
		os.Exit(1)
	}

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGTERM, syscall.SIGINT)
	select {
	case <-sig:
		l.Info("Recieved interrupt, shutting down")
		cancel()
		time.Sleep(100 * time.Millisecond)
		os.Exit(0)
	}
}

// F is just a simple type for adding tags to logs
type fi map[string]interface{}

var pkgField = l.Fields{"pkg": "main", "version": romulus.Version()}

// LogLevel sets the logging level
func LogLevel(lv string) {
	if lvl, e := l.ParseLevel(lv); e == nil {
		l.SetLevel(lvl)
	}
	romulus.LogLevel(lv)
}

func log() *l.Entry { return logf(nil) }
func logf(f fi) *l.Entry {
	fi := l.Fields{}
	for k, v := range pkgField {
		fi[k] = v
	}
	for k, v := range f {
		fi[k] = v
	}
	return l.WithFields(fi)
}
