# iozone

Dockerized IOzone app built on top of official Ubuntu images.

Image specific:

- [iozone](http://www.iozone.org)

## Run example

```bash
$ sudo docker run -it threadx/iozone

root@fe0b7b4a178b:/#  iozone -R -l 5 -u 5 -r 64k -s 100m -F f1 f2 f3 f4 f5
```

