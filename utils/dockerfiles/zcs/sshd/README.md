# SSH

A ssh container to connect to your VPSA. You will have access to mounted block and NAS shares.


## Run example

Issue ssh from a server connect to the frontend private network. (Sorry, public ip's are not accessible in public clouds.)

```

$ sudo -i
$ ssh -p 9222 <your-vpsa-ip-address>

```

The root password is "zadara".  You probably will want to change it if you want to prevent unwanted access to this container.
