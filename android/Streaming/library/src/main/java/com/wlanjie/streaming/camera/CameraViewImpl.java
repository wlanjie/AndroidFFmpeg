/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wlanjie.streaming.camera;

import android.graphics.SurfaceTexture;
import android.view.View;

import java.util.Set;

public abstract class CameraViewImpl {

    final CameraCallback mCallback;

    SurfaceTexture mPreviewSurface;

    int mWidth;

    int mHeight;

    CameraViewImpl(CameraCallback callback) {
        mCallback = callback;
    }

    public void setPreviewSurface(SurfaceTexture previewSurface) {
        mPreviewSurface = previewSurface;
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public abstract void start();

    public abstract void stop();

    abstract boolean isCameraOpened();

    public abstract void setFacing(int facing);

    abstract int getFacing();

    public abstract void startPreview(int width, int height);

    abstract Set<AspectRatio> getSupportedAspectRatios();

    abstract void setAspectRatio(AspectRatio ratio);

    abstract AspectRatio getAspectRatio();

    abstract void setAutoFocus(boolean autoFocus);

    abstract boolean getAutoFocus();

    abstract void setFlash(int flash);

    abstract int getFlash();

    abstract void setDisplayOrientation(int displayOrientation);
}
