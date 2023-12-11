package org.itri.usbterminal;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialProber;

/**
 * add devices here, that are not known to DefaultProber
 *
 * if the App should auto start for these devices, also
 * add IDs to app/src/main/res/xml/usb_device_filter.xml
 */
class CustomProber {

    static UsbSerialProber getCustomProber() {
        ProbeTable customTable = new ProbeTable();
//        customTable.addProduct(0x26AC, 0x0011, CdcAcmSerialDriver.class); //pixhawk2 cube CDC
//        customTable.addProduct(0x2DAE, 0x1011, CdcAcmSerialDriver.class); //pixhawk2 cube CDC
//        customTable.addProduct(0x10C4, 0xEA06, CdcAcmSerialDriver.class); //simulation
        customTable.addProduct(0x2DAE, 0x1016, CdcAcmSerialDriver.class); //pixhawk2 cube CDC

        //customTable.addProduct(0x16d0, 0x087e, CdcAcmSerialDriver.class); // e.g. Digispark CDC
        return new UsbSerialProber(customTable);
    }

}
