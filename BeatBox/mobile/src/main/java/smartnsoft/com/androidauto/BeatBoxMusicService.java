package smartnsoft.com.androidauto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaMetadata.Builder;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;

public class BeatBoxMusicService
    extends MediaBrowserService
{
  public static final String ROOT = "root";

  private MediaSession session;

  public static final String RESOURCES = "android.resource://smartnsoft.com.androidauto/";

  private MediaPlayer player;

  private Map<String, MediaMetadata> metadatas = new HashMap<>();

  //region MUSIC INSTRUMENTS "ALBUM" WITH NAVIGATION
  public static final String MUSIC_INSTRUMENTS = "Music instruments";

  private static String[] INSTRUMENTS = new String[]
      {
          "guitar",
          "accordion",
          "drums",
          "mic",
          "violin",
          "piano"
      };
  //endregion

  @Override
  public void onCreate()
  {
    super.onCreate();

    session = new MediaSession(this, "BeatBoxMusicService");
    setSessionToken(session.getSessionToken());
    session.setCallback(new MediaSessionCallback());
    session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

    //region RETRIEVE AN AWESOME MP3
    player = new MediaPlayer();
    player.setAudioStreamType(AudioManager.STREAM_MUSIC);

    retrieveMediaMetadata("music", MediaItem.FLAG_PLAYABLE);
    //endregion

    //region RETRIEVE INSTRUMENT MP3s
    for (String resourceName : INSTRUMENTS)
    {
      retrieveMediaMetadata(resourceName, MediaItem.FLAG_PLAYABLE);
    }
    //endregion
  }

  //region HELPER METHOD TO RETRIEVE MP3
  /**
   * Retrieves the metadatas of an MP3 (title, artist, album and title arts) and associates it a media ID based
   * on its resource name. Returns a MediaItem representing these metadatas
   * @param resourceName The name of the resource in the resources folder
   * @param flag either PLAYABLE or BROWSABLE
   * @return The MediaItem corresponding to the MediaMetadata retrieved from the MP3
   */
  private MediaItem retrieveMediaMetadata(String resourceName, int flag){
    MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
    metaRetriever.setDataSource(getApplicationContext(), Uri.parse(RESOURCES + getResources().getIdentifier(resourceName, "raw", getPackageName())));
    final byte[] picture = metaRetriever.getEmbeddedPicture();
    final MediaMetadata mediaMetadata = new Builder()
        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, resourceName)
        .putString(MediaMetadata.METADATA_KEY_TITLE, metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
        .putString(MediaMetadata.METADATA_KEY_ARTIST, metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeByteArray(picture, 0, picture.length))
        .putBitmap(MediaMetadata.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), getResources().getIdentifier("ic_" + resourceName, "drawable", getPackageName())))
        .build();
    metadatas.put(mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), mediaMetadata);
    return new MediaItem(mediaMetadata.getDescription(), flag);
  }
  //endregion

  @Override
  public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints)
  {
    return new BrowserRoot(ROOT, null);
  }

  @Override
  public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result)
  {
    final List<MediaItem> items = new ArrayList<>();

    //region ADD AN AWESOME MUSIC AT THE ROOT
    if (ROOT.equals(parentMediaId))
    {
      items.add(new MediaItem(metadatas.get("music").getDescription(), MediaItem.FLAG_PLAYABLE));
    }
    //endregion

    //region ADD A FOLDER THAT CAN BE BROWSED
    if (ROOT.equals(parentMediaId)){
      items.add(new MediaItem(
          new MediaDescription.Builder()
              .setMediaId(MUSIC_INSTRUMENTS)
              .setTitle(MUSIC_INSTRUMENTS)
              .setIconBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_guitar))
              .build(), MediaItem.FLAG_BROWSABLE));
    }
    //endregion

    //region ADD INSTRUMENT MP3s WITHIN THE TREE VIEW SECOND LEVEL
    if (MUSIC_INSTRUMENTS.equals(parentMediaId))
    {
      for (String resourceName : INSTRUMENTS)
      {
        items.add(new MediaItem(metadatas.get(resourceName).getDescription(), MediaItem.FLAG_PLAYABLE));
      }
    }
    //endregion

    result.sendResult(items);
  }

  private final class MediaSessionCallback
      extends MediaSession.Callback
  {

    private MediaMetadata currentStream;

    public static final long SKIP_CONTROLS = PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    //region HELPER METHOD TO PLAY MUSIC
    /**
     * Plays the currentStream which is set.
     * Creates a new MediaPlayer and sets the data source according to the MediaMetaData
     */
    private void playCurrentStream()
    {
      session.setMetadata(currentStream);
      try
      {
        player.release();
        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setDataSource(getBaseContext(), Uri.parse(RESOURCES + getResources().getIdentifier(currentStream.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), "raw", getPackageName())));
        player.prepare();
        player.start();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }
    //endregion

    //region ADD TREE VIEW MUSIC CHOICE HANDLING
    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras)
    {
      currentStream = metadatas.get(mediaId);
      playCurrentStream();
      session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE | ("music".equals(mediaId) ? 0 : SKIP_CONTROLS)).setState(PlaybackState.STATE_PLAYING, 123, 1, SystemClock.elapsedRealtime()).build());
    }
    //endregion

    //region ADD PLAY/PAUSE CONTROL HANDLING
    @Override
    public void onPlay()
    {
      if (!player.isPlaying() )
      {
        player.start();
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE |
                ("music".equals(currentStream.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)) ? 0 : SKIP_CONTROLS))
            .setState(PlaybackState.STATE_PLAYING, 0, 1, SystemClock.elapsedRealtime()).build());
      }
    }

    @Override
    public void onPause()
    {
      if (player.isPlaying())
      {
        player.pause();
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE |
                ("music".equals(currentStream.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)) ? 0 : SKIP_CONTROLS))
            .setState(PlaybackState.STATE_PAUSED, 0, 1, SystemClock.elapsedRealtime()).build());
      }
    }
    //endregion

    //region ADD SKIP NEXT AND PREVIOUS CONTROL HANDLING
    @Override
    public void onSkipToNext()
    {
      for (int index = 0; index < INSTRUMENTS.length; index++)
      {
        if(currentStream.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).equals(INSTRUMENTS[index])) {
          currentStream = metadatas.get(INSTRUMENTS[(index + 1) % INSTRUMENTS.length]);
          break;
        }
      }
      playCurrentStream();
    }

    @Override
    public void onSkipToPrevious()
    {
      for (int index = 0; index < INSTRUMENTS.length; index++)
      {
        if(currentStream.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).equals(INSTRUMENTS[index])) {
          currentStream = metadatas.get(INSTRUMENTS[(index + 1) % INSTRUMENTS.length]);
          break;
        }
      }
      playCurrentStream();
    }
    //endregion

    //region ADD VOICE CONTROL HANDLING
    @Override
    public void onPlayFromSearch(final String query, final Bundle extras)
    {
      onPlay();
    }
    //endregion
  }

  @Override
  public void onDestroy()
  {
    session.release();
  }
}
