package main

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/BurntSushi/toml"
	log "github.com/Sirupsen/logrus"
	"gopkg.in/fsnotify.v1"
)

type FileProvider struct {
	Watch    bool
	Filename string
}

func NewFileProvider() *FileProvider {
	fileProvider := new(FileProvider)
	// default values
	fileProvider.Watch = true

	return fileProvider
}

func (provider *FileProvider) Provide(configurationChan chan<- configMessage) {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Error("Error creating file watcher", err)
		return
	}
	defer watcher.Close()

	file, err := os.Open(provider.Filename)
	if err != nil {
		log.Error("Error opening file", err)
		return
	}
	defer file.Close()

	done := make(chan bool)
	// Process events
	go func() {
		for {
			select {
			case event := <-watcher.Events:
				if strings.Contains(event.Name, file.Name()) {
					log.Debug("File event:", event)
					configuration := provider.LoadFileConfig(file.Name())
					if configuration != nil {
						configurationChan <- configMessage{"file", configuration}
					}
				}
			case error := <-watcher.Errors:
				log.Error("Watcher event error", error)
			}
		}
	}()

	if provider.Watch {
		err = watcher.Add(filepath.Dir(file.Name()))
	}

	if err != nil {
		log.Error("Error adding file watcher", err)
		return
	}

	configuration := provider.LoadFileConfig(file.Name())
	configurationChan <- configMessage{"file", configuration}
	<-done
}

func (provider *FileProvider) LoadFileConfig(filename string) *Configuration {
	configuration := new(Configuration)
	if _, err := toml.DecodeFile(filename, configuration); err != nil {
		log.Error("Error reading file:", err)
		return nil
	}
	return configuration
}
