package com.rene_arnold.galleremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration.Builder;
import com.nostra13.universalimageloader.utils.StorageUtils;
import com.rene_arnold.galleremote.api.DelayChangeCallback;
import com.rene_arnold.galleremote.api.ImageChangeCallback;
import com.rene_arnold.galleremote.model.Image;
import com.rene_arnold.galleremote.model.Setting;
import com.rene_arnold.galleremote.services.ImageReloadService;
import com.rene_arnold.galleremote.util.DatabaseHelper;
import com.rene_arnold.galleremote.util.ImageCache;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class FullscreenActivity extends Activity implements
        ImageChangeCallback, DelayChangeCallback {

    private ImageSwitcher switcher;

    private ProgressBar progressBar;
    /**
     * a pointer to the currently shown image
     */
    private int counter = 0;

    /**
     * the {@link List} of {@link Image}s
     */
    private volatile List<Image> images;

    private ImageReloadService imageReloadService;
    private DatabaseHelper databaseHelper;

    /**
     * a {@link Handler} to post actions
     */
    private Handler mUiHandler = new Handler(Looper.getMainLooper());
    private Handler mBgHandler;

    private static final int RELOAD_DELAY = 15 * 60 * 1000; // 15 min
    private static final long FALLBACK_DELAY = 2 * 60 * 1000; // 2 min

    private long exitRequest = 0;

    /**
     * the time to wait until the next {@link Image} should be loaded
     */
    private long delay = FALLBACK_DELAY;

    private Runnable showNextImage;
    private Runnable reloadRequest;
    private Runnable startImageScrollRunnable;

    private ImageCache bitmaps;
    private HandlerThread mHandlerThread = new HandlerThread("HandlerThread");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandlerThread.start();
        mBgHandler = new Handler(mHandlerThread.getLooper());


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener
                (visibility -> {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        // TODO: The system bars are visible. Make any desired
                        // adjustments to your UI, such as showing the action bar or
                        // other navigational controls.
                    } else {
                        // TODO: The system bars are NOT visible. Make any desired
                        // adjustments to your UI, such as hiding the action bar or
                        // other navigational controls.
                    }
                });

        setContentView(R.layout.activity_fullscreen);


        Builder builder = new ImageLoaderConfiguration.Builder(this);
        builder.threadPoolSize(4);
        builder.diskCache(new UnlimitedDiskCache(StorageUtils.getCacheDirectory(this)));

        ImageLoaderConfiguration configuration = builder.build();
        ImageLoader imageLoader = ImageLoader.getInstance();
        imageLoader.init(configuration);

        imageReloadService = new ImageReloadService(this);
        databaseHelper = new DatabaseHelper(this);
        bitmaps = new ImageCache(this);

        switcher = findViewById(R.id.image_switcher);
        switcher.setFactory(() -> {
            ImageView myView = new ImageView(getApplicationContext());
            myView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            myView.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return myView;
        });
        switcher.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // add cross-fading
        Animation aniIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        aniIn.setDuration(1000);
        Animation aniOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        aniOut.setDuration(1000);

        switcher.setInAnimation(aniIn);
        switcher.setOutAnimation(aniOut);
        switcher.setOnClickListener((v) -> {
            mUiHandler.removeCallbacks(startImageScrollRunnable);
            mUiHandler.removeCallbacks(showNextImage);
            mUiHandler.removeCallbacks(reloadRequest);
            nextImage();
            mUiHandler.postDelayed(startImageScrollRunnable, 30000);
        });

        View progressBar = findViewById(R.id.progressBar1);
        if (progressBar instanceof ProgressBar) {
            this.progressBar = (ProgressBar) progressBar;
            this.progressBar.setVisibility(View.INVISIBLE);
        }

        reloadRequest = new Runnable() {
            @Override
            public void run() {
                imageReloadService.reloadDelay(FullscreenActivity.this);
                imageReloadService.reloadImages(FullscreenActivity.this);
                mUiHandler.postDelayed(this, RELOAD_DELAY);
            }
        };

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        mBgHandler.post(reloadRequest);

        startImageScrollRunnable = () -> {
            mUiHandler.removeCallbacks(showNextImage);
            mBgHandler.removeCallbacks(reloadRequest);
            mUiHandler.postDelayed(showNextImage, delay);
            mBgHandler.postDelayed(reloadRequest, RELOAD_DELAY);
            Toast toast = Toast.makeText(FullscreenActivity.this, R.string.automatic_screenplay, Toast.LENGTH_LONG);
            toast.show();
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        };
    }

    @Override
    protected void onDestroy() {
        images.clear();
        mBgHandler.removeCallbacks(reloadRequest);
        mUiHandler.removeCallbacks(startImageScrollRunnable);
        mUiHandler.removeCallbacks(showNextImage);
        if (mHandlerThread != null) {
            mHandlerThread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        try {
            QueryBuilder<Image, ?> queryBuilder = databaseHelper.getDao(Image.class).queryBuilder();
            queryBuilder.orderBy(Image.COLUMN_POSITION, true);
            this.images = queryBuilder.query();
        } catch (Exception e) {
            this.images = new ArrayList<>();
        }
        try {
            Dao<Setting, Integer> settingDao = databaseHelper
                    .getDao(Setting.class);
            List<Setting> query = settingDao.queryForEq(Setting.COLUMN_KEY,
                    "delay");
            if (query.size() == 1) {
                delay = Long.valueOf(query.get(0).getValue());
            }
        } catch (Exception e) {
        }

        if (images.size() > 0) {
            setImage(images.iterator().next());
        }
        showNextImage = new Runnable() {
            @Override
            public void run() {
                FullscreenActivity.this.nextImage();
                mUiHandler.postDelayed(this, delay);
            }
        };
        mUiHandler.postDelayed(showNextImage, delay);
    }

    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mUiHandler.removeCallbacks(startImageScrollRunnable);
                mUiHandler.removeCallbacks(showNextImage);
                mBgHandler.removeCallbacks(reloadRequest);
                prevImage();
                mUiHandler.postDelayed(startImageScrollRunnable, 30000);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mUiHandler.removeCallbacks(startImageScrollRunnable);
                mUiHandler.removeCallbacks(showNextImage);
                mBgHandler.removeCallbacks(reloadRequest);
                nextImage();
                mUiHandler.postDelayed(startImageScrollRunnable, 30000);
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                mUiHandler.post(startImageScrollRunnable);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (System.currentTimeMillis() - exitRequest < 5000) {
                    mBgHandler.removeCallbacks(reloadRequest);
                    mBgHandler.post(reloadRequest);
                    Toast t = Toast.makeText(this, "Bilder werden aktualisiert",
                            Toast.LENGTH_LONG);
                    t.show();
                }
                ;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (System.currentTimeMillis() - exitRequest < 5000) {
                    // adust URL
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                            this);
                    View popupView = getLayoutInflater().inflate(
                            R.layout.popup_url, null);
                    dialogBuilder.setView(popupView);
                    final EditText urlInput = (EditText) popupView
                            .findViewById(R.id.url_input);
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(this);
                    String string = prefs.getString("server_url",
                            getString(R.string.server_url));
                    urlInput.setText(string);
                    dialogBuilder.setPositiveButton("Ok",
                            (dialog, paramInt) -> {
                                SharedPreferences prefs1 = PreferenceManager
                                        .getDefaultSharedPreferences(FullscreenActivity.this);
                                Editor edit = prefs1.edit();
                                edit.putString("server_url", urlInput.getText()
                                        .toString());
                                edit.commit();
                                dialog.dismiss();
                            });
                    dialogBuilder.setNegativeButton("Abbrechen",
                            (dialog, paramInt) -> dialog.dismiss());
                    AlertDialog dialog = dialogBuilder.create();
                    dialog.show();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                long now = System.currentTimeMillis();
                if (now - exitRequest < 5000) {
                    finish();
                } else {
                    exitRequest = System.currentTimeMillis();
                    Toast t = Toast.makeText(this, R.string.exit_confirmation,
                            Toast.LENGTH_LONG);
                    t.show();
                }
                break;
        }

        return true;
    }

    private void nextImage() {
        if (images.isEmpty()) {
            counter = 0;
            // setImage(null);
            return;
        }
        counter = (counter + 1) % images.size();
        setImage(getCurrentImage());
    }

    private Image getCurrentImage() {
        return images.get(counter);
    }

    private void prevImage() {
        counter = (counter - 1) % images.size();
        if (counter < 0)
            counter += images.size();
        setImage(getCurrentImage());
    }

    public void setImage(Image image) {
        if (image == null)
            return;
        Bitmap bitmap = bitmaps.get(image.getSavePoint());
        runOnUiThread(() -> switcher.setImageDrawable(new BitmapDrawable(getResources(), bitmap)));
    }

    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    public void changeDelay(Long newDelay) {
        if (newDelay == null)
            return;
        delay = newDelay;
        mUiHandler.removeCallbacks(showNextImage);
        showNextImage = new Runnable() {

            @Override
            public void run() {
                FullscreenActivity.this.nextImage();
                mUiHandler.postDelayed(this, delay);
            }
        };
        mUiHandler.postDelayed(showNextImage, delay);

        startImageScrollRunnable = () -> {
            mUiHandler.removeCallbacks(showNextImage);
            mUiHandler.removeCallbacks(reloadRequest);
            mUiHandler.postDelayed(showNextImage, delay);
            mUiHandler.postDelayed(reloadRequest, RELOAD_DELAY);
            Toast toast = Toast.makeText(FullscreenActivity.this,
                    R.string.automatic_screenplay, Toast.LENGTH_LONG);
            toast.show();
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        };

        try {
            Dao<Setting, Integer> dao = databaseHelper.getDao(Setting.class);
            List<Setting> query = dao.queryForEq(Setting.COLUMN_KEY, "delay");
            if (query.size() == 1) {
                Setting setting = query.get(0);
                setting.setValue(Long.valueOf(delay).toString());
                dao.update(setting);
            } else {
                dao.delete(query);
                Setting setting = new Setting("delay", Long.valueOf(delay)
                        .toString());
                dao.create(setting);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void onImagesChangedEvent(List<Image> newImages) {
        mUiHandler.removeCallbacks(showNextImage);
        boolean wasEmpty = images.isEmpty();
        Image currentImage = null;
        if (!wasEmpty)
            currentImage = getCurrentImage();
        images = newImages;
        Image newImage = getCurrentImage();
        if (wasEmpty && !images.isEmpty()) {
            setImage(images.iterator().next());
        } else if (currentImage != null
                && !currentImage.getImageAddress().equals(
                newImage.getImageAddress())) {
            // if current image is replaced -> show new image
            setImage(newImage);
        }
        mUiHandler.postDelayed(showNextImage, delay);
    }

    public void startImageSync(final Integer length) {
        runOnUiThread(() -> {
            if (length > 0) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                progressBar.setMax(length);
                Log.d(FullscreenActivity.class.getSimpleName(), "Started Imagesync with " + length + " images");
            }
        });
    }

    public void updateImageSync(final Integer position) {
        runOnUiThread(() -> {
            progressBar.setProgress(position);
            Log.d(FullscreenActivity.class.getSimpleName(), "Set Imagesync progress to " + position);
            if (progressBar.getProgress() == progressBar.getMax()) {
                progressBar.setVisibility(View.INVISIBLE);
                Log.d(FullscreenActivity.class.getSimpleName(), "hide progress bar");
            }
        });

    }
}
