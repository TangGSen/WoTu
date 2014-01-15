/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.xjt.wotu.util;

import android.os.Environment;

import com.xjt.wotu.data.MediaPath;
import com.xjt.wotu.data.MediaSet;

import java.util.Comparator;

public class UtilsMedia {
    
    public static final String IMPORTED = "Imported";
    public static final String DOWNLOAD = "download";
    public static final Comparator<MediaSet> NAME_COMPARATOR = new NameComparator();

    public static final int CAMERA_BUCKET_ID = UtilsCom.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera");
    public static final int DOWNLOAD_BUCKET_ID = UtilsCom.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/"
            + DOWNLOAD);
    public static final int IMPORTED_BUCKET_ID = UtilsCom.getBucketId(
            Environment.getExternalStorageDirectory().toString() + "/"
            + IMPORTED);
    public static final int SNAPSHOT_BUCKET_ID = UtilsCom.getBucketId(
            Environment.getExternalStorageDirectory().toString() +
            "/Pictures/Screenshots");

    private static final MediaPath[] CAMERA_PATHS = {
            new MediaPath("/local/all/" + CAMERA_BUCKET_ID),
            new MediaPath("/local/image/" + CAMERA_BUCKET_ID),
            new MediaPath("/local/video/" + CAMERA_BUCKET_ID)};

    public static boolean isCameraSource(MediaPath path) {
        return CAMERA_PATHS[0] == path || CAMERA_PATHS[1] == path
                || CAMERA_PATHS[2] == path;
    }

    // Sort MediaSets by name
    public static class NameComparator implements Comparator<MediaSet> {
        public int compare(MediaSet set1, MediaSet set2) {
            int result = set1.getName().compareToIgnoreCase(set2.getName());
            if (result != 0) return result;
            return set1.getPath().toString().compareTo(set2.getPath().toString());
        }
    }
}
