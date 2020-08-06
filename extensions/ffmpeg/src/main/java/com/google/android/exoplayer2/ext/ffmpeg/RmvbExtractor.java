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

import static com.google.android.exoplayer2.util.Util.getPcmEncoding;

import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ext.ffmpeg.bridge.MediaContainerDemuxer;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.FlacMetadataReader;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Facilitates the extraction of data from the real container format.
 */
public final class RmvbExtractor implements Extractor {
  private final String TAG = RmvbExtractor.class.getSimpleName();

  /** Factory that returns one extractor which is a {@link RmvbExtractor}. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new RmvbExtractor()};


  private final ParsableByteArray outputBuffer;
  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;

  private MediaContainerDemuxer mDemuxer;

  public RmvbExtractor() {
    outputBuffer = new ParsableByteArray();
    mDemuxer = new MediaContainerDemuxer();
  }

  public void setUrl(String url) {
    mDemuxer.setUrl(url);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    //TODO: Now direct to set unseekable due to not support seek.
    output.seekMap(new SeekMap.Unseekable(/* durationUs */ C.TIME_UNSET));
    trackOutput = extractorOutput.track(0, C.TRACK_TYPE_AUDIO);//C.TRACK_TYPE_VIDEO);
    extractorOutput.endTracks();
    mDemuxer.init(output);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    final byte[] RM_TAG = {'.', 'R', 'M', 'F'};
    byte[] head = new byte[4];
    input.peek(head, 0, 4);
    return Arrays.equals(head, RM_TAG);
  }

  @Override
  public int read(final ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    int rt = mDemuxer.prepare(input);
    if (rt != 0)
      return Extractor.RESULT_END_OF_INPUT;
    return Extractor.RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
  }

  @Override
  public void release() {
  }

  private static void outputSample(
      ParsableByteArray sampleData, int size, long timeUs, TrackOutput output) {
    sampleData.setPosition(0);
    output.sampleData(sampleData, size);
    output.sampleMetadata(
        timeUs, C.BUFFER_FLAG_KEY_FRAME, size, /* offset= */ 0, /* encryptionData= */ null);
  }
}
