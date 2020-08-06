package com.google.android.exoplayer2.ext.ffmpeg.bridge;

import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public class OutputWriter {
  private final String TAG = OutputWriter.class.getSimpleName();

  public OutputWriter() {}

  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0, C.TRACK_TYPE_VIDEO);
    output.endTracks();
//    Format f = Format.createVideoSampleFormat(/* id= */ null,
//        MimeTypes.BASE_TYPE_VIDEO + "/rv40",
//        /* codecs= */ null,
//        /* bitrate= */ Format.NO_VALUE,
//        /* maxInputSize= */ Format.NO_VALUE,
//        0,
//        0,
//        Format.NO_VALUE,
//        /* initializationData= */ null,
//        /* drmInitData= */ Format.NO_VALUE,
//        /* selectionFlags= */ 0,
//        /* language= */ null);
    Format f = Format.createAudioSampleFormat(null,
        MimeTypes.BASE_TYPE_AUDIO + "/cook", null, Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);
    trackOutput.format(f);
  }

  @SuppressWarnings("unused") // Called from native code.
  public int sampleData(byte[] buf, int bytesLeft, int ttype) {
    Log.i(TAG, "size = " + bytesLeft + "  type = " + ttype);
    if (ttype == 1) // 0 - video, 1 - audio
      trackOutput.sampleData(new ParsableByteArray(buf, bytesLeft), bytesLeft);
    return bytesLeft;
  }

  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;
  private int pendingOutputBytes;
}
