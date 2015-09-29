////////////////////////////////////////////////////////////////////////////
//    File:        io.h
//    Author:      Ken Chatfield
//    Description: Functions to convert to/from proto format
////////////////////////////////////////////////////////////////////////////

#ifndef CPUVISOR_UTILS_IO_H_
#define CPUVISOR_UTILS_IO_H_

#include <vector>
#include <string>
#include <fstream>

#include <glog/logging.h>

#include <opencv2/opencv.hpp>

#include "cpuvisor_srv.pb.h"

using google::protobuf::Message;

namespace cpuvisor {
  int64_t getTextFileLineCount(const std::string& text_path);

  void writeFeatsToProto(const cv::Mat& feats, const std::vector<std::string>& paths,
                         const std::string& proto_path);
  void writeChunkIndexToProto(const std::vector<std::string>& chunk_fnames,
                              const size_t feat_num, const size_t feat_dim,
                              const std::string& proto_path);
  bool readFeatsFromProto(const std::string& proto_path,
                          cv::Mat* feats, std::vector<std::string>* paths);

  void writeModelToProto(const cv::Mat& model, const std::string& proto_path);
  bool readModelFromProto(const std::string& proto_path, cv::Mat* model);

  bool readProtoFromTextFile(const std::string& proto_path, Message* proto);
  void writeProtoToTextFile(const std::string& proto_path, const Message& proto);
  bool readProtoFromBinaryFile(const std::string& proto_path, Message* proto);
  void writeProtoToBinaryFile(const std::string& proto_path, const Message& proto);

}

#endif
