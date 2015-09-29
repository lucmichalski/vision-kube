package weed_server

import (
	"net/http"
	"path/filepath"

	"github.com/chrislusf/seaweedfs/go/glog"
	"github.com/chrislusf/seaweedfs/go/stats"
	"github.com/chrislusf/seaweedfs/go/util"
)

func (vs *VolumeServer) statusHandler(w http.ResponseWriter, r *http.Request) {
	m := make(map[string]interface{})
	m["Version"] = util.VERSION
	m["Volumes"] = vs.store.Status()
	writeJsonQuiet(w, r, http.StatusOK, m)
}

func (vs *VolumeServer) assignVolumeHandler(w http.ResponseWriter, r *http.Request) {
	err := vs.store.AddVolume(r.FormValue("volume"), r.FormValue("collection"), vs.needleMapKind, r.FormValue("replication"), r.FormValue("ttl"))
	if err == nil {
		writeJsonQuiet(w, r, http.StatusAccepted, map[string]string{"error": ""})
	} else {
		writeJsonError(w, r, http.StatusNotAcceptable, err)
	}
	glog.V(2).Infoln("assign volume =", r.FormValue("volume"), ", collection =", r.FormValue("collection"), ", replication =", r.FormValue("replication"), ", error =", err)
}

func (vs *VolumeServer) deleteCollectionHandler(w http.ResponseWriter, r *http.Request) {
	if "benchmark" != r.FormValue("collection") {
		glog.V(0).Infoln("deleting collection =", r.FormValue("collection"), "!!!")
		return
	}
	err := vs.store.DeleteCollection(r.FormValue("collection"))
	if err == nil {
		writeJsonQuiet(w, r, http.StatusOK, map[string]string{"error": ""})
	} else {
		writeJsonError(w, r, http.StatusInternalServerError, err)
	}
	glog.V(2).Infoln("deleting collection =", r.FormValue("collection"), ", error =", err)
}

func (vs *VolumeServer) statsDiskHandler(w http.ResponseWriter, r *http.Request) {
	m := make(map[string]interface{})
	m["Version"] = util.VERSION
	var ds []*stats.DiskStatus
	for _, loc := range vs.store.Locations {
		if dir, e := filepath.Abs(loc.Directory); e == nil {
			ds = append(ds, stats.NewDiskStatus(dir))
		}
	}
	m["DiskStatuses"] = ds
	writeJsonQuiet(w, r, http.StatusOK, m)
}
