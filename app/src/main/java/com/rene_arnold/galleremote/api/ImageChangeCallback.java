package com.rene_arnold.galleremote.api;

import java.util.List;

import com.rene_arnold.galleremote.model.Image;

public interface ImageChangeCallback {

	public void onImagesChangedEvent(List<Image> newImages);
	
	public void startImageSync(final Integer length);

	public void updateImageSync(final Integer position);
}
