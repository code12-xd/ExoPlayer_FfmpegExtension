//
// Created by dangxin on 2020/7/28.
//
#ifndef EXOPLAYER_RELEASE_V2_JFORMAT_H
#define EXOPLAYER_RELEASE_V2_JFORMAT_H

#include "../../MediaContainerDemuxer_jni.h"

class JFormat {
 public:
  JFormat(JNIEnv* env) { mJniEnv = env; init(); }

 private:
  void init();

 private:
  JNIEnv* mJniEnv;
};
#endif //EXOPLAYER_RELEASE_V2_JFORMAT_H
