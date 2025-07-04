
package com.example.hmirelay

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ToggleButton
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var usbSerialPort: UsbSerialPort? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSerial()

        val toggles = arrayOfNulls<ToggleButton>(16)
        for (i in 1..16) {
            val resId = resources.getIdentifier("toggle$i", "id", packageName)
            toggles[i-1] = findViewById(resId)
        }

        toggles.forEachIndexed { index, toggle ->
            toggle?.setOnCheckedChangeListener { _, isChecked ->
                sendCoilCommand(index + 1, isChecked)
            }
        }
    }

    private fun setupSerial() {
        val manager = getSystemService(USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            return
        }
        // Open first driver
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device) ?: return
        usbSerialPort = driver.ports[0] // Most devices have just one port
        usbSerialPort?.open(connection)
        usbSerialPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        val executor = Executors.newSingleThreadExecutor()
        executor.submit(SerialInputOutputManager(usbSerialPort, null))
    }

    private fun sendCoilCommand(coilNum: Int, state: Boolean) {
        usbSerialPort ?: return
        val address: Byte = 0x01 // Slave ID
        val function: Byte = 0x05 // Write Single Coil
        val coilAddress: Int = coilNum - 1
        val hiAddr: Byte = ((coilAddress shr 8) and 0xFF).toByte()
        val loAddr: Byte = (coilAddress and 0xFF).toByte()
        val hiVal: Byte = if (state) 0xFF.toByte() else 0x00.toByte()
        val loVal: Byte = 0x00
        val frameNoCrc = byteArrayOf(address, function, hiAddr, loAddr, hiVal, loVal)
        val crc = crc16(frameNoCrc)
        val frame = frameNoCrc + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        usbSerialPort?.write(frame, 1000)
    }

    private fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                if (crc and 0x0001 != 0) {
                    crc = crc shr 1
                    crc = crc xor 0xA001
                } else {
                    crc = crc shr 1
                }
            }
        }
        return crc
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            usbSerialPort?.close()
        } catch (e: Exception) {
        }
    }
}
