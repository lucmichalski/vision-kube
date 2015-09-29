echo 'Welcome to Mac Builder of VMX Webcam'

mkdir -p ./Contents/MacOS/
mkdir -p ./Contents/Frameworks/

rm ./Contents/MacOS/*
rm ./Contents/Frameworks/*
#D=~/projects/VMXserver/build/VMXserver.app/
#D=~/projects/vmxmiddle/dist/VMX.app/
D=.
#F=$D/Contents/MacOS/VMXwebcam2
#G=$D/Contents/MacOS/VMXwebcam
F=.VMXwebcam
G=VMXwebcam
#D=.
#F=.

g++ -v -std=c++11 -I /opt/local/include -L/opt/local/lib -lopencv_core -lopencv_highgui -O3 -o ${F} VMXwebcam.cpp

#g++-mp-4.7 -v -std=c++11 -I /opt/local/include -L/opt/local/lib -lopencv_core -lopencv_highgui -o VMXvideo VMXvideo.cpp
#g++ -std=c++11 `pkg-config opencv --cflags --libs` VMXwebcam.cpp -o VMXwebcam
go build -o ${G} VMXwebcam.go
#exit
#clean up libs
LIBS=`otool -L ${F} | grep "\t" | grep "/opt/local/lib" | awk '{print($1)}'`

echo 'libs are ' $LIBS
echo 'Building VMXwebcam lib dependencies (takes a few seconds)'
for i in $LIBS; do
    echo "i is" $i
    LOCAL_LIB='@executable_path/../Frameworks/'`basename $i`
    #echo "LL is" $LOCAL_LIB
    echo install_name_tool -change $i $LOCAL_LIB $F
    install_name_tool -change $i $LOCAL_LIB $F
    cp ${i} $D/Contents/Frameworks/
    LIBS2=`otool -L ${i} | grep "\t" | grep "/opt/local/lib" | awk '{print($1)}'`
    for j in $LIBS2; do
        #echo "i j is " $i $j
        cp ${j} $D/Contents/Frameworks/
        LIBS3=`otool -L ${j} | grep "\t" | grep "/opt/local/lib" | awk '{print($1)}'`
        for k in $LIBS3; do
            #echo "j k is " $j $k
            cp ${k} $D/Contents/Frameworks/
            LIBS4=`otool -L ${k} | grep "\t" | grep "/opt/local/lib" | awk '{print($1)}'`
            for l in $LIBS4; do
                #echo "k l is " $k $l
                cp ${l} $D/Contents/Frameworks/
                LIBS5=`otool -L ${l} | grep "\t" | grep "/opt/local/lib" | awk '{print($1)}'`
                for m in $LIBS5; do
                    #echo "l m is " $l $m
                    cp ${m} $D/Contents/Frameworks/
                done

                
            done
            
        done
    done
done

### go over all libs and replace /opt/local/bin with @executable_path\/..\/Frameworks

LIBS=`find $D/Contents/Frameworks/ -type f`
for i in $LIBS; do
    LIBS2=`otool -L ${i} | grep "\t" | grep "/opt/local/lib" | awk '{print($1)}'`
    for j in $LIBS2; do
        #LOCAL_LIB='./'`basename $j`
        LOCAL_LIB='@executable_path/../Frameworks/'`basename $j`
        install_name_tool -change $j $LOCAL_LIB $i
    done    
done

#update local id of library
cd $D/Contents/Frameworks/
LIBS=`ls`
for i in $LIBS; do
    install_name_tool -id $i $i
done
cd -

#copy over
mv $F ./Contents/MacOS
mv $G ./Contents/MacOS
