package io.github.domi04151309.home.activities

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.net.wifi.WifiManager
import android.util.Log
import com._8rine.upnpdiscovery.UPnPDevice
import com._8rine.upnpdiscovery.UPnPDiscovery
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.toolbox.Volley
import io.github.domi04151309.home.R
import io.github.domi04151309.home.adapters.DeviceDiscoveryListAdapter
import io.github.domi04151309.home.data.DeviceItem
import io.github.domi04151309.home.data.ListViewItem
import io.github.domi04151309.home.helpers.Devices
import io.github.domi04151309.home.helpers.ShellyAPI
import io.github.domi04151309.home.helpers.Theme
import io.github.domi04151309.home.interfaces.RecyclerViewHelperInterface
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class SearchDevicesActivity : AppCompatActivity(), RecyclerViewHelperInterface {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DeviceDiscoveryListAdapter
    private lateinit var devices: Devices
    private lateinit var nsdManager: NsdManager
    private lateinit var discoveryListenerHttp: NsdManager.DiscoveryListener
    private lateinit var discoveryListenerSimpleHome: NsdManager.DiscoveryListener
    private lateinit var resolveListener: NsdManager.ResolveListener
    private var resolveListenerBusy = AtomicBoolean(false)
    private var pendingNsdServices = ConcurrentLinkedQueue<NsdServiceInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.set(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)

        recyclerView = findViewById(R.id.recyclerView)
        adapter = DeviceDiscoveryListAdapter(arrayListOf(ListViewItem(
            title = resources.getString(R.string.pref_add_search),
            summary = resources.getString(R.string.pref_add_search_summary),
            icon = R.drawable.ic_search
        )), this)
        devices = Devices(this)
        val addresses = mutableListOf<String>()
        val queue = Volley.newRequestQueue(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        //Device variables
        val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val routerIp = intToIp(manager.dhcpInfo.gateway)
        val customQuery = "M-SEARCH * HTTP/1.1" + "\r\n" +
                "HOST: 239.255.255.250:1900" + "\r\n" +
                "MAN: ssdp:discover" + "\r\n" +
                "MX: 10" + "\r\n" +
                "ST: ssdp:all" + "\r\n" +
                "\r\n"
        val customPort = 1900
        val customAddress = "239.255.255.250"

        Thread {
            //Add Router
            adapter.add(ListViewItem(
                title = resources.getString(R.string.pref_device_router),
                summary = routerIp,
                hidden = "Website#Router",
                icon = R.drawable.ic_device_router,
                state = devices.addressExists(routerIp)
            ))
            addresses += routerIp

            //Get Hue Bridges
            UPnPDiscovery.discoveryDevices(this, object : UPnPDiscovery.OnDiscoveryListener {
                override fun onStart() {}
                override fun onFoundNewDevice(device: UPnPDevice) {
                    if (device.server.contains("IpBridge") && !addresses.contains(device.hostAddress)) {
                        adapter.add(ListViewItem(
                            title = device.friendlyName,
                            summary = device.hostAddress,
                            hidden = "Hue API#Lamp",
                            icon = R.drawable.ic_device_lamp,
                            state = devices.addressExists(device.hostAddress)
                        ))
                        addresses += device.hostAddress
                    }
                }

                override fun onFinish(devices: HashSet<UPnPDevice>) {}
                override fun onError(e: Exception) {
                    Log.e("UPnPDiscovery", "Error: " + e.localizedMessage)
                }
            }, customQuery, customAddress, customPort)

            //Get compatible devices
            UPnPDiscovery.discoveryDevices(this, object : UPnPDiscovery.OnDiscoveryListener {
                override fun onStart() {}
                override fun onFoundNewDevice(device: UPnPDevice) {
                    val friendlyName = device.friendlyName
                    if (friendlyName.startsWith("FRITZ!") && !addresses.contains(device.hostAddress)) {
                        adapter.add(ListViewItem(
                            title = device.friendlyName,
                            summary = device.hostAddress,
                            hidden = "Website#Router",
                            icon = R.drawable.ic_device_router,
                            state = devices.addressExists(device.hostAddress)
                        ))
                        addresses += device.hostAddress
                    }
                    if (device.server.contains("SimpleHome") && !addresses.contains(device.hostAddress)) {
                        adapter.add(ListViewItem(
                            title = device.friendlyName,
                            summary = device.hostAddress,
                            hidden = "SimpleHome API#Raspberry Pi",
                            icon = R.drawable.ic_device_raspberry_pi,
                            state = devices.addressExists(device.hostAddress)
                        ))
                        addresses += device.hostAddress
                    }
                }

                override fun onFinish(devices: HashSet<UPnPDevice>) {}
                override fun onError(e: Exception) {
                    Log.e("UPnPDiscovery", "Error: " + e.localizedMessage)
                }
            })
        }.start()


        nsdManager = (getSystemService(NSD_SERVICE) as NsdManager)
        resolveListener =  object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                runOnUiThread {
                    if (serviceInfo.serviceType.equals("._simplehome._tcp")) {
                        val url = serviceInfo.attributes["url"]?.decodeToString()
                            ?: serviceInfo.host.hostAddress
                        adapter.add(ListViewItem(
                            title = serviceInfo.serviceName,
                            summary = url,
                            hidden = "SimpleHome API#Raspberry Pi",
                            icon = R.drawable.ic_device_raspberry_pi,
                            state = devices.addressExists(url)
                        ))
                    } else {
                        val pos = adapter.add(ListViewItem(
                            title = serviceInfo.serviceName,
                            summary = serviceInfo.host.hostAddress,
                            hidden = "Shelly Gen ${serviceInfo.attributes["gen"]?.decodeToString() ?: "1"}#Lamp",
                            icon = R.drawable.ic_device_lamp,
                            state = devices.addressExists(serviceInfo.host.hostAddress)
                        ))

                        queue.add(
                            ShellyAPI.loadName(
                                "http://" + serviceInfo.host.hostAddress + "/",
                                serviceInfo.attributes["gen"]?.decodeToString()?.toInt() ?: 1
                            ) { name ->
                                if (name.isNotEmpty()) adapter.changeTitle(pos, name)
                            }
                        )
                    }
                }
                resolveNextInQueue()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolveNextInQueue()
            }
        }

        class DnsDiscoveryListener : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(p0: String?, p1: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(p0: String?, p1: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                val lowService = service.serviceName.lowercase()
                if ((lowService.startsWith("shelly") && !lowService.startsWith("shellybutton1"))
                        || service.serviceType.equals("_simplehome._tcp.")
                ) {
                    if (resolveListenerBusy.compareAndSet(false, true))
                        nsdManager.resolveService(service, resolveListener)
                    else
                        pendingNsdServices.add(service)
                }
            }

            override fun onServiceLost(p0: NsdServiceInfo) {
                val iterator = pendingNsdServices.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().serviceName == p0.serviceName)
                        iterator.remove()
                }
            }

            override fun onDiscoveryStarted(p0: String?) { }
            override fun onDiscoveryStopped(p0: String?) { }
        }

        discoveryListenerHttp = DnsDiscoveryListener()
        nsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListenerHttp)

        discoveryListenerSimpleHome = DnsDiscoveryListener()
        nsdManager.discoverServices("_simplehome._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListenerSimpleHome)
    }

    internal fun resolveNextInQueue() {
        val nextNsdService = pendingNsdServices.poll()
        if (nextNsdService != null)
            nsdManager.resolveService(nextNsdService, resolveListener)
        else
            resolveListenerBusy.set(false)
    }

    private fun intToIp(address: Int): String {
        return (address and 0xFF).toString() + "." + (address shr 8 and 0xFF) + "." + (address shr 16 and 0xFF) + "." + (address shr 24 and 0xFF)
    }

    override fun onItemClicked(view: View, position: Int) {
        val name = view.findViewById<TextView>(R.id.title).text.toString()
        val hidden =  view.findViewById<TextView>(R.id.hidden).text.toString()
        if (hidden != "") {
            AlertDialog.Builder(this)
                .setTitle(R.string.pref_add_dialog)
                .setMessage(resources.getString(R.string.pref_add_dialog_message, name))
                .setPositiveButton(R.string.str_add) { _, _ ->
                    val newItem = DeviceItem(devices.generateNewId())
                    newItem.name = name
                    newItem.address = view.findViewById<TextView>(R.id.summary).text.toString()
                    newItem.mode = hidden.substring(0 , hidden.indexOf("#"))
                    newItem.iconName = hidden.substring(hidden.lastIndexOf("#") + 1)
                    devices.addDevice(newItem)
                    adapter.changeState(position, true)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
        }
    }

    override fun onStop() {
        super.onStop()
        nsdManager.stopServiceDiscovery(discoveryListenerHttp)
        nsdManager.stopServiceDiscovery(discoveryListenerSimpleHome)
    }
}
