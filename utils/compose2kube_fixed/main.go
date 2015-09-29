/*
Copyright 2015 Kelsey Hightower All rights reserved.
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

package main // import "github.com/sstarcher/compose2kube"

import (
	"encoding/json"
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/docker/libcompose/project"
	"k8s.io/kubernetes/pkg/api"
	"k8s.io/kubernetes/pkg/api/resource"
)

var (
	composeFile string
	outputDir   string
)

func init() {
	flag.StringVar(&composeFile, "compose-file", "docker-compose.yml", "Specify an alternate compose `file`")
	flag.StringVar(&outputDir, "output-dir", "output", "Kubernetes configs output `directory`")
}

func main() {
	flag.Parse()

	p := project.NewProject(&project.Context{
		ProjectName: "kube",
		ComposeFile: composeFile,
	})

	if err := p.Parse(); err != nil {
		log.Fatalf("Failed to parse the compose project from %s: %v", composeFile, err)
	}
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		log.Fatalf("Failed to create the output directory %s: %v", outputDir, err)
	}

	for name, service := range p.Configs {
		pod := &api.Pod{
			TypeMeta: api.TypeMeta{
				Kind:       "Pod",
				APIVersion: "v1",
			},
			ObjectMeta: api.ObjectMeta{
				Name:   name,
				Labels: map[string]string{"service": name},
			},
			Spec: api.PodSpec{
				Containers: []api.Container{
					{
						Name:  name,
						Image: service.Image,
						Args:  service.Command.Slice(),
						Resources: api.ResourceRequirements{
							Limits: api.ResourceList{},
						},
					},
				},
			},
		}

		if service.CpuShares != 0 {
			pod.Spec.Containers[0].Resources.Limits[api.ResourceCPU] = *resource.NewQuantity(service.CpuShares, "decimalSI")
		}

		if service.MemLimit != 0 {
			pod.Spec.Containers[0].Resources.Limits[api.ResourceMemory] = *resource.NewQuantity(service.MemLimit, "decimalSI")
		}

		// Configure the environment variables
		var environment []api.EnvVar
		for _, envs := range service.Environment.Slice() {
			value := strings.Split(envs, "=")
			environment = append(environment, api.EnvVar{Name: value[0], Value: value[1]})
		}

		pod.Spec.Containers[0].Env = environment

		// Configure the container ports.
		var ports []api.ContainerPort
		for _, port := range service.Ports {
			portNumber, err := strconv.Atoi(port)
			if err != nil {
				log.Fatalf("Invalid container port %s for service %s", port, name)
			}
			ports = append(ports, api.ContainerPort{ContainerPort: portNumber})
		}

		pod.Spec.Containers[0].Ports = ports

		// Configure the container restart policy.
		var (
			rc      *api.ReplicationController
			objType string
			data    []byte
			err     error
		)
		switch service.Restart {
		case "", "always":
			objType = "rc"
			rc = replicationController(name, pod)
			pod.Spec.RestartPolicy = api.RestartPolicyAlways
			data, err = json.MarshalIndent(rc, "", "  ")
		case "no", "false":
			objType = "pod"
			pod.Spec.RestartPolicy = api.RestartPolicyNever
			data, err = json.MarshalIndent(pod, "", "  ")
		case "on-failure":
			objType = "rc"
			rc = replicationController(name, pod)
			pod.Spec.RestartPolicy = api.RestartPolicyOnFailure
			data, err = json.MarshalIndent(rc, "", "  ")
		default:
			log.Fatalf("Unknown restart policy %s for service %s", service.Restart, name)
		}

		if err != nil {
			log.Fatalf("Failed to marshal the replication controller: %v", err)
		}

		// Save the replication controller for the Docker compose service to the
		// configs directory.
		outputFileName := fmt.Sprintf("%s-%s.yaml", name, objType)
		outputFilePath := filepath.Join(outputDir, outputFileName)
		if err := ioutil.WriteFile(outputFilePath, data, 0644); err != nil {
			log.Fatalf("Failed to write replication controller %s: %v", outputFileName, err)
		}
		fmt.Println(outputFilePath)
	}
}

func replicationController(name string, pod *api.Pod) *api.ReplicationController {
	return &api.ReplicationController{
		TypeMeta: api.TypeMeta{
			Kind:       "ReplicationController",
			APIVersion: "v1",
		},
		ObjectMeta: api.ObjectMeta{
			Name:   name,
			Labels: map[string]string{"service": name},
		},
		Spec: api.ReplicationControllerSpec{
			Replicas: 1,
			Selector: map[string]string{"service": name},
			Template: &api.PodTemplateSpec{
				ObjectMeta: api.ObjectMeta{
					Labels: map[string]string{"service": name},
				},
				Spec: pod.Spec,
			},
		},
	}
}
