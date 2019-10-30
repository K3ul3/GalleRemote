package com.rene_arnold.galleremote.util;


import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import androidx.annotation.Nullable;

public class BitmapUtil {

	private static final float BLUR_RADIUS = 20.5f; // 25 is maximum radius

	public static Bitmap open(Uri uri, Activity activity){
		Bitmap bitmap = BitmapFactory.decodeFile(uri.getPath());
//		Bitmap merge = applyBlur(bitmap, activity);
//		bitmap.recycle();
		return bitmap;
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	@Nullable
	public static Bitmap applyBlur(Bitmap bitmap, Activity context) {
		RenderScript rs = RenderScript.create(context);
		Bitmap bitmapBlur = bitmap.copy(Bitmap.Config.ARGB_8888, true);

		Allocation in = Allocation.createFromBitmap(rs, bitmapBlur,
				Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
		Allocation out = Allocation.createTyped(rs, in.getType());

		ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs,
				out.getElement());
		blur.setRadius(BLUR_RADIUS);
		blur.setInput(in);
		blur.forEach(out);
		out.copyTo(bitmapBlur);
		rs.destroy();
		
		Bitmap merge = bitmapOverlayToCenter(bitmapBlur, bitmap, context);
		bitmapBlur.recycle();
		return merge;
	}
	
	public static Bitmap bitmapOverlayToCenter(Bitmap bitmap1, Bitmap overlayBitmap, Activity context) {
        int bitmap2Height = overlayBitmap.getHeight();
        int bitmap1Width = bitmap1.getWidth();
        float width = 1024;
        float height = 600;

        Bitmap finalBitmap = Bitmap.createBitmap((int) width, (int)height, bitmap1.getConfig());
        Canvas canvas = new Canvas(finalBitmap);
        
        float ratioSmall =  height / bitmap2Height;
        int newWidth =  (int) (overlayBitmap.getScaledWidth(canvas) * ratioSmall);
        
        float ratioBig =  width / bitmap1Width;
        int newHeight = (int) (bitmap1.getScaledHeight(canvas) * ratioBig);
        
        float marginLeft = (float) (width * 0.5 - newWidth * 0.5);
        float marginTop = (float) (height * 0.5 - newHeight * 0.5);
        
        Paint p = new Paint(Color.RED);
        ColorFilter filter = new LightingColorFilter(0xFF7F7F7F, 0x00000000);
        p.setColorFilter(filter);
        Bitmap scaledBitmap1 = Bitmap.createScaledBitmap(bitmap1, (int)width, newHeight, false);
		canvas.drawBitmap(scaledBitmap1, 0, marginTop, p);
        Bitmap scaledBitmap2 = Bitmap.createScaledBitmap(overlayBitmap, newWidth, (int)height, false);
		canvas.drawBitmap(scaledBitmap2, marginLeft, 0, null);
		scaledBitmap1.recycle();
		scaledBitmap2.recycle();
        
        return finalBitmap;
    }
}