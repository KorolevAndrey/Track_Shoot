package com.trackshoot;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploaderThread extends AsyncTask {

    private Context context;
    private JSONObject shotInfo;
    private File imageFile;
    private String remoteServiceURL = "https://api.imgur.com/3/image";
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");
    private static final String TAG = TrackShootService.class.getName();
    private static final String IMGUR_CLIENT_ID = "581faf1f75dd3f1";

    UploaderThread(Context context, JSONObject shotInfo, File imageFile) {
        this.context = context;
        this.shotInfo = shotInfo;
        this.imageFile = imageFile;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        OkHttpClient okHttpClient = new OkHttpClient();

        Log.d(TAG, "FILE PATH: " + imageFile.getAbsolutePath());

        RequestBody requestBody = null;

        try {
            requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("title", "shot")
                    .addFormDataPart("description", shotInfo.getString(context.getString(R.string.json_string_app_id)))
                    .addFormDataPart("image", imageFile.getName(), RequestBody.create(MEDIA_TYPE_JPEG, imageFile.getAbsoluteFile()))
                    .build();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Request request = new Request.Builder()
                .header("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                .url(remoteServiceURL)
                .post(requestBody)
                .build();

        try {
            Response response = okHttpClient.newCall(request).execute();
            Log.d(TAG, "POST RESPONSE: " + response.body().string());
            Log.d(TAG, "IS RESPONSE SUCCESSFUL: " + response.isSuccessful());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}