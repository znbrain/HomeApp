package io.github.domi04151309.home.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class DeviceSecrets(context: Context, private val id: String) {

    companion object {
        private const val DEFAULT_JSON = "{\"username\": \"\", \"password\": \"\"}"
    }

    private val masterKeyAlias = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val _prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "device_secrets",
        masterKeyAlias,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val secrets = JSONObject(
        _prefs.getString(id, DEFAULT_JSON)
            ?: DEFAULT_JSON
    )

    fun updateDeviceSecrets() {
        _prefs.edit().putString(id, secrets.toString()).apply()
    }

    fun deleteDeviceSecrets() {
        _prefs.edit().remove(id).apply()
    }

    var username: String
        get() {
            return if (secrets.has("username")) secrets.getString("username") else ""
        }
        set(value) {
            secrets.put("username", value)
        }

    var password: String
        get() {
            return if (secrets.has("password")) secrets.getString("password") else ""
        }
        set(value) {
            secrets.put("password", value)
        }
}