package com.rene_arnold.galleremote.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.collection.LruCache;

public class ImageCache extends LruCache<Uri, Bitmap> {

	private Activity activity;

	public ImageCache(Activity activity) {
		super(2);
		this.activity = activity;
	}
	
	@Override
	protected Bitmap create(Uri key) {
		Bitmap bitmap = BitmapUtil.open(key, activity);
		return bitmap;
	}
	
	@Override
	protected void entryRemoved(boolean evicted, Uri key, Bitmap oldValue,
			Bitmap newValue) {
		oldValue.recycle();
		super.entryRemoved(evicted, key, oldValue, newValue);
	}

}
