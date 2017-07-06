package com.trackshoot;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class TrackShootActivity extends AppCompatActivity {

    private TextView labelServiceStatus;
    private EditText editTextAppId;
    private TextView labelAppId;
    private TextView labelSubmissionRate;
    private RadioGroup radioGroupDistance;
    private Button buttonService;

    private Intent trackShootServiceIntent;

    private int rate;

    private static final String TAG = TrackShootActivity.class.getName();
    public static final String APP_ID = "APP_ID";
    public static final String RATE = "RATE";
    private static final int VITAL_PERMISSIONS_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_shoot);
        initComponents();
        requestPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Activate the appropriate UI and get the service if it's running.
        loadPreferences();
        if (isServiceRunning(TrackShootService.class)) {
            startTrackService();
            activateSecondaryUI();
        } else {
            activatePrimaryUI();
        }
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked.
        switch (view.getId()) {
            case R.id.button_radio_200m:
                if (checked)
                    rate = 200;
                break;

            case R.id.button_radio_100m:
                if (checked)
                    rate = 100;
                break;

            case R.id.button_radio_50m:
                if (checked)
                    rate = 50;
                break;
        }
    }

    private void initComponents() {
        // Initialize the UI components.
        labelServiceStatus = (TextView) findViewById(R.id.label_service_status);
        editTextAppId = (EditText) findViewById(R.id.edittext_app_id);
        labelAppId = (TextView) findViewById(R.id.label_app_id);
        labelSubmissionRate = (TextView) findViewById(R.id.label_submission_rate);
        radioGroupDistance = (RadioGroup) findViewById(R.id.radio_group_distance);
        buttonService = (Button) findViewById(R.id.button_service);

        labelServiceStatus.setText(getString(R.string.status_not_running));
        buttonService.setText(getString(R.string.button_start));

        buttonService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isServiceRunning(TrackShootService.class)) {
                    stopTrackService();
                    activatePrimaryUI();
                } else {
                    // Start the service only if the distance has been chosen.
                    if (rate == 0) {
                        Toast.makeText(getApplicationContext(), getString(R.string.message_choose_distance), Toast.LENGTH_SHORT).show();
                    } else {
                        startTrackService();
                        activateSecondaryUI();
                        savePreferences();
                    }
                }
            }
        });
    }

    private void activatePrimaryUI() {
        labelSubmissionRate.setText(getString(R.string.label_submission_rate));
        editTextAppId.setVisibility(View.VISIBLE);
        labelAppId.setVisibility(View.INVISIBLE);
        radioGroupDistance.setVisibility(View.VISIBLE);
        labelServiceStatus.setText(getString(R.string.status_not_running));
        buttonService.setText(getString(R.string.button_start));
    }

    private void activateSecondaryUI() {
        editTextAppId.setVisibility(View.INVISIBLE);
        labelAppId.setVisibility(View.VISIBLE);
        labelAppId.setText(getString(R.string.label_app_id_secondary) + editTextAppId.getText());
        labelSubmissionRate.setText(getString(R.string.label_submit_every) + rate + getString(R.string.sign_meters_space));
        radioGroupDistance.setVisibility(View.INVISIBLE);
        labelServiceStatus.setText(getString(R.string.status_running));
        buttonService.setText(getString(R.string.button_stop));
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE).edit();
        editor.putString(APP_ID, editTextAppId.getText().toString());
        editor.putInt(RATE, rate);
        editor.commit();
    }

    private void loadPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);
        editTextAppId.setText(sharedPreferences.getString(APP_ID, ""));
        rate = sharedPreferences.getInt(RATE, 0);

        switch (rate) {
            case 200:
                radioGroupDistance.check(R.id.button_radio_200m);
                break;
            case 100:
                radioGroupDistance.check(R.id.button_radio_100m);
                break;
            case 50:
                radioGroupDistance.check(R.id.button_radio_50m);
                break;
        }
    }

    private void startTrackService() {
        trackShootServiceIntent = new Intent(this, TrackShootService.class);
        trackShootServiceIntent.putExtra(APP_ID, editTextAppId.getText().toString());
        trackShootServiceIntent.putExtra(RATE, rate);
        startService(trackShootServiceIntent);
    }

    private void stopTrackService() {
        stopService(trackShootServiceIntent);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(TrackShootActivity.this, new String[]{
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        }, VITAL_PERMISSIONS_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == VITAL_PERMISSIONS_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED ||
                    grantResults[1] == PackageManager.PERMISSION_DENIED ||
                    grantResults[2] == PackageManager.PERMISSION_DENIED) {
                requestPermissions();
            }
        }
    }
}