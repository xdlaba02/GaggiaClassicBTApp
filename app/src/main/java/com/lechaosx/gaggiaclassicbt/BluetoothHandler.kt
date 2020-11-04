package com.lechaosx.gaggiaclassicbt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.welie.blessed.*
import java.util.*


internal class BluetoothHandler private constructor(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    private var mPeripheral: BluetoothPeripheral? = null
    private var mPID: BluetoothGattCharacteristic? = null
    private var mTarget: BluetoothGattCharacteristic? = null
    private var mTemp: BluetoothGattCharacteristic? = null

    fun setTarget(target: String) {
        mTarget?.let { mPeripheral?.writeCharacteristic(it, target.toByteArray(), WRITE_TYPE_DEFAULT) }
    }

    fun setPID(kP: String, kI: String, kD: String) {
        mPID?.let { mPeripheral?.writeCharacteristic(it, "$kP $kI $kD".toByteArray(), WRITE_TYPE_DEFAULT) }
    }

    private val peripheralCallback: BluetoothPeripheralCallback =
        object : BluetoothPeripheralCallback() {
            override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
                peripheral.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                if (!peripheral.requestMtu(64)) {
                    return;
                }

                if (peripheral.getService(SERVICE_UUID) != null) {
                    mPeripheral = peripheral

                    mPID   = peripheral.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID_KPID)
                    mTarget = peripheral.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID_TARGET)
                    mTemp   = peripheral.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID_TEMP)

                    mPID?.let { peripheral.readCharacteristic(it) }
                    mTarget?.let { peripheral.readCharacteristic(it) }
                    mTemp?.let { peripheral.readCharacteristic(it) }

                    mTemp?.let { peripheral.setNotify(it, true) }
                    mTarget?.let { peripheral.setNotify(it, true) }
                    mPID?.let { peripheral.setNotify(it, true) }

                    context.sendBroadcast(Intent("PeripheralInitialized"))
                }
            }

            override fun onNotificationStateUpdate(peripheral: BluetoothPeripheral, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothPeripheral.GATT_SUCCESS) {
                    Toast.makeText(context, "Unable to toggle notifications!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCharacteristicWrite(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothPeripheral.GATT_SUCCESS) {
                    Toast.makeText(context, "Unable to write characteristic!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCharacteristicUpdate(peripheral: BluetoothPeripheral, value: ByteArray, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothPeripheral.GATT_SUCCESS) {
                    return
                }

                val message = String(value)

                when (characteristic.uuid) {
                    CHARACTERISTIC_UUID_TEMP -> {
                        val values = message.split(" ")


                        val intent = Intent("CurrentTemperature")
                        intent.putExtra("Temperature", values[0])
                        intent.putExtra("P", values[1])
                        intent.putExtra("I", values[2])
                        intent.putExtra("D", values[3])
                        intent.putExtra("PID", values[4])
                        intent.putExtra("Timestamp", values[5])

                        context.sendBroadcast(intent)
                    }

                    CHARACTERISTIC_UUID_KPID -> {
                        val values = message.split(" ")

                        val intent = Intent("CurrentPID")
                        intent.putExtra("kP", values[0])
                        intent.putExtra("kI", values[1])
                        intent.putExtra("kD", values[2])

                        context.sendBroadcast(intent)
                    }

                    CHARACTERISTIC_UUID_TARGET -> {
                        val intent = Intent("CurrentTarget")
                        intent.putExtra("Target", message)
                        context.sendBroadcast(intent)
                    }
                }
            }
        }

    private val bluetoothCentralCallback: BluetoothCentralCallback =
        object : BluetoothCentralCallback() {
            override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
                Toast.makeText(context, "Gaggia Classic connected!", Toast.LENGTH_LONG).show()
            }

            override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: Int) {
                Toast.makeText(context, "Connection failed!", Toast.LENGTH_LONG).show()
            }

            override fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: Int) {
                Toast.makeText(context, "Gaggia Classic disconnected!", Toast.LENGTH_LONG).show()

                handler.postDelayed({
                    central.autoConnectPeripheral(peripheral, peripheralCallback)
                }, 5000)

                context.sendBroadcast(Intent("PeripheralDisconnected"))
            }

            override fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
                central.stopScan()
                central.connectPeripheral(peripheral, peripheralCallback)
            }

            override fun onBluetoothAdapterStateChanged(state: Int) {
                if (state == BluetoothAdapter.STATE_ON) {
                    central.startPairingPopupHack()
                    central.scanForPeripheralsWithAddresses(arrayOf("24:62:AB:FF:77:92"))
                }
            }
        }

    private val central: BluetoothCentral = BluetoothCentral(context, bluetoothCentralCallback, handler)

    companion object {
        private var instance: BluetoothHandler? = null

        private val SERVICE_UUID = UUID.fromString("74ab2f66-bd28-11ea-b3de-0242ac130004")
        private val CHARACTERISTIC_UUID_KPID = UUID.fromString("74ab31b4-bd28-11ea-b3de-0242ac130004")
        private val CHARACTERISTIC_UUID_TEMP = UUID.fromString("1ef4a71e-c1ce-11ea-b3de-0242ac130004")
        private val CHARACTERISTIC_UUID_TARGET = UUID.fromString("1ef4a818-c1ce-11ea-b3de-0242ac130004")

        @Synchronized
        fun getInstance(context: Context): BluetoothHandler? {
            if (instance == null) {
                instance = BluetoothHandler(context.applicationContext)
            }
            return instance
        }
    }

    init {
        central.startPairingPopupHack()
        central.scanForPeripheralsWithAddresses(arrayOf("24:62:AB:FF:77:92"))
    }
}