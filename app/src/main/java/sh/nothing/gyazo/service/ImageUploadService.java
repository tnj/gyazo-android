
package sh.nothing.gyazo.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.deploygate.sdk.DeployGate;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;

import sh.nothing.gyazo.R;

public class ImageUploadService extends IntentService {

    private static final String TAG = "ImageUploadService";
    public static final String PREFS_GYAZO_ID = "id";
    public static final int WAIT_FOR_SAVE_COMPLETION_SECONDS = 10;

    private Handler handler;

    public ImageUploadService() {
        super(TAG);
        handler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Uri uri = intent.getData();

        onStart();

        int retry = 3;
        String result = null;
        do {
            try {
                result = upload(uri);
                break;
            } catch (IOException e) {
                Log.w(TAG, String.format("Failed to upload an image retry = %d", retry), e);
                DeployGate.logWarn("Failed to upload: " + e.getMessage());
            }
        } while (--retry > 0);

        if (result == null) {
            onFailed();
        } else {
            onSucceed(result);
        }
    }

    private void onSucceed(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setText(url);
        showToast(R.string.upload_successful, Toast.LENGTH_SHORT);

        DeployGate.logVerbose("Upload successful");
    }

    private void onFailed() {
        showToast(R.string.failed_to_upload_image, Toast.LENGTH_SHORT);
        DeployGate.logWarn("Upload failed");
    }

    private void onStart() {
        showToast(R.string.uploading_image, Toast.LENGTH_SHORT);
    }

    private void showToast(final int res, final int length) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ImageUploadService.this, res, length).show();
            }
        });
    }

    private String upload(final Uri uri) throws IOException {
        OkHttpClient client = new OkHttpClient();

        String id = loadGyazoId();
        String type = getContentResolver().getType(uri);
        File file = ensureFileFromContentUri(uri);

        RequestBody body = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"id\""),
                        RequestBody.create(null, id)
                )
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"imagedata\"; filename=\"gyazo.com\""),
                        RequestBody.create(MediaType.parse(type), file)
                )
                .build();

        Request request = new Request.Builder()
                .url(getEndpointUrl())
                .post(body)
                .build();

        Response response = client.newCall(request).execute();

        if (response.isSuccessful()) {
            String url = response.body().string();

            id = response.header("X-Gyazo-Id");
            if (!TextUtils.isEmpty(id))
                saveGyazoId(id);

            return url;
        }
        return null;
    }

    private SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private String loadGyazoId() {
        return getPrefs().getString(PREFS_GYAZO_ID, "");
    }

    private void saveGyazoId(String id) {
        getPrefs().edit().putString(PREFS_GYAZO_ID, id).apply();
    }

    public String getEndpointUrl() {
        return "http://upload.gyazo.com/upload.cgi";
    }

    @Nullable
    private File ensureFileFromContentUri(Uri uri) {
        File file;
        int retry = WAIT_FOR_SAVE_COMPLETION_SECONDS;
        do {
            file = getFileFromContentUri(uri);
            if (file != null)
                break;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        } while (--retry > 0);
        return file;
    }

    @Nullable
    public File getFileFromContentUri(Uri uri) {
        Cursor cursor = getContentResolver().query(uri,
                new String[]{
                        MediaStore.Images.ImageColumns.DATA,
                        MediaStore.Images.ImageColumns.SIZE
                }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    // SIZE == 0 means image compression haven't finished yet
                    if (cursor.getLong(1) > 0)
                        return new File(cursor.getString(0));
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

}
