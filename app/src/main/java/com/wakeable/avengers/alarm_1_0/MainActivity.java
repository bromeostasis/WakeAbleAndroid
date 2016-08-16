package com.wakeable.avengers.alarm_1_0;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ToggleButton;
import android.widget.Button;
import java.util.Calendar;

public class MainActivity extends Activity {

    AlarmManager alarmManager;
    private final String PREFS = "preferences";
    private PendingIntent pendingIntent;
    private TimePicker alarmTimePicker;
    private Button bluetoothButton;
    private static MainActivity inst;
    private TextView alarmTextView;
    private static ToggleButton alarmToggle;
    private boolean inForeground = false;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;

    public static MainActivity instance() {
        return inst;
    }

    @Override
    public void onStart() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.commit();

        inForeground = true;
        super.onStart();
        inst = this;
    }

    @Override
    protected void onDestroy() {

        unbindService(mServiceConnection);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        inForeground = false;
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Bind service
        Intent bleServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(bleServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        inForeground = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothButton = (Button) findViewById(R.id.bluetoothButton);
        alarmTimePicker = (TimePicker) findViewById(R.id.alarmTimePicker);
        alarmTextView = (TextView) findViewById(R.id.alarmText);
        alarmToggle = (ToggleButton) findViewById(R.id.alarmToggle);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);


        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public static void changeToggle(){
        alarmToggle.toggle();
    }

    public void onToggleClicked(View view) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        if (!prefs.getString("macAddress", "empty").equals("empty")) {
            if (((ToggleButton) view).isChecked()) {

                String address = prefs.getString("macAddress", "We Fucked UP");
                Boolean status = mBluetoothLeService.connect(address, mBluetoothAdapter);

                if (status == true) {
                    Log.d("MyActivity", "Alarm On");
                    Calendar today = Calendar.getInstance();
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR_OF_DAY, alarmTimePicker.getCurrentHour());
                    calendar.set(Calendar.MINUTE, alarmTimePicker.getCurrentMinute());
                    calendar.set(Calendar.SECOND, 0);
                    if (calendar.compareTo(today) < 0) {
                        Log.d("MainActivity", "Let's set this for tomorrow?");
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.get(Calendar.DAY_OF_WEEK) + 1);
                        Log.d("MainActivity", "Cool, now we've got: " + String.valueOf(calendar.getTime()));
                    }
                    Intent myIntent = new Intent(getBaseContext(), AlarmReceiver.class);
                    pendingIntent = PendingIntent.getBroadcast(getBaseContext(), 0, myIntent, 0);
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                    Log.d("MyActivity", String.valueOf(calendar.getTime()));
                }
                else{
                    Log.d("Main", "Could not connect to bluetooth device. Not setting alarm");
                }
            } else {
                alarmManager.cancel(pendingIntent);
                setAlarmText("");
                Log.d("MyActivity", "Alarm Off");
            }
        }
        else{
            ((ToggleButton) view).toggle();
            AlertDialog.Builder builder = new AlertDialog.Builder(inst);

            builder.setMessage("You do not have a bluetooth device paired, you can't set an alarm yet!");

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    public void onBluetoothClicked(View view){
        Intent btIntent = new Intent(getApplicationContext(), BluetoothActivity.class);
        startActivity(btIntent);
    }

    public void onDeviceClicked(View view){
        Intent deviceIntent = new Intent(getApplicationContext(), DeviceActivityLe.class);
        startActivity(deviceIntent);
    }

    public void setAlarmText(String alarmText) {
        alarmTextView.setText(alarmText);
    }
    public boolean isInForeground(){return inForeground;}



    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
}