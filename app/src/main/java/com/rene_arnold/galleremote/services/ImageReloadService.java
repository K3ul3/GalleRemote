package com.rene_arnold.galleremote.services;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import com.j256.ormlite.dao.Dao;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.rene_arnold.galleremote.FullscreenActivity;
import com.rene_arnold.galleremote.api.DelayChangeCallback;
import com.rene_arnold.galleremote.api.ImageChangeCallback;
import com.rene_arnold.galleremote.model.Image;
import com.rene_arnold.galleremote.util.BitmapUtil;
import com.rene_arnold.galleremote.util.DatabaseHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ImageReloadService {

    private static final String DEBUG_TAG = ImageReloadService.class
            .getSimpleName();

    private final FullscreenActivity context;

    private Long delayCache;
    private List<URL> imageCache;

    private HttpRestService httpRestService;

    public ImageReloadService(FullscreenActivity context) {
        this.context = context;
        httpRestService = new HttpRestService(context);
    }

    public void reloadDelay(DelayChangeCallback callback) {
        Long newDelay = reloadDelay();
        if (newDelay != null) {
            callback.changeDelay(newDelay);
        }
    }

    public void reloadImages(final ImageChangeCallback callback) {
        List<Image> newImages = doReloadImages(callback);
        if (newImages != null) {
            callback.onImagesChangedEvent(newImages);
        }
    }

    private List<Image> doReloadImages(final ImageChangeCallback callback) {
        List<URL> images = httpRestService.getImages();
        if (images == null)
            return null;
        boolean equal = true;
        if (imageCache != null && imageCache.size() == images.size()) {
            for (int i = 0; i < imageCache.size(); i++) {
                if (!imageCache.get(i).equals(images.get(i))) {
                    equal = false;
                    break;
                }
            }
        } else {
            equal = false;
        }
        if (!equal) {
            imageCache = images;
            List<Image> syncImages = null;
            try {
                syncImages = syncImages(images, callback);
            } catch (SQLException | IOException e) {
                Log.e(ImageReloadService.class.getSimpleName(), e.getClass().getSimpleName(), e);
            }
            Log.d(DEBUG_TAG, "Images changed");
            return syncImages;
        }
        return null;
    }

    private Long reloadDelay() {
        Log.d(DEBUG_TAG, "Reload data from Server");
        Long delay = httpRestService.getDelay();
        if (delay != null
                && (delayCache == null || delay.longValue() != delayCache
                .longValue())) {
            Log.d(DEBUG_TAG, "Delay changed");
            delayCache = delay;
        }
        return delay;
    }

    /**
     * Gets the current {@link List} of Images and compares it to the
     * {@link List} provided by the server. New {@link Image}s will be
     * downloaded and added and no longer used {@link Image}s will be deleted
     * from Database and File-System.
     *
     * @param urls     the {@link List} of {@link URL}s provided by the server
     * @param callback
     * @return the new {@link List} of {@link Image}s
     * @throws SQLException if the database is not accessible
     * @throws IOException
     */
    private List<Image> syncImages(List<URL> urls,
                                   final ImageChangeCallback callback) throws SQLException,
            IOException {
        DatabaseHelper databaseHelper = context.getDatabaseHelper();
        Dao<Image, Integer> dao = databaseHelper.getDao(Image.class);
        List<Image> imageList = dao.queryForAll();
        List<Image> newImages = new ArrayList<>();
        if (urls == null || urls.isEmpty()) {
            return imageList;
        }
        List<Image> unusedImages = new ArrayList<>(imageList);
        imageCache = new ArrayList<>(urls);
        callback.startImageSync(urls.size());
        int position = 1;
        urlList:
        for (URL url : urls) {
            // search for existing
            for (Image image : imageList) {
                if (image.getImageAddress().equals(url)) {
                    // if found -> take it
                    unusedImages.remove(image);
                    if (image.getPosition() != position) {
                        image.setPosition(position);
                        dao.update(image);
                    }
                    newImages.add(image);
                    position++;
                    callback.updateImageSync(position);
                    continue urlList;
                }
            }
            Bitmap bitmap = ImageLoader.getInstance().loadImageSync(
                    url.toString());
            String filePath = context.getExternalFilesDir(null)
                    .getAbsolutePath()
                    + File.separator
                    + Long.valueOf(System.currentTimeMillis()).toString();
            File file = new File(filePath);
            FileOutputStream fileOutput = new FileOutputStream(file);
            Bitmap merge = BitmapUtil.applyBlur(bitmap, context);
            merge.compress(Bitmap.CompressFormat.JPEG, 90, fileOutput);
            bitmap.recycle();
            merge.recycle();
            fileOutput.flush();
            fileOutput.close();
            Uri uri = Uri.fromFile(file);
            Image image = new Image();
            image.setImageAddress(url);
            image.setPosition(position);
            image.setSavePoint(uri);
            dao.create(image);
            newImages.add(image);
            position++;
            callback.updateImageSync(position);
        }
        for (Image image : unusedImages) {
            deleteImage(image);
        }
        dao.delete(unusedImages);
        return newImages;
    }

    private void deleteImage(Image image) {
        File file = new File(image.getSavePoint().getPath());
        file.delete();
    }
}
