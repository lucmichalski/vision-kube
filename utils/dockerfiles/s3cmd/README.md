s3cmd
-----
This container implements the [AWS S3](http://aws.amazon.com/s3/) s3cmd as a microservice. I use it to download log files stored on S3 and process them locally from within the container. When done I just exit to make the cleanup real easy.

At a minimum, you will need to import your .s3cfg file from your host shares into the container. Import other directories as needed.

### Usage

```
docker run -it \
	-v $HOME/.s3cfg:/.s3cfg \
	-v $HOME:/home/$USER \
	s3cmd
```
#### Some Container Commands
```
# s3cmd ls s3://<bucketname>
# s3cmd put file s3://<bucketname>/
# s3cmd get s3://<bucketname>/file

```
