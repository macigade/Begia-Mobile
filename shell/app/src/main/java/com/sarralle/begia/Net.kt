package com.sarralle.begia

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * One way to open a connection to a BEGIA, whether it is this phone's
 * recorder on the loopback or a laptop's over the plant WiFi. A laptop
 * serves HTTPS with its own certificate authority, which this phone has
 * no way to trust - so, as the installer already does for a payload, any
 * certificate is accepted on those connections. The plant network is the
 * trust boundary here, not the certificate.
 */
object Net {
    fun connect(url: String, connectMs: Int, readMs: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        if (c is HttpsURLConnection) {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, trustAll, SecureRandom())
            c.sslSocketFactory = ssl.socketFactory
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        return c
    }
}
