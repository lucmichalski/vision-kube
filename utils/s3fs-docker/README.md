S3FS-docker
===========

A docker image with S3FS built and ready to use

Using 
-----

While you can use this image directly, it's probably more useful to
extend this container into your own.  You can use '/root/run' as a
wrapper which mounts S3, runs a command, and unmounts.

### Environment Variables

This image will mount bucket $AWS_BUCKET on /mnt/s3 using credentials
$AWSACCESSKEYID and $AWSSECRETACCESSKEY, then run the command passed
in to docker.


### Example

Show what is in an S3 bucket

     docker build -t withs3 .
     docker run --privileged=true -e AWS_BUCKET=mybucket -e AWSACCESSKEYID=somekey -e AWSSECRETACCESSKEY=somesecret withs3 ls /mnt/s3

#### Notes on Privileged

Allegedly, you should only need to run with '--cap-add SYS_ADMIN
--device /dev/fuse'.  However, that hasn't worked in my testing.