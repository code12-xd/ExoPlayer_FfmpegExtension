package com.google.android.exoplayer2.ext.ffmpeg;

import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.SimpleDecoderAudioRenderer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.SimpleDecoderVideoRenderer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Decodes and renders video using FFmpeg.
 */
public final class FfmpegVideoRenderer extends SimpleDecoderVideoRenderer {

  /** The number of input and output buffers. */
  private static final int NUM_BUFFERS = 16;
  /** The default input buffer size. */
  private static final int DEFAULT_INPUT_BUFFER_SIZE = 960 * 6;


  private @MonotonicNonNull FfmpegVideoDecoder decoder;

  /**
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public FfmpegVideoRenderer(long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify) {
    this(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        0,
        /* drmSessionManager= */ 0,
        /* playClearSamplesWithoutKeys= */ 0);
  }

  public FfmpegVideoRenderer(
      long allowedJoiningTimeMs,
      @Nullable Handler eventHandler,
      @Nullable VideoRendererEventListener eventListener,
      int maxDroppedFramesToNotify,
      int threads,
      int numInputBuffers,
      int numOutputBuffers) {
    super(
        allowedJoiningTimeMs,
        eventHandler,
        eventListener,
        maxDroppedFramesToNotify,
        /* drmSessionManager= */ null,
        /* playClearSamplesWithoutKeys= */ false);
  }

  @Override
  @FormatSupport
  protected int supportsFormatInternal(
      @Nullable DrmSessionManager<ExoMediaCrypto> drmSessionManager, Format format) {
    Assertions.checkNotNull(format.sampleMimeType);
    if (!FfmpegLibrary.isAvailable()) {
      return FORMAT_UNSUPPORTED_TYPE;
    } else if (format.sampleMimeType.endsWith("rv40")) {
      return RendererCapabilities.create(FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
    } else if (!supportsFormatDrm(drmSessionManager, format.drmInitData)) {
      return RendererCapabilities.create(FORMAT_UNSUPPORTED_DRM);
    } else {
      return RendererCapabilities.create(FORMAT_HANDLED, ADAPTIVE_SEAMLESS, TUNNELING_NOT_SUPPORTED);
    }
  }

  @Override
  @AdaptiveSupport
  public final int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
    return ADAPTIVE_NOT_SEAMLESS;
  }

  @Override
  protected FfmpegVideoDecoder createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
      throws FfmpegVideoDecoderException {
    int initialInputBufferSize =
        format.maxInputSize != Format.NO_VALUE ? format.maxInputSize : DEFAULT_INPUT_BUFFER_SIZE;
    decoder =
        new FfmpegVideoDecoder(
            NUM_BUFFERS, NUM_BUFFERS, initialInputBufferSize, format, false);//shouldUseFloatOutput(format)
    return decoder;
  }

  @Override
  protected void renderOutputBufferToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegVideoDecoderException {
    if (decoder == null) {
      throw new FfmpegVideoDecoderException(
          "Failed to render output buffer to surface: decoder is not initialized.");
    }
//TODO:????    decoder.renderToSurface(outputBuffer, surface);
    outputBuffer.release();
  }

  @Override
  protected void setDecoderOutputMode(@C.VideoOutputMode int outputMode) {
    if (decoder != null) {
      decoder.setOutputMode(outputMode);
    }
  }

  // PlayerMessage.Target implementation.
  @Override
  public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
    if (messageType == C.MSG_SET_SURFACE) {
      setOutputSurface((Surface) message);
    } else if (messageType == C.MSG_SET_VIDEO_DECODER_OUTPUT_BUFFER_RENDERER) {
      setOutputBufferRenderer((VideoDecoderOutputBufferRenderer) message);
    } else {
      super.handleMessage(messageType, message);
    }
  }
}
