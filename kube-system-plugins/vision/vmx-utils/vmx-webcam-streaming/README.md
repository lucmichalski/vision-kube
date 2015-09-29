Welcome to VMXwebcam.

This simple OpenCV/Go program will serve your webcam or a movie file
over http. This standalone program is useful for interacting with the
rest of the VMX object detection system, but can also be easily
incorporated into any of your personal projects. This code is MIT
licensed, and there are both Mac and Linux compilation instructions
below.

### Turn camera 0 into an http server on port 3001

Usually my Mac's webcam is "camera 0", but when using virtual camera
devices, the webcam can become "camera 0" or "camera 2."

```
./VMXwebcam 0 :3001
```

Then to get an image, you can do:
``` curl http://localhost:3001 > image.jpg ```

Or just go to `http://localhost:3001` in your browser
and keep hitting refresh.

### Turn movie file a.mp4 into an http server on port 3002
```
./VMXwebcam /tmp/a.mp4 :3002
```


# Compiling

On Mac, you'll need to install OpenCV first.
```
./mac_builder.sh
```

On Linux, you'll need to install OpenCV, and then CMake is used for compilation.
```
./linux_builder.sh
```
