##VMX: The vision.ai object detection/recognition server

    VMX makes computer vision easy.  Docker makes bundling complex
    applications easy.  The vmx-docker-manager lets you run the VMX
    Object Detection server on a Linux computer, or a (Mac/Windows)
    machine via boot2docker. 

##Requirements
    64 bit Linux and Docker >= 1.2, root permissions to run Docker
    boot2docker for Mac or Windows

##Instructions

Either clone the repo

    git clone https://github.com/VISIONAI/vmx-docker-manager.git

Or download the shell script directly: https://raw.githubusercontent.com/VISIONAI/vmx-docker-manager/master/vmx

    Usage: vmx COMMAND [OPTIONS]

    Commands:
        start PORT: Start vmx on localhost:PORT
        stop:       Stop vmx
        enter:      Shell access to a Docker container for inspecting volumes
        init :      Download and start the containers that have the pre-reqs, mcr, etc.
                    (This is done automatically the first time you start vmx)
        update TAG: Update your vmx (will stop vmx first). TAG must be
                    "latest" or "dev", defaults to "latest" when TAG is empty
        backup BACKUP: Backup your vmx data (models and config file) to folder BACKUP
        restore BACKUP: Restore from a backup        

##Example:
 Run vmx on port 3000
    
    $  ./vmx start 3000

 Stop vmx

    $ ./vmx stop

 Backup your data to vmx-docker-manager/.vmx-backup/

    $  ./vmx backup
    
 Restore from a backup

    $  ./vmx restore .vmx-backup/2014_FriNov21_20_04
 
 Update vmx to latest (stable) version

    $  ./vmx update

or 

    $  ./vmx update latest

 Update vmx to dev version

    $  ./vmx update dev

   
##Permissions

vmx-docker-manager uses Docker which requires permission to run as root, or the
user to be added to the `docker` group.

To make sure Docker is installed and running correctly on your machine, you can first
try a simple Docker command such as:

```
docker run -t -i --rm ubuntu echo "Docker runs"
```

##Explanation

vmx-docker-manager automatically downloads all dependencies.  In
general, VMX requires a few files/folder to be available:
 - MATLAB MCR (Matlab Compiler Runtime)
 - Mount points for:
   - Session Information
   - Local Model storage
 - Certain open source libraries

The VMX Docker manager is a set of sane defaults and configurations
that store user data (session and models) in seperate volumes at
/vmx/sessions and /vmx/models; and loads the binaries into /vmx/build.
Those mount points, along with the matlab MCR dependency are run
within the context of an Ubuntu 14.04 with the required libraries.

The Dockerfiles that build the referenced Docker images can be found here:

- https://github.com/VISIONAI/docker-mcr-2014a
- https://github.com/VISIONAI/docker-vmx-userdata
- https://github.com/VISIONAI/docker-vmx-middle
- https://github.com/VISIONAI/docker-vmx-server
- https://github.com/VISIONAI/docker-vmx-appbuilder

## Downloading and sharing VMX models

```
Usage: models.sh COMMAND [OPTIONS]

Commands:
    download [a] [b] [c]: Download models from remote server models.vision.ai into incoming folder
    import:   Import models from incoming folder into VMX container
    upload model_name:   Upload models to a remote location via SSH

Examples:
 - Download all publicly available models from models.vision.ai
    $  ./models download -all

 - Download a,b,c from models.vision.ai
    $  ./models download a b c

 - Import downloaded models into local VMX
    $  ./models import

 - Upload a model called gesture-pack-12
    $  ./models upload gesture-pack-12
```

## Extra: Running VMX over HTTPS

If you are a **vision.ai administrator** and you want to run VMX on a vision.ai
subdomain such as `megavmx.vision.ai`, you'll need to checkout the
`vision.ai` branch of the vmx-docker-manager

```
cd ~
git clone https://github.com/VISIONAI/vmx-docker-manager.git
cd vmx-docker-manager
git checkout vision.ai
./vmx start 3000
```

What the vision.ai branch does is the following: make sure that VMX is
only accessible from 127.0.0.1, and set the VIRTUAL_HOST environment
variable.

You should also use the private nginx-ssl-proxy script as follows:

```
cd ~
git clone https://github.com/VISIONAI/nginx-ssl-proxy
cd nginx-ssl-proxy
./proxy.sh
```