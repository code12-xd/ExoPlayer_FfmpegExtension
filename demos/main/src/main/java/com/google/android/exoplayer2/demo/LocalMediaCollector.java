package com.google.android.exoplayer2.demo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;

public class LocalMediaCollector {
    private static final String TAG = LocalMediaCollector.class.getSimpleName();
    /**
     * 获取本地所有的音乐文件
     */
    //表示操作的表  Uri.parse("content://media/external/audio/media");
    private static Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    //表示要获取的字段
    private static String[] projection = new String[]{
            String.valueOf(MediaStore.Audio.Media._ID),
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA};
    //表示where 子句
    private static String selection = "";
    //表示排序字段
    private static String sortOrder = MediaStore.Audio.Media.DEFAULT_SORT_ORDER;

    public static class MediaBean {
        private int id;
        private String title;
        private String album;
        private String albumimage;
        private String artist;
        private int duration;
        private String data;
        private Uri uri;

        public void setId(int id) {
            this.id = id;
        }

        public int getId() { return id; }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() { return title; }

        public void setAlbum(String album) {
            this.album = album;
        }

        public String getAlbum() { return album; }

        public void setAlbumimage(String albumimage) {
            this.albumimage = albumimage;
        }

        public String getAlbumimage() { return albumimage; }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getArtist() { return artist; }

        public void setUri(Uri uri) {
            this.uri = uri;
        }

        public Uri getUri() { return uri; }
    }

    public static ArrayList<MediaBean> getLocalMusics(Context c){
        ArrayList<MediaBean> musicList = new ArrayList<MediaBean>();
        ContentResolver content = c.getContentResolver();
        Cursor cursor = content.query(uri, null, null, null, sortOrder);
        if(cursor==null){
            Log.v(TAG, "Music corsor==null");
        }else if(!cursor.moveToFirst()){
            getFromPath(musicList, Environment.getExternalStorageDirectory() + "/song");
            Log.v(TAG, "Music corsor.moveToForst() is false");
        }else{
            int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            do{
                int id = cursor.getInt(idCol);
                String title = cursor.getString(titleCol);
                String album = cursor.getString(albumCol);
                String artist = cursor.getString(artistCol);
                int duration = cursor.getInt(durationCol);
                String data = cursor.getString(dataCol);

                MediaBean music = new MediaBean();
                music.setId(id);
                music.setTitle(title);
                music.setAlbum(album);
                music.setArtist(artist);
//                music.setDuration(duration);
//                music.setData(data);
                music.setUri(Uri.withAppendedPath(uri, String.valueOf(id)));
                musicList.add(music);

            }while(cursor.moveToNext());
        }
        cursor.close();
        return musicList;
    }
    
    private static void getFromPath(ArrayList<MediaBean> musicList, String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        File d = new File(path);

        if (!d.isDirectory()) return;

        File[] files = d.listFiles();
        File f = null;
        for (int i = 0; i < files.length; i++) {
            f = files[i];
            try {
                if (!f.getParent().endsWith(".mp3")) continue;
                mmr.setDataSource(f.getPath());
                String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                //byte[] pic = mmr.getEmbeddedPicture();
                MediaBean music = new MediaBean();
                music.setTitle(title);
                music.setAlbum(album);
                music.setArtist(artist);
                music.setUri(Uri.parse(f.getPath()));
                musicList.add(music);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}
