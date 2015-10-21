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
            Log.w("IONLESS", "Directory not created: "+FileName);
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

    private boolean connect() {
        UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
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
                        Log.d("IONLESS", "Create socket: " + e.getMessage());
                        return false;
                    }

                    int retry_number = 10;

                    do {
                        retry_number--;
                        try {
                            mBTSocket.connect();
                            mBTInputStream = mBTSocket.getInputStream();
                            mBTOutputStream = mBTSocket.getOutputStream();
                            return true;
                        } catch (IOException connectException) {
                            Log.d("IONLESS", "Connect: RETRY " + connectException.getMessage());
                            try {
                                this.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                    } while (retry_number > 0);

                    mmParent_handler.obtainMessage(2, mmEqName + " can't connect").sendToTarget();
                    resetConnection();
                }
            }
        }

        return false;
    }

    private boolean ping(int iPingRetry) {
        int bytes = 0;
        boolean response=true;

        try {
            while((iPingRetry > 0)) { //(bytes != 'a') &&
                iPingRetry--;
                mBTOutputStream.write('a');
                Log.d("IONLESS", "PING - "+iPingRetry);
                waitbt(100);
                if( mBTInputStream.available() != 0) {
                    bytes = mBTInputStream.read();
                    Log.d("IONLESS", "PING RESP: " + bytes);
                    if (bytes != 'a') response = false;
                    else response = true;
                }
                else {
                    Log.d("IONLESS", "PING FAIL: " + bytes);
                    waitbt(5000);
                }
            }

            mBTOutputStream.flush();

            waitbt(1000);

            while( mBTInputStream.available() != 0 ){
                bytes = mBTInputStream.read();
                Log.d("IONLESS", "PING RESP RESIDUAL: "+bytes);
            }

        } catch (IOException e) {
            e.printStackTrace();
            mmParent_handler.obtainMessage(2, mmEqName + " error on first write(a)").sendToTarget();
            return false;
        }

        return response;
        /*
        if(iPingRetry > 0)
            return true;
        else
            return false;
        */
    }

    private void waitbt(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean readFile(int iFilesToRead) {
        int bytes; // bytes returned from read()
        int bToSend;
        int iCompleted = 15;

        Communication_machine mCommMachine = new Communication_machine();

        mmParent_handler.obtainMessage(3, "Recebendo o arquivo: "+iFilesToRead+" - "+iCompleted+"%").sendToTarget();

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
                        Log.d("IONLESS", "Device don't answer");
                        resetConnection();
                        return;
                    }
                }
                */
                if( mBTInputStream == null) {
                    Log.d("IONLESS", "mBTInputStream == null");
                    mmParent_handler.obtainMessage(3," communication exception!").sendToTarget();
                    return false;
                }

                Log.d("IONLESS", "READ FROM IS");
                bytes = mBTInputStream.read(); //read(buffer,0,1);
                Log.d("IONLESS", "READ FROM IS: "+bytes);
                if (bytes <= 0)
                    Log.d("IONLESS", "ZERO BYTE READ: " + bytes);

                if (bytes == 24) //0x18 - CAN - Cancel (force receiver to start sending)
                    Log.d("IONLESS", "RECEIVE CAN: " + bytes);

                bToSend = mCommMachine.communication_task(bytes);
                Log.d("IONLESS", "COM MACHINE TOSEND: "+bToSend);

                mmParent_handler.obtainMessage(3, "Recebendo o arquivo: "+iFilesToRead+" - "+(iCompleted++)+"%").sendToTarget();

                switch (bToSend) {
                    case 0x04: //EOT
                        int res = SaveFile(mCommMachine.comm_save_file());
                        // Send the obtained bytes to the UI activity
                        // mmParent_handler.obtainMessage(res, mmEqName + " OK - file saved").sendToTarget();
                        //resetConnection();
                        Log.d("IONLESS", "EOT: "+bytes);
                        return true;
                    case 0x15: //NACK
                        // Send the obtained bytes to the UI activity
                        mmParent_handler.obtainMessage(3," communication NACK: " + bToSend).sendToTarget();
                        //resetConnection();
                        //break;
                        Log.d("IONLESS", "NACK: "+bytes);
                        return false;
                    default:
                        if (bToSend > 0) mBTOutputStream.write(bToSend);
                        break;
                }

            } catch (IOException e) {
                mmParent_handler.obtainMessage(2, mmEqName + " exception: " + e.getMessage()).sendToTarget();
                Log.d("IONLESS", e.getMessage());
                //resetConnection();
                return false;
            }
        }
    }

    public void run() {
        mmParent_handler.obtainMessage(3, mmEqName + " Enabling bluetooth!").sendToTarget();
        mmBluetooth.cancelDiscovery();
        /*
        mmBluetooth.disable();
        waitbt(1000);
        mmBluetooth.enable();
        waitbt(5000);
        */
        mmParent_handler.obtainMessage(3, mmEqName + " Bluetooth ready!").sendToTarget();

        if (!connect()) {
            mmParent_handler.obtainMessage(2, mmEqName + " device not found!").sendToTarget();
            resetConnection();
            return;
        }

        Log.d("IONLESS", "Connected!");

        int iFilesToRead = 10;
        do {
            mmParent_handler.obtainMessage(3, "Recebendo o arquivo: "+iFilesToRead+" - 0%").sendToTarget();

            waitbt(1000);

            if (!ping(5)) {
                mmParent_handler.obtainMessage(2, mmEqName + " device is not responding!").sendToTarget();
                resetConnection();
                return;
            }

            mmParent_handler.obtainMessage(3, "Recebendo o arquivo: "+iFilesToRead+" - 2%").sendToTarget();

            waitbt(1000);

            try {
                mBTOutputStream.write('f'); //'f'  0x15 //O correto seria escrever NACK para inicio - revisar no firmware
                Log.d("IONLESS", "WRITE F: ");
            } catch (IOException e) {
                e.printStackTrace();
                mmParent_handler.obtainMessage(2, mmEqName + " error on first write(f)").sendToTarget();
                return;
            }
            mmParent_handler.obtainMessage(3, "Recebendo o arquivo: "+iFilesToRead+" - 10%").sendToTarget();

        }while(readFile(iFilesToRead) && (iFilesToRead-- > 0));
        // Keep listening to the InputStream until an exception occurs


        Log.d("IONLESS", "Exit thread!");
        mmParent_handler.obtainMessage(1, mmEqName + " Exit thread").sendToTarget();
        resetConnection();
    } // run()
}
