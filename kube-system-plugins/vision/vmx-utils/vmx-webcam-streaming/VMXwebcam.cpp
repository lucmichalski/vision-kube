#include "opencv2/opencv.hpp"
#include <string>

using namespace cv;
using namespace std;

int main(int argc, char** argv)
{

  if (argc !=2 ) {
    std::cout<<"Usage: "<<argv[0]<<" 0_or_movie_file"<<std::endl;
    return 1;
  }
  VideoCapture cap;

  bool webcam = false;
  std::string cam_input(argv[1]);
  if (cam_input.size()==1) {
    int cam_id = atoi(cam_input.c_str());
    cap.open(cam_id); 
    webcam = true;
  } else {
    cap.open(argv[1]); 
    webcam = false;
  }

  if(!cap.isOpened())  {
    std::cout<<"Problem opening camera "<<cam_input<<std::endl;
    return -1;
  }

  string input;
  std::cout<<"Ready: Type a .jpg filename and it will dump the image"<<std::endl;
  Mat frame2, frame;
  while (std::getline(std::cin,input)) {
    cap >> frame2;
    if (webcam)
      cv::flip(frame2,frame,1);
    else
      frame2.copyTo(frame);

    //vector<uchar> buffer;
    //imencode(".jpg",frame, buffer);
    //char tmpname[] = "/tmp/webcam.XXXXXX";
    //char* tmpfile = mkstemp(tmpname);
    //char* tmpfile = tmpnam ("/tmp/");
    //std::cout<<"tmpfile is " <<tmpfile<<" ";
    imwrite(input,frame);
    //for (int i = 0; i < buffer.size(); ++i) {
    //  std::cout<<buffer[i];
    //}
    //std::cout<<std::endl;
    std::cout<<"just wrote "<<input<<" of size "<<frame.size().height<<" x " <<frame.size().width<<std::endl;
  }
  return 0;
}
