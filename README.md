# ionless

Bluetooth communication module for Android

use like this:

                mMain_handler = new BTHandler(this);

                ReceptionBTinput recpt_thr = new ReceptionBTinput(mMain_handler,selectedDev,mBluetooth);

                recpt_thr.start();
