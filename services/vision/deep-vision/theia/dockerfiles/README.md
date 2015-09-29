# theia
See the world


## Training your own RNN on the Flickr or Coco datasets

### Fetch data
To download the datasets used for training, run
```
sh fetch-data.sh
```
Then move the unpacked data to the `data` folder in the `neuraltalk` directory from within the docker container.

### Train the RNN
Replacing `flickr30k` with `coco` or `flickr8k` as desired, run
```
python driver.py --dataset flickr30k
```

## Using a pretrained RNN from Caffe Model Zoo

## Classifying new images
