// Copyright 2025 The ExtVideoPlayer Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package ext.videoplayer;

import android.content.Context;
import android.os.Build;
import android.util.LongSparseArray;
import io.flutter.FlutterInjector;
import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import ext.videoplayer.Messages.*;
import io.flutter.view.TextureRegistry;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.HttpsURLConnection;

/** Android platform implementation of the ExtVideoPlayer plugin. */
public class ExtVideoPlayerPlugin implements FlutterPlugin, VideoPlayerApi {
  private static final String TAG = "ExtVideoPlayerPlugin";
  private final LongSparseArray<VideoPlayer> videoPlayers = new LongSparseArray<>();
  private FlutterState flutterState;
  private VideoPlayerOptions options = new VideoPlayerOptions();

  /** Default constructor for Flutter v2 embedding. */
  public ExtVideoPlayerPlugin() {}

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      try {
        HttpsURLConnection.setDefaultSSLSocketFactory(new CustomSSLSocketFactory());
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        Log.w(TAG, "Failed to enable TLSv1.1 and TLSv1.2 for older APIs.", e);
      }
    }

    FlutterInjector injector = FlutterInjector.instance();
    this.flutterState = new FlutterState(
            binding.getApplicationContext(),
            binding.getBinaryMessenger(),
            injector.flutterLoader()::getLookupKeyForAsset,
            injector.flutterLoader()::getLookupKeyForAsset,
            binding.getTextureRegistry()
    );
    flutterState.startListening(this, binding.getBinaryMessenger());
  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {
    if (flutterState == null) {
      Log.w(TAG, "Detached from engine before initialization.");
      return;
    }
    flutterState.stopListening(binding.getBinaryMessenger());
    flutterState = null;
    disposeAllPlayers();
  }

  private void disposeAllPlayers() {
    for (int i = 0; i < videoPlayers.size(); i++) {
      videoPlayers.valueAt(i).dispose();
    }
    videoPlayers.clear();
  }

  public TextureMessage create(CreateMessage arg) {
    TextureRegistry.SurfaceTextureEntry handle = flutterState.textureRegistry.createSurfaceTexture();
    EventChannel eventChannel = new EventChannel(flutterState.binaryMessenger, "flutter.io/videoPlayer/videoEvents" + handle.id());

    VideoPlayer player = (arg.getAsset() != null) ? createAssetPlayer(arg, eventChannel, handle) : createUriPlayer(arg, eventChannel, handle);
    videoPlayers.put(handle.id(), player);

    TextureMessage result = new TextureMessage();
    result.setTextureId(handle.id());
    return result;
  }

  private VideoPlayer createAssetPlayer(CreateMessage arg, EventChannel eventChannel, TextureRegistry.SurfaceTextureEntry handle) {
    String assetLookupKey = (arg.getPackageName() != null) ?
            flutterState.keyForAssetAndPackageName.get(arg.getAsset(), arg.getPackageName()) :
            flutterState.keyForAsset.get(arg.getAsset());
    return new VideoPlayer(flutterState.applicationContext, eventChannel, handle, "asset:///" + assetLookupKey, null, options);
  }

  private VideoPlayer createUriPlayer(CreateMessage arg, EventChannel eventChannel, TextureRegistry.SurfaceTextureEntry handle) {
    return new VideoPlayer(flutterState.applicationContext, eventChannel, handle, arg.getUri(), arg.getFormatHint(), options);
  }

  public void dispose(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.dispose();
      videoPlayers.remove(arg.getTextureId());
    }
  }

  public void setLooping(LoopingMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.setLooping(arg.getIsLooping());
    }
  }

  public void setVolume(VolumeMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.setVolume(arg.getVolume());
    }
  }

  public void setPlaybackSpeed(PlaybackSpeedMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.setPlaybackSpeed(arg.getSpeed());
    }
  }

  public void play(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.play();
    }
  }

  public PositionMessage position(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    PositionMessage result = new PositionMessage();
    if (player != null) {
      result.setPosition(player.getPosition());
      player.sendBufferingUpdate();
    }
    return result;
  }

  public void seekTo(PositionMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.seekTo(arg.getPosition().intValue());
    }
  }

  public void pause(TextureMessage arg) {
    VideoPlayer player = videoPlayers.get(arg.getTextureId());
    if (player != null) {
      player.pause();
    }
  }

  @Override
  public void setMixWithOthers(MixWithOthersMessage arg) {
    options.mixWithOthers = arg.getMixWithOthers();
  }

  private static final class FlutterState {
    private final Context applicationContext;
    private final BinaryMessenger binaryMessenger;
    private final KeyForAssetFn keyForAsset;
    private final KeyForAssetAndPackageName keyForAssetAndPackageName;
    private final TextureRegistry textureRegistry;

    FlutterState(Context applicationContext, BinaryMessenger messenger, KeyForAssetFn keyForAsset, KeyForAssetAndPackageName keyForAssetAndPackageName, TextureRegistry textureRegistry) {
      this.applicationContext = applicationContext;
      this.binaryMessenger = messenger;
      this.keyForAsset = keyForAsset;
      this.keyForAssetAndPackageName = keyForAssetAndPackageName;
      this.textureRegistry = textureRegistry;
    }

    void startListening(ExtVideoPlayerPlugin handler, BinaryMessenger messenger) {
      VideoPlayerApi.setup(messenger, handler);
    }

    void stopListening(BinaryMessenger messenger) {
      VideoPlayerApi.setup(messenger, null);
    }
  }
}
