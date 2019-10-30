package com.rene_arnold.galleremote.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

public class Utilities {

	public static String inputStream2string(InputStream inputStream)
			throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				inputStream, "UTF-8"));
		StringBuilder sb = new StringBuilder();
		String s = reader.readLine();
		while (s != null) {
			sb.append(s);
			s = reader.readLine();
		}
		return sb.toString();
	}

	public static void imageViewAnimatedChange(Context c, final ImageView oldImageView,
			final ImageView newImageView, final Bitmap new_image) {
		final float alpha = Math.max(oldImageView.getAlpha(), newImageView.getAlpha());
		newImageView.setImageBitmap(new_image);
		oldImageView.animate().alpha(0f).setDuration(2000).start();
		newImageView.animate().alpha(alpha).setDuration(2000).setStartDelay(1000).start();
		Handler h = new Handler(Looper.getMainLooper());
		h.postDelayed(new Runnable() {

			@Override
			public void run() {
				oldImageView.setImageBitmap(new_image);
				oldImageView.setAlpha(alpha);
				newImageView.setAlpha(0f);
			}
		}, 2100);
	}
}
