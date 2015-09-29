package romulus

import (
	"path/filepath"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/context"

	"github.com/coreos/etcd/client"
)

var (
	EtcdDebug = false
)

type EtcdClient interface {
	Add(key, val string) error
	Keys(pre string) ([]string, error)
	Del(key string) error
}

type realEtcdClient struct {
	client.KeysAPI
	*sync.RWMutex
	p string
	t time.Duration
}

type fakeEtcdClient map[string]string

func DebugEtcd() {
	EtcdDebug = true
}

func NewEtcdClient(peers []string, prefix string, timeout time.Duration) (EtcdClient, error) {
	if EtcdDebug {
		client.EnablecURLDebug()
	}
	ec, er := client.New(client.Config{Endpoints: peers})
	if er != nil {
		return nil, er
	}
	return &realEtcdClient{client.NewKeysAPI(ec), new(sync.RWMutex), prefix, timeout}, nil
}

func NewFakeEtcdClient() EtcdClient {
	return &fakeEtcdClient{"/": ""}
}

func (r *realEtcdClient) addPrefix(k string) string {
	return strings.Join([]string{r.p, k}, "/")
}

func (r *realEtcdClient) Add(k, v string) error {
	r.Lock()
	defer r.Unlock()
	return r.add(r.addPrefix(k), v)
}

func (r *realEtcdClient) add(k, v string) error {
	c, q := context.WithTimeout(context.Background(), r.t)
	defer q()

	_, e := r.Set(c, k, v, nil)
	return e
}

func (r *realEtcdClient) Del(k string) error {
	r.Lock()
	defer r.Unlock()
	return r.del(r.addPrefix(k))
}

func (r *realEtcdClient) del(k string) error {
	c, q := context.WithTimeout(context.Background(), r.t)
	defer q()

	_, e := r.Delete(c, k, &client.DeleteOptions{Recursive: true})
	if isKeyNotFound(e) {
		return nil
	}
	return e
}

func (r *realEtcdClient) Keys(k string) ([]string, error) {
	r.RLock()
	defer r.RUnlock()
	return r.keys(r.addPrefix(k))
}

func (re *realEtcdClient) keys(p string) ([]string, error) {
	c, q := context.WithTimeout(context.Background(), re.t)
	defer q()

	r, e := re.Get(c, p, &client.GetOptions{Recursive: true, Sort: true, Quorum: true})
	if e != nil {
		if isKeyNotFound(e) {
			return []string{}, nil
		}
		return []string{}, e
	}
	if r.Node == nil {
		return []string{}, nil
	}

	k := make([]string, 0, len(r.Node.Nodes))
	for _, n := range r.Node.Nodes {
		k = append(k, strings.TrimLeft(strings.TrimPrefix(n.Key, p), "/"))
	}
	return k, nil
}

func (f fakeEtcdClient) Add(k, v string) (e error) { f[k] = v; return }
func (f fakeEtcdClient) Del(k string) error {
	delete(f, k)
	for key := range f {
		if strings.HasPrefix(key, k) {
			delete(f, key)
		}
	}
	return nil
}
func (f fakeEtcdClient) Keys(p string) ([]string, error) {
	r := []string{}
	for k := range f {
		if p == filepath.Dir(k) {
			r = append(r, filepath.Base(k))
		}
	}
	return r, nil
}

func isKeyNotFound(e error) bool {
	if e == nil {
		return false
	}
	switch e := e.(type) {
	default:
		return false
	case client.Error:
		if e.Code == client.ErrorCodeKeyNotFound {
			return true
		}
		return false
	}
}
