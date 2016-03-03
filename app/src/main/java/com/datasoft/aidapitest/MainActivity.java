package com.datasoft.aidapitest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    static public final String TAG = "AIDAPITEST";

    TextView mLogView;

    Map<String, String> mDevices;
    BroadcastReceiver mDeviceFoundReceiver;
    BroadcastReceiver mStatusReceiver;
    BroadcastReceiver mConnectedReceiver;
    BroadcastReceiver mDisconnectedReceiver;
    BroadcastReceiver mInjuryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mLogView = (TextView) findViewById(R.id.log);

        mDevices = new HashMap<>();

        /* DEVICE_FOUND event receiver.
         * Received after sending a START_SCAN action, whenever an advertisement is received
         * from and AID device.  Will receive multiple events per device, must track duplicates.
         */
        mDeviceFoundReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String address = intent.getStringExtra("device-address");
                String name = intent.getStringExtra("device-name");
                Log.d(TAG, "Got DEVICE_FOUND event");
                if (name == null || name.isEmpty() || address == null || address.isEmpty()) {
                    return;
                }
                if (!mDevices.containsKey(name)) {
                    mDevices.put(name, address);
                    mLogView.append(String.format("Found %s <%s>\n", name, address));
                }
            }
        };
        IntentFilter deviceFilter = new IntentFilter("com.datasoft.aid.event.DEVICE_FOUND");
        registerReceiver(mDeviceFoundReceiver, deviceFilter);

        /* UPDATE_STATUS event receiver.
         * Received after sending a GET_STATUS action, contains current device status.
         */
        mStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received UPDATE_STATUS event");
                mLogView.append(String.format("Front Device (%s <%s>)\n",
                        intent.getStringExtra("front-device-name"),
                        intent.getStringExtra("front-device-address")));
                mLogView.append(String.format("Front Connected: %b\n",
                        intent.getBooleanExtra("front-device-connected", false)));
                mLogView.append(String.format("Front Battery: %d\n",
                        intent.getIntExtra("front-device-battery", -1)));
                mLogView.append(String.format("Front Injury: %d\n",
                        intent.getIntExtra("front-device-injury", 0)));
                mLogView.append(String.format("Back Device (%s <%s>)\n",
                        intent.getStringExtra("back-device-name"),
                        intent.getStringExtra("back-device-address")));
                mLogView.append(String.format("Back Connected: %b\n",
                        intent.getBooleanExtra("back-device-connected", false)));
                mLogView.append(String.format("Back Battery: %d\n",
                        intent.getIntExtra("back-device-battery", -1)));
                mLogView.append(String.format("Back Injury: %d\n\n",
                        intent.getIntExtra("back-device-injury", 0)));
            }
        };
        IntentFilter statusFilter = new IntentFilter("com.datasoft.aid.event.UPDATE_STATUS");
        registerReceiver(mStatusReceiver, statusFilter);

        /* DEVICE_DISCONNECTED event receiver.
         * Received when AID devices transition from connected -> disconnected state.
         */
        mDisconnectedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mLogView.append("AID Devices disconnected\n\n");
            }
        };
        IntentFilter disconnectedFilter = new IntentFilter("com.datasoft.aid.event.DEVICE_DISCONNECTED");
        registerReceiver(mDisconnectedReceiver, disconnectedFilter);

        /* DEVICE_CONNECTED event receiver.
         * Received when AID devices transition from disconnected -> connected state.
         */
        mConnectedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mLogView.append("AID Devices connected\n\n");
            }
        };
        IntentFilter connectedFilter = new IntentFilter("com.datasoft.aid.event.DEVICE_CONNECTED");
        registerReceiver(mConnectedReceiver, connectedFilter);

        /* INJURY event receiver
         * Received when a piercing event occurs
         */
        mInjuryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean frontUpper = intent.getBooleanExtra("front-upper", false);
                boolean frontLower = intent.getBooleanExtra("front-lower", false);
                boolean backUpper = intent.getBooleanExtra("back-upper", false);
                boolean backLower = intent.getBooleanExtra("back-lower", false);
                mLogView.append(String.format(
                        "Injury Detected.\n  Front <Upper: %b, Lower %b>.\n  Back <Upper: %b, Lower %b>\n\n",
                        frontUpper, frontLower, backUpper, backLower));
            }
        };
        IntentFilter injuryFilter = new IntentFilter("com.datasoft.aid.event.INJURY");
        registerReceiver(mInjuryReceiver, injuryFilter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mDeviceFoundReceiver);
        unregisterReceiver(mStatusReceiver);
        unregisterReceiver(mDisconnectedReceiver);
        unregisterReceiver(mConnectedReceiver);
        unregisterReceiver(mInjuryReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_scan:
                mLogView.setText("");
                mDevices.clear();
                startScan();
                break;
            case R.id.action_stop:
                stopScan();
                break;
            case R.id.action_status:
                requestStatus();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    void startScan() {
        Intent intent = new Intent();
        intent.setClassName("datasoft.com.aid", "com.datasoft.aid.ConnectionService");
        intent.setAction("com.datasoft.aid.action.START_SCAN");
        Log.d(TAG, "Sending START_SCAN intent");
        startService(intent);
    }

    void stopScan() {
        Intent intent = new Intent();
        intent.setClassName("datasoft.com.aid", "com.datasoft.aid.ConnectionService");
        intent.setAction("com.datasoft.aid.action.STOP_SCAN");
        Log.d(TAG, "Sending STOP_SCAN intent");
        startService(intent);
        mLogView.append("\n");
    }

    void requestStatus() {
        Intent intent = new Intent();
        intent.setClassName("datasoft.com.aid", "com.datasoft.aid.ConnectionService");
        intent.setAction("com.datasoft.aid.action.GET_STATUS");
        Log.d(TAG, "Sending GET_STATUS intent");
        startService(intent);
    }
}
