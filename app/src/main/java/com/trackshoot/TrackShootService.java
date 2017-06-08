package com.trackshoot;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.CameraError;
import com.androidhiddencamera.HiddenCameraService;
import com.androidhiddencamera.HiddenCameraUtils;
import com.androidhiddencamera.config.CameraFacing;
import com.androidhiddencamera.config.CameraImageFormat;
import com.androidhiddencamera.config.CameraResolution;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TrackShootService extends HiddenCameraService implements
        SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private boolean isInitialCoordinatesSet;
    private String appId;
    private int rate;
    private double distance;
    private double initialLatitude;
    private double initialLongitude;

    private SensorManager sensorManager;
    private GoogleApiClient googleClient;
    private LocationRequest locationRequest;
    private JSONObject shotInfo;

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private static final String TAG = TrackShootService.class.getName();
    private static final int LOCATION_TIME_INTERVAL = 1000;
    private static final int LOCATION_FASTEST_TIME_INTERVAL = 500;

    @Override
    public void onCreate() {
        super.onCreate();
        isInitialCoordinatesSet = false;
        loadOrientationService();
        createGoogleClient();
        setLocationRequestSettings();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            appId = (String) intent.getExtras().get(TrackShootActivity.APP_ID);
            rate = (int) intent.getExtras().get(TrackShootActivity.RATE);
        }

        loadCamera();

        return START_STICKY;
    }

    private String getDateTime() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getString(R.string.date_format));
        return simpleDateFormat.format(new Date());
    }

    private void loadOrientationService() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(sensorEvent.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(sensorEvent.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    private String getOrientation() {
        sensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);
        sensorManager.getOrientation(rotationMatrix, orientationAngles);
        return "[" + orientationAngles[0] + ", " + orientationAngles[1] + ", " + orientationAngles[2] + "]";
    }

    private void createGoogleClient() {
        if (googleClient == null) {
            googleClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        googleClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    googleClient, locationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    protected void setLocationRequestSettings() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(LOCATION_TIME_INTERVAL);
        locationRequest.setFastestInterval(LOCATION_FASTEST_TIME_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(googleClient,
                        builder.build());
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!isInitialCoordinatesSet) {
            initialLatitude = location.getLatitude();
            initialLongitude = location.getLongitude();
            isInitialCoordinatesSet = true;
            Log.d(TAG, "INITIAL COORDINATES SET");
        } else {
            distance = calculateDistance(initialLatitude, initialLongitude, location.getLatitude(), location.getLongitude());
            Toast.makeText(getApplicationContext(), location.getLatitude() +
                    " " + location.getLongitude() + " " + distance, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "latitude: " + location.getLatitude() + " longitude: " + location.getLongitude() + " distance: " + distance);

            // If the distance is >= required distance then take a picture, pack and send the data and set the initial coordinates.
            distance = rate;

            if (distance >= rate) {
                packData(location.getLatitude(), location.getLongitude());
                shootPicture();
                initialLatitude = location.getLatitude();
                initialLongitude = location.getLongitude();
            }
        }
    }

    private double calculateDistance(double initialLatitude, double initialLongitude, double newLatitude, double newLongitude) {
        double earthRadius = 3958.75;
        double dLatitude = Math.toRadians(newLatitude - initialLatitude);
        double dLongitude = Math.toRadians(newLongitude - initialLongitude);
        double a = Math.sin(dLatitude / 2) * Math.sin(dLatitude / 2) +
                Math.cos(Math.toRadians(initialLatitude)) * Math.cos(Math.toRadians(newLatitude)) *
                        Math.sin(dLongitude / 2) * Math.sin(dLongitude / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadius * c;
        double meterConversion = 1609.00;
        return distance * meterConversion;
    }

    private void packData(double latitude, double longitude) {
        try {
            String gpsCoordinates = "[" + latitude + ", " + longitude + "]";
            shotInfo = new JSONObject();
            shotInfo.put(getString(R.string.json_string_app_id), appId);
            shotInfo.put(getString(R.string.json_string_rate), Integer.toString(rate) + getString(R.string.sign_meters));
            shotInfo.put(getString(R.string.json_string_gps), gpsCoordinates);
            shotInfo.put(getString(R.string.json_string_datetime), getDateTime());
            shotInfo.put(getString(R.string.json_string_orientation), getOrientation());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleClient, this);
        Log.d(TAG, "SERVICE DESTROYED");
    }

    private void loadCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "WRITE EXTERNAL STORAGE GRANTED");
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "CAMERA PERMISSION GRANTED");

            if (HiddenCameraUtils.canOverDrawOtherApps(this)) {
                CameraConfig cameraConfig = new CameraConfig()
                        .getBuilder(this)
                        .setCameraFacing(CameraFacing.FRONT_FACING_CAMERA)
                        .setCameraResolution(CameraResolution.HIGH_RESOLUTION)
                        .setImageFormat(CameraImageFormat.FORMAT_JPEG)
                        .build();

                startCamera(cameraConfig);
            } else {
                // Open settings to grant permission for "Draw other apps".
                Toast.makeText(TrackShootService.this, getString(R.string.message_overdraw), Toast.LENGTH_SHORT).show();
                HiddenCameraUtils.openDrawOverPermissionSetting(this);
            }
        } else {
            Log.d(TAG, "CAMERA PERMISSION DENIED");
        }
    }

    private void shootPicture() {
        Log.d(TAG, "READY TO SHOOT");

        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    takePicture();
                } catch (Exception e) {
                    Log.e(TAG, "CANNOT TAKE PICTURE!");
                }
            }
        }, 1000);
    }

    @Override
    public void onImageCapture(@NonNull File imageFile) {
        UploaderThread uploaderThread = new UploaderThread(this, shotInfo, imageFile);
        uploaderThread.execute();
    }

    @Override
    public void onCameraError(int errorCode) {
        switch (errorCode) {
            case CameraError.ERROR_CAMERA_OPEN_FAILED:
                // Camera open failed. Probably because another application is using the camera.
                Toast.makeText(this, "Cannot open camera.", Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_IMAGE_WRITE_FAILED:
                // Image write failed. Check for provided WRITE_EXTERNAL_STORAGE permission.
                Toast.makeText(this, "Cannot write image captured by camera.", Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_CAMERA_PERMISSION_NOT_AVAILABLE:
                // Camera permission is not available. Ask for the camera permission before initializing it.
                Toast.makeText(this, "Camera permission not available.", Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_OVERDRAW_PERMISSION:
                // Display information dialog to the user with steps to grant permission "Draw over other app"
                HiddenCameraUtils.openDrawOverPermissionSetting(this);
                Toast.makeText(this, "Overdraw permissions not available", Toast.LENGTH_LONG).show();
                break;
            case CameraError.ERROR_DOES_NOT_HAVE_FRONT_CAMERA:
                Toast.makeText(this, "Your device does not have front camera.", Toast.LENGTH_LONG).show();
                break;
        }
    }
}

