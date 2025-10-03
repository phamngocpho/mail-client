package utils;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.cert.X509Certificate;

public class NetworkUtils {

    /**
     * Tạo SSL Socket với trust tất cả certificates (cho testing)
     */
    public static SSLSocket createSSLSocket(String host, int port) throws IOException {
        try {
            // Tạo TrustManager chấp nhận tất cả certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            SSLSocketFactory factory = sslContext.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
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
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

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