package dev.fluentmai.android.vpn.tunnel;

import android.util.Log;

import androidx.annotation.NonNull;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import dev.fluentmai.android.WahlapHookBridge;

public class HttpCapturerTunnel extends Tunnel {
    private static final String TAG = "HttpCapturerTunnel";

    public HttpCapturerTunnel(InetSocketAddress serverAddress, Selector selector) throws Exception {
        super(serverAddress, selector);
    }

    public HttpCapturerTunnel(SocketChannel innerChannel, Selector selector) throws Exception {
        super(innerChannel, selector);
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        onTunnelEstablished();
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) {
        int position = buffer.position();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(position);
        String body = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!body.contains("HTTP")) return;

        // Extract http target from http packet
        String[] lines = body.split("\r\n");
        String url = getUrl(lines);
        Log.d(TAG, "HTTP request inspected");

        // If it's a auth redirect request, catch it
        if (url.contains("tgk-wcaime.wahlap.com")) {
            Log.d(TAG, "Wahlap auth request captured by VPN: " + safeUrlSummary(url));
            WahlapHookBridge.onAuthRequestCaptured(url, body);
        }
    }

    @NonNull
    static String getUrl(String[] lines) {
        if (lines.length == 0) return "";
        String[] requestParts = lines[0].trim().split("\\s+");
        if (requestParts.length < 2) return "";
        String path = requestParts[1];
        if (path.regionMatches(true, 0, "http://", 0, "http://".length()) ||
                path.regionMatches(true, 0, "https://", 0, "https://".length())) {
            return path;
        }
        String host = "";
        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).startsWith("host")) {
                host = line.substring(4);
                while (host.startsWith(":") || host.startsWith(" ")) {
                    host = host.substring(1);
                }
                while (host.endsWith("\n") || host.endsWith("\r") || host.endsWith(" ")) {
                    host = host.substring(0, host.length() - 1);
                }
            }
        }
        if (!path.startsWith("/")) path = "/" + path;

        return "http://" + host + path;
    }

    @NonNull
    private static String safeUrlSummary(String url) {
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return "scheme=" + uri.getScheme() +
                    " host=" + uri.getHost() +
                    " path=" + path +
                    " hasCode=" + query.toLowerCase(Locale.ROOT).contains("code=") +
                    " hasState=" + query.toLowerCase(Locale.ROOT).contains("state=") +
                    " duplicatedHttpInPath=" + path.toLowerCase(Locale.ROOT).contains("http://");
        } catch (Exception ignored) {
            return "unparseable";
        }
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) {
    }

    @Override
    protected boolean isTunnelEstablished() {
        return true;
    }

    @Override
    protected void onDispose() {
        // TODO Auto-generated method stub
    }
}

