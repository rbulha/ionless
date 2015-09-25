package com.example.rogrio.bluett;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Rogerio on 25/08/2015.
 */
public class ReceptionBTinput  extends Thread {
    private final Handler mmParent_handler;
    private final String mmEqName;
    private final BluetoothAdapter mmBluetooth;

    private InputStream mBTInputStream;
    private OutputStream mBTOutputStream;
    private BluetoothSocket mBTSocket;

    public ReceptionBTinput(Handler parent_handler, String EqName, BluetoothAdapter mBluetooth) {
        mmParent_handler = parent_handler;
        mmEqName = EqName;
        mmBluetooth = mBluetooth;

        mBTInputStream = null;
        mBTOutputStream = null;
        mBTSocket = null;
    }

    private void resetConnection() {
        if (mBTInputStream != null) {
            try {mBTInputStream.close();} catch (Exception e) {}
            mBTInputStream = null;
        }

        if (mBTOutputStream != null) {
            try {mBTOutputStream.close();} catch (Exception e) {}
            mBTOutputStream = null;
        }

        if (mBTSocket != null) {
            try {mBTSocket.close();} catch (Exception e) {}
            mBTSocket = null;
        }

    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public File getAlbumStorageDir(String FileName) {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), FileName);
        if (!file.mkdirs()) {
            Log.d("IONLESS", "Directory not created: "+FileName);
        }
        return file;
    }

    private int SaveFile(byte [] buffer){
        if( isExternalStorageWritable()){
            File recebido = getAlbumStorageDir(mmEqName);
            if( recebido.canWrite()) {
                long files_count = recebido.listFiles().length;

                String sFileName = recebido.getPath() + String.format("//report_%02d.txt",files_count+1);

                try {
                    FileOutputStream outputStream = new FileOutputStream(sFileName);
                    outputStream.write(buffer);
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return 0;
                }

                return 1;
            }
            else {
                return 0;
            }
        }
        else {
            return 0;
        }
    }

    public void run() {
        int bytes; // bytes returned from read()
        int bToSend;
        boolean bconnected = false;
        UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        Communication_machine mCommMachine = new Communication_machine();

        /*
        mmBluetooth.disable();
        try {
            this.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mmBluetooth.enable();
        */

        mmBluetooth.cancelDiscovery();

        resetConnection();
        // --- bluetooth stuff
        Set<BluetoothDevice> pairedDevices = mmBluetooth.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {

                if ((device.getName() != null) && (device.getName().compareTo(mmEqName) == 0)) {
                    try {
                        mBTSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (IOException e) {
                        Log.d("DOWNLOAD", "Create socket: " + e.getMessage());
                        return;
                    }

                    int retry_number = 10;

                    do {
                        retry_number--;
                        bconnected = true;
                        try {
                            mBTSocket.connect();
                            break;
                        } catch (IOException connectException) {
                            Log.d("DOWNLOAD", "Connect: RETRY " + connectException.getMessage());
                            bconnected = false;
                            try {
                                this.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                    } while ((!bconnected) && (retry_number > 0));

                    if (!bconnected) {
                        mmParent_handler.obtainMessage(2, mmEqName + " can't connect").sendToTarget();
                        resetConnection();
                        return;
                    }
                    else {
                        try {
                            mBTInputStream = mBTSocket.getInputStream();
                            mBTOutputStream = mBTSocket.getOutputStream();

                        } catch (IOException e) {
                            mmParent_handler.obtainMessage(2, mmEqName + " can't connect - socket fail").sendToTarget();
                            resetConnection();
                            return;
                        }
                    }
                }
            }


            if (!bconnected) {
                mmParent_handler.obtainMessage(2, mmEqName + " device not found!").sendToTarget();
                resetConnection();
                return;
            }

            Log.d("DOWNLOAD", "Connected!");
            mmParent_handler.obtainMessage(3, "Conectado, aguarde o recebimento do arquivo ...").sendToTarget();


            try {
                mBTOutputStream.write('f'); //'f'  0x15 //O correto seria escrever NACK para inicio - revisar no firmware
            } catch (IOException e) {
                e.printStackTrace();
                mmParent_handler.obtainMessage(2, mmEqName + " error on first write(f)").sendToTarget();
                return;
            }
            // Keep listening to the InputStream until an exception occurs
            int iWakeupRetry = 4;
            while (true) {
                try {
                    /*
                    // Read from the InputStream
                    iWakeupRetry = 20;
                    while( mBTInputStream.available() == 0 ) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if(iWakeupRetry-- < 0) {
                            mmParent_handler.obtainMessage(2, mmEqName + " Error: device is not replying").sendToTarget();
                            Log.d("BTRECEPTION", "Device don't answer");
                            resetConnection();
                            return;
                        }
                    }
                    */
                    if( mBTInputStream == null) {
                        Log.d("BTRECEPTION", "mBTInputStream == null");
                        mmParent_handler.obtainMessage(3," communication exception!").sendToTarget();
                        return;
                    }

                    bytes = mBTInputStream.read(); //read(buffer,0,1);
                    if (bytes <= 0)
                        Log.d("BTRECEPTION", "ZERO BYTE READ: " + bytes);

                    if (bytes == 24) //0x18 - CAN - Cancel (force receiver to start sending)
                        Log.d("BTRECEPTION", "RECEIVE CAN: " + bytes);


                    bToSend = mCommMachine.communication_task(bytes);

                    switch (bToSend) {
                        case 0x04: //EOT
                            int res = SaveFile(mCommMachine.comm_save_file());
                            // Send the obtained bytes to the UI activity
                            mmParent_handler.obtainMessage(res, mmEqName + " OK - file saved").sendToTarget();
                            resetConnection();
                            break;
                        case 0x15: //NACK
                            // Send the obtained bytes to the UI activity
                            mmParent_handler.obtainMessage(3," communication NACK: " + bToSend).sendToTarget();
                            //resetConnection();
                            //break;
                        default:
                            if (bToSend > 0) mBTOutputStream.write(bToSend);
                            break;
                    }

                } catch (IOException e) {
                    mmParent_handler.obtainMessage(2, mmEqName + " exception: " + e.getMessage()).sendToTarget();
                    Log.d("BTRECEPTION", e.getMessage());
                    resetConnection();
                    break;
                }
            }
            mmParent_handler.obtainMessage(1, mmEqName + " Exit thread").sendToTarget();
            resetConnection();
        }
        else {
            mmParent_handler.obtainMessage(2, mmEqName + " no paired devices found").sendToTarget();
        }
    } // run()
}
