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
import java.nio.ByteBuffer;
import java.util.List;

/**
 * FFmpeg decoder.
 */
/* package */ final class FfmpegDecoder {

  private final String TAG = FfmpegDecoder.class.getSimpleName();

  public FfmpegDecoder() {}

  public native long ffmpegInitialize(
      String codecName,
      @Nullable byte[] extraData,
      boolean outputFloat,
      int rawSampleRate,
      int rawChannelCount);

  public native int ffmpegDecode(long context, ByteBuffer inputData, int inputSize,
      ByteBuffer outputData, int outputSize);
  public native int ffmpegGetChannelCount(long context);
  public native int ffmpegGetSampleRate(long context);

  public native long ffmpegReset(long context, @Nullable byte[] extraData);

  public native void ffmpegRelease(long context);

}
