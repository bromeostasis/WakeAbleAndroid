package com.avengers.wakeable;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ToggleButton;
import android.widget.Button;
import java.util.Calendar;

public class MainActivity extends Activity {


    private static PendingIntent pendingIntent;
    private static LogService ls = new LogService();

    private static AlarmManager alarmManager;
    private TimePicker alarmTimePicker;
    private static TextView status;
    private static MainActivity inst;
    private static ToggleButton alarmToggle;
    private static Button reconnectButton;
    private static ImageView imageStatus;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private boolean inForeground = false;
    private static SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    private static final int SCAN_PERIOD = 7500;
    private final static int REQUEST_ENABLE_BT = 1;
    private final String PREFS = "preferences";
    private static final String TAG = "MainActivity";
    public static final int requestCode = 9999;


    public static MainActivity instance() {
        return inst;
    }

    @Override
    public void onStart() {

        inForeground = true;
        super.onStart();
        inst = this;
    }

    @Override
    protected void onDestroy() {

        unbindService(mServiceConnection);
        ls.logString(TAG, "Destroyed");
        editor.putBoolean("connected", false);
        editor.commit();

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        inForeground = false;
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        inst = this;

        prefs = getApplicationContext().getSharedPreferences(PREFS, Activity.MODE_PRIVATE);

        if (prefs.getString("macAddress", "empty").equals("empty")) {
            onResume();
            Intent setup = new Intent(this, SetupActivity.class);
            startActivity(setup);
        }

        editor = prefs.edit();
        editor.remove("connected");
        editor.commit();

        inForeground = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        alarmTimePicker = (TimePicker) findViewById(R.id.alarmTimePicker);
        alarmToggle = (ToggleButton) findViewById(R.id.alarmToggle);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        reconnectButton = (Button) findViewById(R.id.reconnect);
        status = (TextView) findViewById(R.id.status);
        imageStatus = (ImageView) findViewById(R.id.imageStatus);


        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Bind service
        Intent bleServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(bleServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    public static void changeToggle() {
        alarmToggle.toggle();
    }

    public static void toggleConnectionButton(){
        boolean connected;
        if (prefs != null) {
            connected = prefs.getBoolean("connected", false);
            ls.logString(TAG, "connected value: " + connected);

            if (connected){

                inst.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reconnectButton.setVisibility(View.INVISIBLE);
                        status.setText(R.string.connected);
                        imageStatus.setImageResource(R.drawable.bluetooth);
                    }
                });
            }
            else{
                inst.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reconnectButton.setVisibility(View.VISIBLE);
                        status.setText(R.string.connection_required);
                        imageStatus.setImageResource(R.drawable.exclamation);
                    }
                });
            }
        }
    }

    public void onToggleClicked(final View view) {
        if (((ToggleButton) view).isChecked()) {
            boolean connected = prefs.getBoolean("connected", false);

            if (connected) {
                ls.logString("MyActivity", "Alarm On");
                Calendar selectedTime = getSelectedTime();
                Intent myIntent = new Intent(getBaseContext(), AlarmReceiver.class);
                pendingIntent = PendingIntent.getBroadcast(getBaseContext(), 0, myIntent, 0);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, selectedTime.getTimeInMillis(), pendingIntent);
                ls.logString("MyActivity", String.valueOf(selectedTime.getTime()));
            } else {
                ls.logString("Main", "Could not connect to bluetooth device. Not setting alarm");

                AlertDialog.Builder builder = new AlertDialog.Builder(inst);
                builder.setMessage(R.string.set_without_connection)
                    .setPositiveButton(R.string.set_anyway, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Calendar selectedTime = getSelectedTime();
                            Intent myIntent = new Intent(getBaseContext(), AlarmReceiver.class);
                            pendingIntent = PendingIntent.getBroadcast(getBaseContext(), 0, myIntent, 0);
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, selectedTime.getTimeInMillis(), pendingIntent);
                            ls.logString("MyActivity", String.valueOf(selectedTime.getTime()));
                        }
                    })
                    .setNegativeButton(R.string.reconnect_first, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                            ((ToggleButton) view).toggle();
                        }
                    });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        } else {
            alarmManager.cancel(pendingIntent);
            ls.logString("MyActivity", "Alarm Off");
        }
    }


    public boolean isInForeground() {
        return inForeground;
    }

    public void reconnect(final View view) {

        String address = prefs.getString("macAddress", "empty");
        if (!address.equals("empty")) {
            boolean connected = prefs.getBoolean("connected", false);
            if (!connected) {
                mBluetoothLeService.connect(address, mBluetoothAdapter);
                checkConnectionAfterWait(true);
            }
            else{
                ls.logString(TAG, "How did they click this? Just resetting the button");
                toggleConnectionButton();
            }
        }
    }



    private Calendar getSelectedTime(){
        Calendar today = Calendar.getInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, alarmTimePicker.getCurrentHour());
        calendar.set(Calendar.MINUTE, alarmTimePicker.getCurrentMinute());
        calendar.set(Calendar.SECOND, 0);
        if (calendar.compareTo(today) < 0) {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.get(Calendar.DAY_OF_WEEK) + 1);
        }
        return calendar;
    }

    private void checkConnectionAfterWait(final boolean showMessage){
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toggleConnectionButton();
                boolean connected = prefs.getBoolean("connected", false);
                if (!connected) {
                    ls.logString(TAG, "Reconnect failed");
                    if (showMessage) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(inst);
                        builder.setMessage(R.string.reconnect_fail)
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }

            }
        }, SCAN_PERIOD);
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();


            String address = prefs.getString("macAddress", "empty");
            if (!address.equals("empty")) {
                boolean connected = prefs.getBoolean("connected", false);
                if (!connected) {
                    ls.logString(TAG, "Back from killed state. Let's try to connect to the device");
                    mBluetoothLeService.connect(address, mBluetoothAdapter);
                    checkConnectionAfterWait(false);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
}