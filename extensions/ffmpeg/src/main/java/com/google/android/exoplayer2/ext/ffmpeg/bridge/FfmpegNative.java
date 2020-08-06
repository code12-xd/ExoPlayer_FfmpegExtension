package com.google.android.exoplayer2.ext.ffmpeg.bridge;

import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FfmpegNative {
    private final String TAG = FfmpegNative.class.getSimpleName();

    public FfmpegNative() {
        mNativeContext = _init();
    }

    public void setInputReader(MediaContainerDemuxer r) {
        mReader = r;
    }

    /**
     * Reads up to {@code length} bytes from the data source.
     *
     * <p>This method blocks until at least one byte of data can be read, the end of the input is
     * detected or an exception is thrown.
     *
     * @param target A target {@link ByteBuffer} into which data should be written.
     * @return Returns the number of bytes read, or -1 on failure. If all of the data has already been
     *     read from the source, then 0 is returned.
     */
    @SuppressWarnings("unused") // Called from native code.
    public int read(ByteBuffer target) throws IOException, InterruptedException {
        if (mReader == null) {
            Log.i(TAG, "reader not init!");
            return 0;
        }
        return mReader.read(target);
    }

    public int open(OutputWriter outputWriter, String url) {
        return _open(mNativeContext, outputWriter, url);
    }

    public long codecInitialize(String codecName, Format format) {
        return _codecInit(mNativeContext, codecName);
    }

    public int decode(ByteBuffer inputData, int inputSize,
        ByteBuffer outputData, int outputSize) {
        return _decode(mNativeContext, inputData, inputSize, outputData, outputSize);
    }

    public int getChannelCount() { return _getChannelCount(mNativeContext); }

    public int getSampleRate() { return _getSampleRate(mNativeContext); }

    public long reset(@Nullable byte[] extraData) { return _reset(mNativeContext, extraData); }

    public void release() { _release(mNativeContext); }


    private native long _init();
    private native int _open(long c, OutputWriter outputWriter, String url);

    private native long _codecInit(long c, String codecName);
    private native int _decode(long context, ByteBuffer inputData, int inputSize,
        ByteBuffer outputData, int outputSize);
    private native int _getChannelCount(long context);
    private native int _getSampleRate(long context);
    private native long _reset(long context, @Nullable byte[] extraData);
    private native void _release(long context);

    private long mNativeContext;
    private MediaContainerDemuxer mReader;
}
