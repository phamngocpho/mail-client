package utils;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;

/**
 * The NetworkUtils class provides utilities for working with network operations.
 * This includes creating SSL sockets, upgrading plain sockets to TLS,
 * and other network-related utilities such as Base64 encoding and decoding.
 */
public class NetworkUtils {

    /**
     * Tạo SSLContext với trust tất cả certificates (cho testing)
     */
    private static SSLContext createTrustAllSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    /**
     * Tạo SSL Socket với trust tất cả certificates (cho testing)
     */
    public static SSLSocket createSSLSocket(String host, int port, String localIP, int localPort) throws IOException {
        try {
            SSLContext sslContext = createTrustAllSSLContext();
            SSLSocketFactory factory = sslContext.getSocketFactory();

            SSLSocket socket;

            // Nếu localIP là null -> tự động
            if (localIP == null || localIP.isEmpty()) {
                socket = (SSLSocket) factory.createSocket(host, port);
            } else {
                // Thủ công bind IP
                socket = (SSLSocket) factory.createSocket();
                InetSocketAddress localAddress = new InetSocketAddress(localIP, localPort);
                socket.bind(localAddress);
                InetSocketAddress remoteAddress = new InetSocketAddress(host, port);
                socket.connect(remoteAddress);
            }

            socket.setSoTimeout(Constants.SOCKET_TIMEOUT);
            socket.startHandshake();

            return socket;
        } catch (Exception e) {
            throw new IOException("Failed to create SSL socket: " + e.getMessage(), e);
        }
    }

    /**
     * Upgrade plain socket sang TLS (cho STARTTLS)
     */
    public static SSLSocket upgradeToTLS(Socket plainSocket, String host) throws IOException {
        try {
            SSLContext sslContext = createTrustAllSSLContext();
            SSLSocketFactory factory = sslContext.getSocketFactory();

            SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    plainSocket, host, plainSocket.getPort(), true
            );
            sslSocket.setUseClientMode(true);
            sslSocket.startHandshake();

            return sslSocket;
        } catch (Exception e) {
            throw new IOException("Failed to upgrade to TLS: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo BufferedReader từ socket
     */
    public static BufferedReader createReader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Tạo PrintWriter từ socket
     */
    public static PrintWriter createWriter(Socket socket) throws IOException {
        return new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Encode Base64 cho SMTP AUTH
     */
    public static String base64Encode(String text) {
        return java.util.Base64.getEncoder().encodeToString(text.getBytes());
    }

    /**
     * Decode Base64
     */
    public static String base64Decode(String encoded) {
        return new String(java.util.Base64.getDecoder().decode(encoded));
    }
}