package net.sfelabs.knox.core.common.domain

import android.util.Base64
import org.json.JSONObject

fun parseHdmPolicyBlock(hdmResponse: String): Int {
    val body = hdmResponse.split(".")[1]
    val json = JSONObject(String(Base64.decode(body, Base64.DEFAULT or Base64.URL_SAFE)))
    return json.getString("deviceBlock").substringAfter("x").toInt(16)
}

fun parseHdmCompromiseBlock(hdmResponse: String): Int {
    val body = hdmResponse.split(".")[1]
    val json = JSONObject(String(Base64.decode(body, Base64.DEFAULT or Base64.URL_SAFE)))
    return json.getString("compromiseBlock").substringAfter("x").toInt(16)
}

//TODO: policyVersion is just a hash of what is currently set, not what the device supports. This needs fixed by the HDM team.
fun parseHdmPolicyVersion(hdmResponse: String): Int {
    val body = hdmResponse.split(".")[1]
    val json = JSONObject(String(Base64.decode(body, Base64.DEFAULT or Base64.URL_SAFE)))
    return json.getInt("policyVersion")
}