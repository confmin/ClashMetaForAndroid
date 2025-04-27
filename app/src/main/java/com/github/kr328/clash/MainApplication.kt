package com.github.kr328.clash

import android.app.Application
import android.content.Context
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.sendProfileChanged
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


@Suppress("unused")
class MainApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName
        extractGeoFiles()

        Log.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()
            // Import profile
            Global.launch {
                try {
                    val configFile = assets.open("config.json")
                    val configContent = configFile.bufferedReader().use { it.readText() }
                    val config = JSONObject(configContent)
                    val uri = config.getString("profile_url")
                    
                    withProfile {
                        val existingProfile = queryAll().find { it.source == uri }
                        if (existingProfile == null) {
                            val uuid = create(Profile.Type.Url,
                                "VPN",
                                uri)
                            val profile = queryByUUID(uuid)
                            update(uuid)
                            coroutineScope {
                                commit(uuid) {
                                    launch {
                                        setActive(profile!!)
                                    }
                                }
                            }
                            val active = queryActive()
                            if (active != null) {
                                sendProfileChanged(active.uuid)
                            } else {
                                setActive(profile!!)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Failed to import profile: ${e.message}")
                }
            }
        } else {
            sendServiceRecreated()
        }
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs();

        val geoipFile = File(clashDir, "geoip.metadb")
        if(!geoipFile.exists()) {
            FileOutputStream(geoipFile).use {
                assets.open("geoip.metadb").copyTo(it);
            }
        }

        val geositeFile = File(clashDir, "geosite.dat")
        if(!geositeFile.exists()) {
            FileOutputStream(geositeFile).use {
                assets.open("geosite.dat").copyTo(it);
            }
        }
        
        val ASNFile = File(clashDir, "ASN.mmdb")
        if(!ASNFile.exists()) {
            FileOutputStream(ASNFile).use {
                assets.open("ASN.mmdb").copyTo(it);
            }
        }
    }

    fun finalize() {
        Global.destroy()
    }
}