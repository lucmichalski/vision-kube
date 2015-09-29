# Endpoints:

List of endpoints added with the vision-kube

## LibCcv
http://seeubaby.blippar.com/api/vision/libccv/
http://seeubaby.blippar.com/api/vision/libccv/bbf/detect.objects
http://seeubaby.blippar.com/api/vision/libccv/convnet/classify
http://seeubaby.blippar.com/api/vision/libccv/dpm/detect.objects
http://seeubaby.blippar.com/api/vision/libccv/icf/detect.objects
http://seeubaby.blippar.com/api/vision/libccv/scd/detect.objects
http://seeubaby.blippar.com/api/vision/libccv/sift
http://seeubaby.blippar.com/api/vision/libccv/swt/detect.words
http://seeubaby.blippar.com/api/vision/libccv/tld/track.object

## Analyze Dominant Colors
http://seeubaby.blippar.com/api/vision/d-colors/  
http://seeubaby.blippar.com/api/vision/d-colors/buildInformation
http://seeubaby.blippar.com/api/vision/d-colors/analyze/dominantColors
http://seeubaby.blippar.com/api/vision/d-colors/analyze/dominantColors

## LTU 7.6 Engine


## VMX v1.x Maxfactor:
UI:
http://seeubaby.blippar.com/api/vision/vmx2/maxfactor/#/
VMX API:
POST	/session	Create a new session
http://seeubaby.blippar.com/api/vision/vmx1/maxfactor/session
PUT	/model	Save the model
http://seeubaby.blippar.com/api/vision/vmx1/maxfactor/model
POST	/model	Create a new model
http://seeubaby.blippar.com/api/vision/vmx1/maxfactor/model
POST	/session/#session_id	Detect objects inside the image
http://seeubaby.blippar.com/api/vision/vmx1/maxfactor/session
GET	/session/#session_id/params	List loaded model's parameters
http://seeubaby.blippar.com/api/vision/vmx1/maxfactor/model
POST	/session/#session_id/edit	Show the model
PUT	/session/#session_id/edit	Edit the model
POST	/session/#session_id/load	Load a new model
POST	/session/#session_id/save	Save model
GET	/session/#session_id/log.txt	Last line of the log
GET	/check	Check VMX version and whether this copy is licensed
POST	/activate/#key	Activate VMX via purchased key
GET	/random	Return a random image from the models
GET	/models/#ModelId/image.jpg	Visualize model as a mean image
GET	/models/#ModelId/data_set.json	Extract Learning Data Set
GET	/models/#ModelId/model.data	Extract full model binary file
GET	/models/#ModelId/compiled.data	Extract compiled model binary file
GET	/models/#ModelId/data_set/first.jpg	Return first image in model
GET	/models/#ModelId/data_set/image.jpg	Return next image in model
GET	/models/#ModelId/data_set/random.jpg	Return random image in model

## VMX v2.x Maxfactor:
UI:
http://seeubaby.blippar.com/api/vision/vmx2/maxfactor/#/
VMX API
GET	/session	List open sessions
http://seeubaby.blippar.com/api/vision/vmx2/maxfactor/sessions
GET	/model	List available models
http://seeubaby.blippar.com/api/vision/vmx2/maxfactor/models
POST	/session	Create a new session
http://seeubaby.blippar.com/api/vision/vmx2/maxfactor/session

http://seeubaby.blippar.com/api/vision/vmx2/maxfactor/check
