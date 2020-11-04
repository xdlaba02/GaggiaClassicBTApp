package com.lechaosx.gaggiaclassicbt

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter


class MainActivity : Activity() {
    private lateinit var currentTempValue: TextView
    private lateinit var currentOutputValue: TextView
    private lateinit var targetTempValue: TextView
    private lateinit var targetTempSendButton: Button
    private lateinit var kPValue: TextView
    private lateinit var kIValue: TextView
    private lateinit var kDValue: TextView
    private lateinit var kPIDSendButton: Button
    private lateinit var tempGraph: LineChart
    private lateinit var pidGraph: LineChart

    private var targetPointsSet = LineDataSet(null, "Target")
    private var tempPointsSet = LineDataSet(null, "Current")

    private var pidPointsSet = LineDataSet(null, "PID")
    private var proportionalPointsSet = LineDataSet(null, "P")
    private var integralPointsSet = LineDataSet(null, "I")
    private var derivativePointsSet = LineDataSet(null, "D")

    private var tempLineData = LineData()
    private var pidLineData = LineData()

    private var currentTime: Float = 0.0f
    private var currentTemp: Float = 0.0f
    private var targetTemp: Float = 0.0f
    private var currentProportional: Float = 0.0f
    private var currentIntegral: Float = 0.0f
    private var currentDerivative: Float = 0.0f
    private var currentPID: Float = 0.0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentTempValue = findViewById(R.id.currentTemp)
        currentOutputValue = findViewById(R.id.currentOutput)
        targetTempValue = findViewById(R.id.targetTemp)
        targetTempSendButton = findViewById(R.id.setTargetTemp)
        kPValue = findViewById(R.id.kP)
        kIValue = findViewById(R.id.kI)
        kDValue = findViewById(R.id.kD)
        kPIDSendButton = findViewById(R.id.setPIDParams)
        tempGraph = findViewById(R.id.tempGraph)
        pidGraph = findViewById(R.id.pidGraph)

        targetPointsSet.color = Color.GREEN;
        targetPointsSet.setDrawValues(false);
        targetPointsSet.setDrawCircles(false);
        targetPointsSet.mode = LineDataSet.Mode.STEPPED

        tempPointsSet.color = Color.RED;
        tempPointsSet.setDrawValues(false);
        tempPointsSet.setDrawCircles(false);
        tempPointsSet.mode = LineDataSet.Mode.LINEAR

        pidPointsSet.color = Color.YELLOW
        pidPointsSet.fillColor = Color.argb(127, 255, 255, 0)
        pidPointsSet.setDrawFilled(true)
        pidPointsSet.setDrawValues(false);
        pidPointsSet.setDrawCircles(false);
        pidPointsSet.mode = LineDataSet.Mode.LINEAR

        tempLineData.addDataSet(pidPointsSet)
        tempLineData.addDataSet(targetPointsSet)
        tempLineData.addDataSet(tempPointsSet)

        tempLineData.isHighlightEnabled = false

        tempGraph.data = tempLineData

        tempGraph.description.text = "Temperature"

        tempGraph.isDoubleTapToZoomEnabled = false

        tempGraph.axisLeft.isEnabled = false

        tempGraph.axisLeft.axisMinimum = 0.0f;
        tempGraph.axisRight.axisMinimum = 0.0f;

        tempGraph.axisRight.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%4.0f °C", value)
            }
        }

        tempGraph.xAxis.granularity = 1.0f
        tempGraph.xAxis.isGranularityEnabled = true

        tempGraph.xAxis.valueFormatter = object: ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                var output: String = "- "

                val timeDiff = currentTime - value

                if (timeDiff > 3600) {
                    output += String.format("%.0f h, ", timeDiff / 3600)
                }
                if (timeDiff > 60) {
                    output += String.format("%.0f m, ", timeDiff % 3600 / 60)
                }

                output += String.format("%.0f s", timeDiff % 60)

                return output
            }
        }


        proportionalPointsSet.color = Color.RED
        proportionalPointsSet.setDrawValues(false);
        proportionalPointsSet.setDrawCircles(false);
        proportionalPointsSet.mode = LineDataSet.Mode.LINEAR

        integralPointsSet.color = Color.GREEN
        integralPointsSet.setDrawValues(false);
        integralPointsSet.setDrawCircles(false);
        integralPointsSet.mode = LineDataSet.Mode.LINEAR

        derivativePointsSet.color = Color.BLUE
        derivativePointsSet.setDrawValues(false);
        derivativePointsSet.setDrawCircles(false);
        derivativePointsSet.mode = LineDataSet.Mode.LINEAR

        pidLineData.addDataSet(proportionalPointsSet)
        pidLineData.addDataSet(integralPointsSet)
        pidLineData.addDataSet(derivativePointsSet)

        pidLineData.isHighlightEnabled = false

        pidGraph.data = pidLineData

        pidGraph.description.text = "PID"

        pidGraph.isDoubleTapToZoomEnabled = false


        pidGraph.axisLeft.isEnabled = false

        pidGraph.xAxis.granularity = 1.0f
        pidGraph.xAxis.isGranularityEnabled = true
        pidGraph.xAxis.valueFormatter = tempGraph.xAxis.valueFormatter

        pidGraph.axisRight.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%3.0f %%", value)
            }
        }

        registerReceiver(currentTempReceiver, IntentFilter("CurrentTemperature"))
        registerReceiver(targetTempReceiver, IntentFilter("CurrentTarget"))
        registerReceiver(kPIDReceiver, IntentFilter("CurrentPID"))

        targetTempSendButton.setOnClickListener {
            BluetoothHandler.getInstance(applicationContext)?.setTarget(targetTempValue.text.toString())
        }

        kPIDSendButton.setOnClickListener {
            BluetoothHandler.getInstance(applicationContext)?.setPID(
                kPValue.text.toString(),
                kIValue.text.toString(),
                kDValue.text.toString()
            )
        }

        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_LONG).show()
            finish()
        }

        BluetoothHandler.getInstance(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(currentTempReceiver)
        unregisterReceiver(targetTempReceiver)
        unregisterReceiver(kPIDReceiver)
    }


    private val currentTempReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val timestamp = intent.getSerializableExtra("Timestamp").toString()
            val temp = intent.getSerializableExtra("Temperature").toString()

            val pid = intent.getSerializableExtra("PID").toString()

            val proportional = intent.getSerializableExtra("P").toString()
            val integral = intent.getSerializableExtra("I").toString()
            val derivative = intent.getSerializableExtra("D").toString()

            if (timestamp.toFloat() < currentTime) {
                tempGraph.clear()
                pidGraph.clear()
            }

            currentTime = timestamp.toFloat()
            currentTemp = temp.toFloat()
            currentPID = pid.toFloat()
            currentProportional = proportional.toFloat()
            currentIntegral = integral.toFloat()
            currentDerivative = derivative.toFloat()

            currentTempValue.text = String.format("%5.1f °C", currentTemp)
            currentOutputValue.text = String.format("%3.0f %%", currentPID * 100.0f)

            tempPointsSet.addEntry(Entry(currentTime, currentTemp))
            targetPointsSet.addEntry(Entry(currentTime, targetTemp))

            pidPointsSet.addEntry(Entry(currentTime, currentPID * targetTemp))
            proportionalPointsSet.addEntry(Entry(currentTime, currentProportional * 100.0f))
            integralPointsSet.addEntry(Entry(currentTime, currentIntegral * 100.0f))
            derivativePointsSet.addEntry(Entry(currentTime, currentDerivative * 100.0f))

            tempGraph.lineData.notifyDataChanged()
            tempGraph.notifyDataSetChanged()
            tempGraph.invalidate()

            pidGraph.lineData.notifyDataChanged()
            pidGraph.notifyDataSetChanged()
            pidGraph.invalidate()
        }
    }

    private val targetTempReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val target = intent.getSerializableExtra("Target").toString()
            targetTempValue.text = target
            targetTemp = target.toFloat()
        }
    }

    private val kPIDReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val kP = intent.getSerializableExtra("kP").toString()
            val kI = intent.getSerializableExtra("kI").toString()
            val kD = intent.getSerializableExtra("kD").toString()

            kPValue.text = kP
            kIValue.text = kI
            kDValue.text = kD
        }
    }
}