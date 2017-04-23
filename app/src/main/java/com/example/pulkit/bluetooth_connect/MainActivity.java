package com.example.pulkit.bluetooth_connect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.IllegalFormatCodePointException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1;
    BluetoothAdapter bluetoothAdapter;

    ArrayList<BluetoothDevice> pairedDeviceArrayList;

    TextView textInfo, textStatus, textByteCnt;
    ListView listViewPairedDevice;
    EditText inputField;
    Button btnSend;

    ArrayAdapter<BluetoothDevice> pairedDeviceAdapter;
    private UUID myUUID;
    private final String UUID_STRING_WELL_KNOWN_SPP =
            "00001101-0000-1000-8000-00805F9B34FB";

    ThreadConnectBTdevice myThreadConnectBTdevice;
    ThreadConnected myThreadConnected;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textInfo = (TextView) findViewById(R.id.info);
        textStatus = (TextView)findViewById(R.id.status);
        textByteCnt = (TextView)findViewById(R.id.textbyteCnt);
        listViewPairedDevice = (ListView)findViewById(R.id.pairedlist);
        inputField = (EditText)findViewById(R.id.input);
        btnSend = (Button)findViewById(R.id.send);


        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(myThreadConnected!=null){
                    byte[] bytesToSend = inputField.getText().toString().getBytes();
                    myThreadConnected.write(bytesToSend);
                }
            }
        });

        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
            Toast.makeText(this, "BLuetooth Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter==null){
            Toast.makeText(this, "Bluetooth is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        if(!bluetoothAdapter.isEnabled()){
            Intent enableblue = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableblue,REQUEST_CODE);
        }

        setup();
    }

    private void setup() {
        Set<BluetoothDevice> pd = bluetoothAdapter.getBondedDevices();
        if(pd.size()>0){
            pairedDeviceArrayList = new ArrayList<BluetoothDevice>();

            for(BluetoothDevice device : pd){
                pairedDeviceArrayList.add(device);
            }

            pairedDeviceAdapter = new ArrayAdapter<BluetoothDevice>(this,android.R.layout.simple_list_item_1,pairedDeviceArrayList);
            listViewPairedDevice.setAdapter(pairedDeviceAdapter);

            listViewPairedDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    BluetoothDevice device = (BluetoothDevice) parent.getItemAtPosition(position);

                    Toast.makeText(MainActivity.this, device.getName(), Toast.LENGTH_SHORT).show();
                        textStatus.setText("Start Thread");
                        myThreadConnectBTdevice = new ThreadConnectBTdevice(device);
                        myThreadConnectBTdevice.start();

                }
            });
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (myThreadConnectBTdevice!=null){
            myThreadConnectBTdevice.cancel();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
     //   super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==REQUEST_CODE){
            if (resultCode==RESULT_OK){
                setup();
            }else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void startThreadConnected(BluetoothSocket socket){
        myThreadConnected = new ThreadConnected(socket);
        myThreadConnected.start();
    }


    private class ThreadConnectBTdevice extends Thread{
        private BluetoothSocket bluetoothSocket = null;
        private final BluetoothDevice bluetoothDevice;

        private ThreadConnectBTdevice(BluetoothDevice device){
            bluetoothDevice = device;
            try {
                bluetoothSocket =device.createInsecureRfcommSocketToServiceRecord(myUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                bluetoothSocket.connect();
                success = true;
            } catch (IOException e) {
                e.printStackTrace();

                final String eMessage = e.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textStatus.setText("Something is wrong " + eMessage);
                    }
                });

                try {
                    bluetoothSocket.close();
                } catch (IOException em) {
                    em.printStackTrace();
                }
            }

            if (success){
                final String msg = "Connect Successful";
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textStatus.setText("");
                        textByteCnt.setText("");
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                });

                startThreadConnected(bluetoothSocket);
            }
        }
        public  void cancel(){
            Toast.makeText(getApplicationContext(), "Close", Toast.LENGTH_SHORT).show();

            try{
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ThreadConnected extends Thread{

        private final BluetoothSocket connectedSocket;
        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;


        private ThreadConnected(BluetoothSocket socket) {
            connectedSocket = socket;
            InputStream in = null;
            OutputStream out = null;


            try {
                in = socket.getInputStream();
                out= socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            String str = "";

            while(true){
                try{
                    bytes = connectedInputStream.read(buffer);
                    final String received = new String(buffer,0,bytes);
                    final String byteCnt = String.valueOf(bytes)+"bytes received \n";

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textStatus.append(received);
                            textByteCnt.append(byteCnt);
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();

                    final String connectionLost = "Connection Lost\n"+e.getMessage();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textStatus.setText(connectionLost);
                        }
                    });
                }
            }
        }

        public void write(byte[] buffer){
            try{
                connectedOutputStream.write(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel(){
            try {
                connectedSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}