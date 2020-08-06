/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.ffmpeg;

import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.decoder.SimpleOutputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * FFmpeg audio decoder.
 */
/* package */ final class FfmpegVideoDecoder
    extends SimpleDecoder<VideoDecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegVideoDecoderException> {

  private final String TAG = FfmpegVideoDecoder.class.getSimpleName();

  // Output buffer sizes when decoding PCM mu-law streams, which is the maximum FFmpeg outputs.
  private static final int OUTPUT_BUFFER_SIZE_16BIT = 65536;
  private static final int OUTPUT_BUFFER_SIZE_32BIT = OUTPUT_BUFFER_SIZE_16BIT * 2;

  // Error codes matching ffmpeg_jni.cc.
  private static final int DECODER_ERROR_INVALID_DATA = -1;
  private static final int DECODER_ERROR_OTHER = -2;

  private final String codecName;
  @Nullable private final byte[] extraData;
  private final @C.Encoding int encoding;
  private final int outputBufferSize;

  private long nativeContext; // May be reassigned on resetting the codec.
  private boolean hasOutputFormat;
  private volatile int channelCount;
  private volatile int sampleRate;

  @C.VideoOutputMode private volatile int outputMode;

  private FfmpegDecoder mNative = new FfmpegDecoder();

  public FfmpegVideoDecoder(
      int numInputBuffers,
      int numOutputBuffers,
      int initialInputBufferSize,
      Format format,
      boolean outputFloat)
      throws FfmpegVideoDecoderException {
    super(new VideoDecoderInputBuffer[numInputBuffers], new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!FfmpegLibrary.isAvailable()) {
      throw new FfmpegVideoDecoderException("Failed to load decoder native libraries.");
    }
    Assertions.checkNotNull(format.sampleMimeType);
    codecName = Assertions.checkNotNull(FfmpegLibrary.getCodecName(format.sampleMimeType));
    extraData = getExtraData(format.sampleMimeType, format.initializationData);
    encoding = outputFloat ? C.ENCODING_PCM_FLOAT : C.ENCODING_PCM_16BIT;
    outputBufferSize = outputFloat ? OUTPUT_BUFFER_SIZE_32BIT : OUTPUT_BUFFER_SIZE_16BIT;
    nativeContext =
        mNative.ffmpegInitialize(codecName, extraData, outputFloat, format.sampleRate, format.channelCount);
    if (nativeContext == 0) {
      throw new FfmpegVideoDecoderException("Initialization failed.");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  public String getName() {
    return "ffmpeg" + FfmpegLibrary.getVersion() + "-" + codecName;
  }

  @Override
  protected VideoDecoderInputBuffer createInputBuffer() {
    return new VideoDecoderInputBuffer();
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  protected FfmpegVideoDecoderException createUnexpectedDecodeException(Throwable error) {
    return new FfmpegVideoDecoderException("Unexpected decode error", error);
  }

  @Nullable
  @Override
  protected FfmpegVideoDecoderException decode(
      VideoDecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      nativeContext = mNative.ffmpegReset(nativeContext, extraData);
      if (nativeContext == 0) {
        return new FfmpegVideoDecoderException("Error resetting (see logcat).");
      }
    }
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    outputBuffer.init(inputBuffer.timeUs, outputMode, /* supplementalData= */ null);
    int result = 0;//TODO:???? mNative.ffmpegDecode(nativeContext, inputData, inputSize, outputData, outputBufferSize);
    Log.i(TAG, "inputsz = " + inputSize + "  result = " + result);
    if (result == DECODER_ERROR_INVALID_DATA) {
      // Treat invalid data errors as non-fatal to match the behavior of MediaCodec. No output will
      // be produced for this buffer, so mark it as decode-only to ensure that the audio sink's
      // position is reset when more audio is produced.
      outputBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);
      return null;
    } else if (result == DECODER_ERROR_OTHER) {
      return new FfmpegVideoDecoderException("Error decoding (see logcat).");
    }
    if (!hasOutputFormat) {
      channelCount = mNative.ffmpegGetChannelCount(nativeContext);
      sampleRate = mNative.ffmpegGetSampleRate(nativeContext);
      if (sampleRate == 0 && "alac".equals(codecName)) {
        Assertions.checkNotNull(extraData);
        // ALAC decoder did not set the sample rate in earlier versions of FFMPEG.
        // See https://trac.ffmpeg.org/ticket/6096
        ParsableByteArray parsableExtraData = new ParsableByteArray(extraData);
        parsableExtraData.setPosition(extraData.length - 4);
        sampleRate = parsableExtraData.readUnsignedIntToInt();
      }
      hasOutputFormat = true;
    }
    //TODO:????
//    outputData.position(0);
//    outputData.limit(result);
    return null;
  }

  @Override
  public void release() {
    super.release();
    mNative.ffmpegRelease(nativeContext);
    nativeContext = 0;
  }

  /** Returns the channel count of output audio. */
  public int getChannelCount() {
    return channelCount;
  }

  /** Returns the sample rate of output audio. */
  public int getSampleRate() {
    return sampleRate;
  }

  /**
   * Returns the encoding of output audio.
   */
  public @C.Encoding int getEncoding() {
    return encoding;
  }

  /**
   * Returns FFmpeg-compatible codec-specific initialization data ("extra data"), or {@code null} if
   * not required.
   */
  private static @Nullable byte[] getExtraData(String mimeType, List<byte[]> initializationData) {
    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
      case MimeTypes.AUDIO_OPUS:
        return initializationData.get(0);
      case MimeTypes.AUDIO_ALAC:
      case MimeTypes.AUDIO_VORBIS:
      default:
        // Other codecs do not require extra data.
        return null;
    }
  }
}
