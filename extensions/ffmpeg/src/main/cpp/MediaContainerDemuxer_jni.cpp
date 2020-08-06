//
// Created by wuhan on 2019-11-09.
//

#include "MediaContainerDemuxer_jni.h"
#include "nativehelper/ScopedLocalRef.h"
#include <pthread.h>

#ifndef NELEM
# define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavformat/avio.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
#include <libavutil/file.h>
}

static const char *const kClassPathName = "com/google/android/exoplayer2/ext/ffmpeg/bridge/FfmpegNative";
static pthread_mutex_t *codec_lock = nullptr;
static JavaVM *g_JavaVM;

#define ERROR_STRING_BUFFER_LENGTH 256
void logError(const char *functionName, int errorNumber) {
  char *buffer = (char *) malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  ALOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

class JavaDataSource {
 public:
  void setJavaClz(JNIEnv *env, jobject javaClz, jobject outer) {
      this->env = env;
      this->mJavaClz = env->NewGlobalRef(javaClz);
      this->mOutClz = env->NewGlobalRef(outer);
      if (mid == NULL) {
          jclass cls = env->GetObjectClass(mJavaClz);
          mid = env->GetMethodID(cls, "read", "(Ljava/nio/ByteBuffer;)I");
      }
      if (mOutSampleData == NULL) {
          jclass cls = env->GetObjectClass(mOutClz);
          mOutSampleData = env->GetMethodID(cls, "sampleData", "([BII)I");
      }
      ALOGD("out: %p, sample: %p", mOutClz, mOutSampleData);
  }

//  void setJavaClz(JNIEnv *env, jobject javaClz) {
//      this->env = env;
//      this->mJavaClz = javaClz;
//      if (mid == NULL) {
//          jclass cls = env->GetObjectClass(mJavaClz);
//          mid = env->GetMethodID(cls, "read", "(Ljava/nio/ByteBuffer;)I");
//      }
//      ALOGD("out: %p, sample: %p", mOutClz, mOutSampleData);
//  }

  ssize_t readAt(off64_t offset, void *const data, size_t size) {
      JNIEnv* envl = this->env;
      g_JavaVM->AttachCurrentThread(&envl, NULL);
      ALOGD("IN readAt!!  env: %p, mJavaClz: %p", envl, mJavaClz);
      jobject byteBuffer = envl->NewDirectByteBuffer(data, size);
      int result = envl->CallIntMethod(mJavaClz, mid, byteBuffer);
      //g_JavaVM->DetachCurrentThread();
      if (envl->ExceptionCheck()) {
          // Exception is thrown in Java when returning from the native call.
          result = -1;
      }
      return result;
  }

  int output(JNIEnv* envl, jobject javaClz, jobject outer, void* const data, int size, int type) {
    ALOGD("IN output!!  mOutClz: %p, outer: %p", mOutClz, outer);
//    JNIEnv* env = this->env;
//    g_JavaVM->AttachCurrentThread(&env, NULL);
    jbyte *by = (jbyte*)data;
    jbyteArray jarray = envl->NewByteArray(size+1);
    envl->SetByteArrayRegion(jarray, 0, size, by);

    jclass cls = env->GetObjectClass(mOutClz);
    jmethodID outSampleData = env->GetMethodID(cls, "sampleData", "([BII)I");
    int result = envl->CallIntMethod(outer, outSampleData, jarray, size, type);
    //g_JavaVM->DetachCurrentThread();
    ALOGD("result: %i", result);
    if (envl->ExceptionCheck()) {
      // Exception is thrown in Java when returning from the native call.
      result = -1;
    }
    return result;
  }

 public:
  int mPos = 0;

 private:
  JNIEnv *env;
  jobject mJavaClz;
  jobject mOutClz;
  jmethodID mid = NULL;
  jmethodID mOutSampleData = NULL;
};

struct Context {
  JavaDataSource* mSource;
  AVFormatContext* pFormatCtx;
  AVCodecContext* pCodecCtx = 0;
  int mStarted = 0;
  int mStatus = 0;

  Context() {
      mSource = new JavaDataSource();
      avformat_network_init();
      pFormatCtx = avformat_alloc_context();
  }

  ~Context() {
      avformat_free_context(pFormatCtx);
      delete mSource;
  }
};

void logFF(void *ptr, int level, const char *fmt, va_list vl) {
  va_list vl2;
  char *line = new char[128 * sizeof(char)];
  static int print_prefix = 1;
  va_copy(vl2, vl);
  av_log_format_line(ptr, level, fmt, vl2, line, 128, &print_prefix);
  va_end(vl2);
  line[127] = '\0';
  ALOGD("%s", line);
  delete line;
}

static jlong Native_init(JNIEnv *env, jobject thiz, jobject outer) {
    ALOGD("com_google_android_exoplayer2_ext_ffmpeg_bridge_Native_init");
    Context* context = new Context();
    av_log_set_callback(&logFF);
    return (long)context;
}

static int read_packet(void *opaque, uint8_t *buf, int buf_size) {
    Context* pc = (Context*)opaque;
    ALOGD("opaque = %p, buf = %p, buf_size = %i", opaque, buf, buf_size);
    if (!pc) return 0;

    return pc->mSource->readAt(0, buf, buf_size);
}

AVCodecContext *createContext(JNIEnv *env, AVCodec *codec, void* extraData, int size,
                              jboolean outputFloat, jint rawSampleRate,
                              jint rawChannelCount) {
  AVCodecContext *context = avcodec_alloc_context3(codec);
  if (!context) {
    ALOGD("Failed to allocate context.");
    return NULL;
  }
  context->request_sample_fmt =
      outputFloat ? AV_SAMPLE_FMT_FLT : AV_SAMPLE_FMT_S16;
  if (extraData) {
    context->extradata = (uint8_t *)extraData;
    context->extradata_size = size;
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_COOK ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    context->channels = rawChannelCount;
    context->channel_layout = av_get_default_channel_layout(rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    return NULL;
  }
  return context;
}

static jlong Native_open(
    JNIEnv *env, jobject thiz, jlong context, jobject outer, jstring url) {
    ALOGD("com_google_android_exoplayer2_ext_ffmpeg_bridge_Native_open");
    Context* pc = (Context*)context;
    AVFormatContext* pFormatCtx = pc->pFormatCtx;
    AVIOContext *avio_ctx = NULL;
    uint8_t *buffer = NULL, *avio_ctx_buffer = NULL;
    size_t buffer_size, avio_ctx_buffer_size = 8192;
    char *input_filename = "/mnt/sdcard/song/2.rmvb";
    int ret = 0;

    if (!pc->mStarted) {
      pc->mSource->setJavaClz(env, thiz, outer);
      avio_ctx_buffer = (uint8_t*)av_malloc(avio_ctx_buffer_size*4);
      if (!avio_ctx_buffer) {
          ret = AVERROR(ENOMEM);
          goto end;
      }
      ALOGD("avio_ctx_buffer = %p", avio_ctx_buffer);
      avio_ctx = avio_alloc_context(avio_ctx_buffer, avio_ctx_buffer_size,
                                    0, pc, &read_packet, NULL, NULL);
      if (!avio_ctx) {
          ret = AVERROR(ENOMEM);
          goto end;
      }
      pFormatCtx->pb = avio_ctx;
      pFormatCtx->flags = AVFMT_FLAG_CUSTOM_IO;
      ALOGD("avio_ctx = %i", avio_ctx);

      av_register_all();
      ret = avformat_open_input(&pFormatCtx, NULL, NULL, NULL);
      if (ret < 0) {
          char buf[1024] = {0};
          av_strerror(ret, buf, 1024);
          ALOGD("Could not open input err: %d %s", ret, buf);
          goto end;
      }
      ALOGD("open_input return %i", ret);

//    const char* inurl = env->GetStringUTFChars(url, NULL);
//    if(avformat_open_input(&pFormatCtx, input_filename, NULL, NULL) != 0){
//        ALOGD("Couldn't open input stream.\n");
//        return -1;
//    }

        ret = avformat_find_stream_info(pFormatCtx, NULL);
        if (ret < 0) {
            ALOGD("Could not find stream information\n");
            return ret;
        }
        ALOGD("find_stream return = %i", ret);

      end:
        /* note: the internal buffer could have changed, and be != avio_ctx_buffer */
//        if (avio_ctx)
//            av_freep(&avio_ctx->buffer);
//        avio_context_free(&avio_ctx);

        if (ret < 0) {
            ALOGD("Error occurred: %s\n", av_err2str(ret));
            return ret;
        }
        pc->mStarted = 1;
    }

    // read packets
    if (pc->mStatus == 0){
        //pc->mSource->setJavaClz(env, thiz, outer); //set again
        AVPacket pkt1, *pkt = &pkt1;
        pc->mStatus = av_read_frame(pFormatCtx, pkt);
        ALOGD("read frame: %d", pc->mStatus);

        /* check if packet is in play range specified by user, then queue, otherwise discard */
        int64_t stream_start_time = pFormatCtx->streams[pkt->stream_index]->start_time;
        int64_t pkt_ts = pkt->pts == AV_NOPTS_VALUE ? pkt->dts : pkt->pts;
        //TODO:??
    //      pkt_in_play_range = duration == AV_NOPTS_VALUE ||
    //          (pkt_ts - (stream_start_time != AV_NOPTS_VALUE ? stream_start_time : 0)) *
    //              av_q2d(ic->streams[pkt->stream_index]->time_base) -
    //              (double)(start_time != AV_NOPTS_VALUE ? start_time : 0) / 1000000
    //              <= ((double)duration / 1000000);
        AVStream* pkstream = pFormatCtx->streams[pkt->stream_index];
        pc->mSource->output(env, thiz, outer, pkt->data, pkt->size, pkstream->codecpar->codec_type);
    }

    return pc->mStatus;
//    char buf[1024] = {0};
//    pc->mSource->output(env, thiz, outer, buf, 1024, 1);
//    return 0;
}

static jlong Native_codecInit(JNIEnv *env, jobject cls, jlong context, jstring codecn) {
    int ret = 0;
    Context* pc = (Context*)context;
    AVFormatContext* pFormatCtx = pc->pFormatCtx;
    ALOGD("com_google_android_exoplayer2_ext_ffmpeg_bridge_Native_codecInit");

    //pc->mSource->setJavaClz(env, cls);

    ALOGD("number of streams = %i", pFormatCtx->nb_streams);
    for (int i = 0; i < pFormatCtx->nb_streams; i++) {
        AVStream *st = pFormatCtx->streams[i];
        enum AVMediaType type = st->codecpar->codec_type;
        ALOGD("codec id: %d,  channels: %d,  sample rate: %d",
              st->codecpar->codec_id,
              st->codecpar->channels,
              st->codecpar->sample_rate);
        if (type == AVMEDIA_TYPE_AUDIO) {
            ALOGD("extra: %s,  extra size: %d",
                  st->codecpar->extradata,
                  st->codecpar->extradata_size);
            AVCodec *codec = avcodec_find_decoder_by_name("cook");
            AVCodecContext* codec_context = avcodec_alloc_context3(codec);
            int result = avcodec_parameters_to_context(codec_context, st->codecpar);
            if (result < 0) {
              logError("avcodec_parameters_to_context", result);
              return NULL;
            }
            result = avcodec_open2(codec_context, codec, NULL);
            if (result < 0) {
                logError("avcodec_open2", result);
                return NULL;
            }
            pc->pCodecCtx = codec_context;
            ALOGD("codec context: %p", codec_context);
        }
    }
    return (long)pc->pCodecCtx;
}

static const int DECODER_ERROR_INVALID_DATA = -1;
static const int DECODER_ERROR_OTHER = -2;

int decodePacket(AVCodecContext *context, AVPacket *packet, uint8_t *outputBuffer, int outputSize) {
    int result = 0;
    // Queue input data.
    result = avcodec_send_packet(context, packet);
    if (result) {
        logError("avcodec_send_packet", result);
        return result == AVERROR_INVALIDDATA ? DECODER_ERROR_INVALID_DATA
                                             : DECODER_ERROR_OTHER;
    }

    // Dequeue output data until it runs out.
    int outSize = 0;
    while (true) {
        AVFrame *frame = av_frame_alloc();
        if (!frame) {
            ALOGD("Failed to allocate output frame.");
            return -1;
        }
        result = avcodec_receive_frame(context, frame);
        if (result) {
            av_frame_free(&frame);
            if (result == AVERROR(EAGAIN)) {
                break;
            }
            logError("avcodec_receive_frame", result);
            return result;
        }

        // Resample output.
        AVSampleFormat sampleFormat = context->sample_fmt;
        int channelCount = context->channels;
        int channelLayout = context->channel_layout;
        int sampleRate = context->sample_rate;
        int sampleCount = frame->nb_samples;
        int dataSize = av_samples_get_buffer_size(NULL, channelCount, sampleCount,
                                                  sampleFormat, 1);
        SwrContext *resampleContext;
        if (context->opaque) {
            resampleContext = (SwrContext *)context->opaque;
        } else {
            resampleContext = swr_alloc();
            av_opt_set_int(resampleContext, "in_channel_layout",  channelLayout, 0);
            av_opt_set_int(resampleContext, "out_channel_layout", channelLayout, 0);
            av_opt_set_int(resampleContext, "in_sample_rate", sampleRate, 0);
            av_opt_set_int(resampleContext, "out_sample_rate", sampleRate, 0);
            av_opt_set_int(resampleContext, "in_sample_fmt", sampleFormat, 0);
            // The output format is always the requested format.
            av_opt_set_int(resampleContext, "out_sample_fmt",
                           context->request_sample_fmt, 0);
            result = swr_init(resampleContext);
            if (result < 0) {
                logError("swr_init", result);
                av_frame_free(&frame);
                return -1;
            }
            context->opaque = resampleContext;
        }
        int inSampleSize = av_get_bytes_per_sample(sampleFormat);
        int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
        int outSamples = swr_get_out_samples(resampleContext, sampleCount);
        int bufferOutSize = outSampleSize * channelCount * outSamples;
        if (outSize + bufferOutSize > outputSize) {
            ALOGD("Output buffer size (%d) too small for output data (%d).",
                 outputSize, outSize + bufferOutSize);
            av_frame_free(&frame);
            return -1;
        }
        result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                             (const uint8_t **)frame->data, frame->nb_samples);
        av_frame_free(&frame);
        if (result < 0) {
            logError("swr_convert", result);
            return result;
        }
        int available = swr_get_out_samples(resampleContext, 0);
        if (available != 0) {
            ALOGD("Expected no samples remaining after resampling, but found %d.",
                 available);
            return -1;
        }
        outputBuffer += bufferOutSize;
        outSize += bufferOutSize;
    }
    return outSize;
}

static jint Native_decode(JNIEnv *env, jobject thiz, jlong ctx, jobject inputData,
                          jint inputSize, jobject outputData, jint outputSize) {
    Context* pc = (Context*)ctx;
    AVFormatContext* pFormatCtx = pc->pFormatCtx;
    AVCodecContext* pCodecCtx = pc->pCodecCtx;
    ALOGD("com_google_android_exoplayer2_ext_ffmpeg_bridge_Native_decode");

    //pc->mSource->setJavaClz(env, thiz);

    if (!pCodecCtx) {
        ALOGD("Context must be non-NULL.");
        return -1;
    }
    if (!inputData || !outputData) {
        ALOGD("Input and output buffers must be non-NULL.");
        return -1;
    }
    if (inputSize < 0) {
        ALOGD("Invalid input buffer size: %d.", inputSize);
        return -1;
    }
    if (outputSize < 0) {
        ALOGD("Invalid output buffer length: %d", outputSize);
        return -1;
    }
    uint8_t *inputBuffer = (uint8_t *) env->GetDirectBufferAddress(inputData);
    uint8_t *outputBuffer = (uint8_t *) env->GetDirectBufferAddress(outputData);
//    AVPacket packet;
//    av_init_packet(&packet);
//    packet.data = inputBuffer;
//    packet.size = inputSize;

    AVPacket pkt1, *pkt = &pkt1;
    pc->mStatus = av_read_frame(pFormatCtx, pkt);
    ALOGD("read frame: %d", pc->mStatus);

    return decodePacket(pCodecCtx, pkt, outputBuffer, outputSize);//&packet
}

static jint Native_getChannelCount(JNIEnv *, jobject, jlong) {
    return 2;
}

static jint Native_getSampleRate(JNIEnv *, jobject, jlong) {
    return 44100;
}

static jlong Native_reset(JNIEnv *, jobject, jlong, jbyteArray) {
    return 0;
}

static void Native_release(JNIEnv *, jobject, jlong) {
}

static const JNINativeMethod gMethods[] = {
        {"_init",                   "()J",
                                    (void *) Native_init},
        {"_open",                   "(JLcom/google/android/exoplayer2/ext/ffmpeg/bridge/OutputWriter;Ljava/lang/String;)I",
                                    (void *) Native_open},
        {"_codecInit",              "(JLjava/lang/String;)J",
                                    (void *) Native_codecInit},
        {"_decode",                 "(JLjava/nio/ByteBuffer;ILjava/nio/ByteBuffer;I)I",
                                    (void *) Native_decode},
        {"_getChannelCount",        "(J)I",
                                    (void *) Native_getChannelCount},
        {"_getSampleRate",          "(J)I",
                                    (void *) Native_getSampleRate},
        {"_reset",                  "(J[B)J",
                                    (void *) Native_reset},
        {"_release",                "(J)V",
                                    (void *) Native_release},
};

/*----------------------------------------------------------------------
|    JNI_OnLoad
+---------------------------------------------------------------------*/
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;

    if (vm == nullptr) {
        return RESULT_FAIL;
    }

    g_JavaVM = vm;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return RESULT_FAIL;
    }
    if (env == nullptr) {
        return RESULT_FAIL;
    }

    if (jniRegisterNativeMethods(env, kClassPathName, gMethods, NELEM(gMethods)) < 0) {
        return RESULT_FAIL;
    }
    // success -- return valid version number
    return JNI_VERSION_1_4;
}


void JNI_OnUnload(JavaVM *vm, void *reserved) {
    if (codec_lock != nullptr) {
        g_JavaVM = nullptr;
        pthread_mutex_destroy(codec_lock);
        free(codec_lock);
        codec_lock = nullptr;
    }
}