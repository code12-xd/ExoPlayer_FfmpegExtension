package com.google.android.exoplayer2.ext.ffmpeg.bridge;

import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ext.ffmpeg.FfmpegLibrary;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MediaContainerDemuxer {
  private final String TAG = MediaContainerDemuxer.class.getSimpleName();

  public MediaContainerDemuxer() {
    mNative = FfmpegLibrary.getInterface();
    mNative.setInputReader(this);
  }

  private String mUrl;
  public void setUrl(String url) {
    mUrl = url;
  }

  public void init(ExtractorOutput output) {
    mEndOfExtractorInput = false;
    if (mTempBuffer == null) {
      mTempBuffer = new byte[TEMP_BUFFER_SIZE];
    }
    mOutput.init(output);
  }

  public int prepare(ExtractorInput input) {
    mInput = input;
    return mNative.open(mOutput, "");
  }

  public int read(ByteBuffer target) throws IOException, InterruptedException {
    int byteCount = target.remaining() - 1;
    Log.i(TAG, "count = " + byteCount + "  mBuffer = " + mBufferData);
    if (mBufferData != null) {
      byteCount = Math.min(byteCount, mBufferData.remaining());
      int originalLimit = mBufferData.limit();
      mBufferData.limit(mBufferData.position() + byteCount);
      target.put(mBufferData);
      mBufferData.limit(originalLimit);
    } else if (mInput != null) {
      ExtractorInput extractorInput = this.mInput;
      byte[] tempBuffer = Util.castNonNull(this.mTempBuffer);
      byteCount = Math.min(byteCount, TEMP_BUFFER_SIZE);
      int read = readFromExtractorInput(extractorInput, tempBuffer, /* offset= */ 0, byteCount);
      if (read < 4) {
        // Reading less than 4 bytes, most of the time, happens because of getting the bytes left in
        // the buffer of the input. Do another read to reduce the number of calls to this method
        // from the native code.
        read +=
            readFromExtractorInput(
                extractorInput, tempBuffer, read, /* length= */ byteCount - read);
      }
      byteCount = read;
      try {
        target.put(mTempBuffer, 0, byteCount);
        return byteCount;
      } catch (Exception e) {
        e.printStackTrace();
        Log.i(TAG, "exception = " + e.getMessage());
      }
      Log.i(TAG, "target = " + Arrays.toString(target.array()));
    } else {
      return -1;
    }
    return byteCount;
  }

  private int readFromExtractorInput(
      ExtractorInput extractorInput, byte[] tempBuffer, int offset, int length)
      throws IOException, InterruptedException {
    int read = extractorInput.read(tempBuffer, offset, length);
    if (read == C.RESULT_END_OF_INPUT) {
      mEndOfExtractorInput = true;
      read = 0;
    }
    return read;
  }

  private FfmpegNative mNative;
  @Nullable private ByteBuffer mBufferData;
  @Nullable private ExtractorInput mInput;
  @Nullable private OutputWriter mOutput = new OutputWriter();
  @Nullable private byte[] mTempBuffer;
  private boolean mEndOfExtractorInput;

  private static final int TEMP_BUFFER_SIZE = 8192; // The same buffer size.
}
