package com.homeworkoutcoach.app.scale

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class ScanProblem {
    /** The phone has no Bluetooth Low Energy. */
    NO_BLUETOOTH,
    BLUETOOTH_OFF,
    NO_PERMISSION,
    FAILED,
}

/** Where weigh-in readings come from; tests use a fake instead of the radio. */
interface ScaleSource {
    fun start(onReading: (ScaleReading) -> Unit, onProblem: (ScanProblem) -> Unit)
    fun stop()
}

/**
 * Listens for Flexscale broadcasts. Nothing is paired or connected; the scale advertises each reading.
 * Start and stop on the main thread.
 */
class ScaleScanner(private val context: Context) : ScaleSource {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: ScanCallback? = null

    /** Android 12+: "Nearby devices" (declared as not used for location). Older: location, which BLE scans need. */
    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermission() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Null when ready to scan. */
    fun problem(): ScanProblem? = when {
        adapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> ScanProblem.NO_BLUETOOTH
        !adapter.isEnabled -> ScanProblem.BLUETOOTH_OFF
        !hasPermission() -> ScanProblem.NO_PERMISSION
        else -> null
    }

    @SuppressLint("MissingPermission") // checked in problem()
    override fun start(onReading: (ScaleReading) -> Unit, onProblem: (ScanProblem) -> Unit) {
        stop()
        problem()?.let { return onProblem(it) }
        val scanner = adapter?.bluetoothLeScanner ?: return onProblem(ScanProblem.BLUETOOTH_OFF)
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Flexscale.parse(result.scanRecord?.getManufacturerSpecificData(Flexscale.COMPANY_ID))?.let(onReading)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { onScanResult(0, it) }

            override fun onScanFailed(errorCode: Int) = onProblem(ScanProblem.FAILED)
        }
        callback = cb
        val filter = ScanFilter.Builder()
            .setManufacturerData(Flexscale.COMPANY_ID, Flexscale.HEADER, byteArrayOf(-1, -1))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        scanner.startScan(listOf(filter), settings, cb)
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        val cb = callback ?: return
        callback = null
        val a = adapter ?: return
        if (hasPermission() && a.isEnabled) runCatching { a.bluetoothLeScanner?.stopScan(cb) }
    }
}
