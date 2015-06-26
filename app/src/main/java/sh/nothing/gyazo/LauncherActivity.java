package sh.nothing.gyazo;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;

import java.io.File;

import sh.nothing.gyazo.service.ImageUploadService;

public class LauncherActivity extends Activity {

    private static final String SCREENSHOTS_DIR_NAME = "Screenshots";
    private static final long RECENT_THREASHOLD_MILLIS = 60000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri target = findUploadableScreenshot();
        if (target != null) {
            uploadScreenshot(target);
        } else {
            startPreference();
        }

        finish();
    }

    private void startPreference() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void uploadScreenshot(Uri target) {
        Intent i = new Intent(this, ImageUploadService.class);
        i.setData(target);
        startService(i);
    }

    private Uri findUploadableScreenshot() {
        Uri target = extractTargetFromIntent();
        if (target == null) {
            target = findTargetFromMediaStore();
        }
        return target;
    }

    private Uri findTargetFromMediaStore() {
        Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Images.ImageColumns._ID,
                        MediaStore.Images.ImageColumns.DATE_TAKEN
                },
                MediaStore.Images.ImageColumns.MIME_TYPE + "=? AND " +
                        MediaStore.Images.ImageColumns.DATA + " LIKE ?",
                new String[]{
                        "image/png",
                        getScreenshotDir() + "%"
                },
                MediaStore.Images.ImageColumns._ID + " DESC");

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    if (System.currentTimeMillis() - RECENT_THREASHOLD_MILLIS < cursor.getLong(1))
                        return ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(0));
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    @NonNull
    private String getScreenshotDir() {
        File screenShotDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), SCREENSHOTS_DIR_NAME);
        return screenShotDir.getAbsolutePath();
    }

    private Uri extractTargetFromIntent() {
        // TODO implement
        return null;
    }
}
