package com.antidpi.android

import com.antidpi.android.xray.VlessToXrayConverter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessToXrayConverterTest {

    @Test
    fun `converts vless reality link into xray json with dialerProxy`() {
        val sample = "vless://11111111-2222-3333-4444-555555555555@203.0.113.10:443" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.example.com" +
            "&fp=chrome&pbk=TEST_PUBLIC_KEY&sid=abcd1234&type=tcp#RU-Mobile-Node"

        val jsonStr = VlessToXrayConverter.convertToXrayJson(sample)
        val root = JSONObject(jsonStr)
        val outbounds = root.getJSONArray("outbounds")

        val proxy = outbounds.getJSONObject(0)
        assertEquals("vless", proxy.getString("protocol"))
        val sockopt = proxy.getJSONObject("streamSettings").getJSONObject("sockopt")
        assertEquals("antidpi-out", sockopt.getString("dialerProxy"))

        val reality = proxy.getJSONObject("streamSettings").getJSONObject("realitySettings")
        assertEquals("www.example.com", reality.getString("serverName"))
        assertEquals("TEST_PUBLIC_KEY", reality.getString("publicKey"))

        val antidpi = outbounds.getJSONObject(1)
        assertEquals("antidpi-out", antidpi.getString("tag"))
        assertEquals("socks", antidpi.getString("protocol"))
        val server = antidpi.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("127.0.0.1", server.getString("address"))
        assertEquals(1080, server.getInt("port"))
        assertTrue(root.getString("remarks").contains("RU-Mobile-Node"))
    }
}
