package br.geracaoai.controlesamsung.data

import android.content.Context

/** O que o cliente da TV precisa guardar (tokens, MAC, última TV). */
interface TvStore {
    var lastIp: String?
    var lastName: String?
    fun token(ip: String): String?
    fun setToken(ip: String, token: String?)
    fun mac(ip: String): String?
    fun setMac(ip: String, mac: String?)
}

/** Preferências locais (sem nuvem, sem login). Tokens guardados por IP da TV. */
class Prefs(context: Context) : TvStore {
    private val sp = context.applicationContext.getSharedPreferences("controle", Context.MODE_PRIVATE)

    override var lastIp: String?
        get() = sp.getString("lastIp", null)
        set(v) { sp.edit().putString("lastIp", v).apply() }

    override var lastName: String?
        get() = sp.getString("lastName", null)
        set(v) { sp.edit().putString("lastName", v).apply() }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) { sp.edit().putBoolean("haptics", v).apply() }

    var autoKeyboard: Boolean
        get() = sp.getBoolean("autoKeyboard", true)
        set(v) { sp.edit().putBoolean("autoKeyboard", v).apply() }

    override fun token(ip: String): String? = sp.getString("token_$ip", null)
    override fun setToken(ip: String, token: String?) { sp.edit().putString("token_$ip", token).apply() }

    override fun mac(ip: String): String? = sp.getString("mac_$ip", null)
    override fun setMac(ip: String, mac: String?) { sp.edit().putString("mac_$ip", mac).apply() }

    fun forget(ip: String) {
        sp.edit().remove("token_$ip").remove("mac_$ip").apply()
        if (lastIp == ip) { sp.edit().remove("lastIp").remove("lastName").apply() }
    }
}
