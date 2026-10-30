package com.ivor.openstream.presentation.settings

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

/**
 * Discover an explicitly advertised VistaStream receiver on the same LAN.
 * Service discovery is not authentication: the receiver's pairing code is
 * still required and all payloads use the existing authenticated encryption.
 */
internal class VistaLanDiscovery(context: Context) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var registration: NsdManager.RegistrationListener? = null

    fun discover(onFound: (String, String) -> Unit) {
        stopDiscovery()
        // Old Android releases may require a multicast lock while discovering.
        runCatching {
            lock = wifi?.createMulticastLock("vistastream-discovery")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != TYPE) return
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = info.host
                            if (host is Inet4Address && host.isSiteLocalAddress &&
                                info.port in 1024..65535) {
                                onFound(info.serviceName, host.hostAddress + ":" + info.port)
                            }
                        }
                    })
                }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { stopDiscovery() }
    }

    fun advertise(port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "VistaStream"
            serviceType = TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { registration = null }
    }

    fun stopDiscovery() {
        discovery?.let { listener ->
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        discovery = null
        runCatching { lock?.takeIf { it.isHeld }?.release() }
        lock = null
    }

    fun stopAdvertising() {
        registration?.let { listener ->
            runCatching { nsd.unregisterService(listener) }
        }
        registration = null
    }

    fun close() {
        stopDiscovery()
        stopAdvertising()
    }

    companion object {
        const val TYPE = "_vistastream._tcp."
    }
}
