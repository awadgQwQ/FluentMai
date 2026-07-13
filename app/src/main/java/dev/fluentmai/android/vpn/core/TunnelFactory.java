package dev.fluentmai.android.vpn.core;

import android.util.Log;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import dev.fluentmai.android.WahlapHookHttpService;
import dev.fluentmai.android.vpn.tcpip.CommonMethods;
import dev.fluentmai.android.vpn.tunnel.HttpCapturerTunnel;
import dev.fluentmai.android.vpn.tunnel.RawTunnel;
import dev.fluentmai.android.vpn.tunnel.Tunnel;

public class TunnelFactory {
    private final static String TAG = "TunnelFactory";

    public static Tunnel wrap(SocketChannel channel, Selector selector) throws Exception {
        return new RawTunnel(channel, selector);
    }

    public static Tunnel createTunnelByConfig(InetSocketAddress destAddress, Selector selector) throws Exception {
        Log.d(TAG, destAddress.getHostString() + ":" + destAddress.getPort());
        String mappedHost = null;
        if (destAddress.getAddress() != null)
        {
            Log.d(TAG, destAddress.getAddress().toString());
            mappedHost = DnsProxy.reverseLookup(
                    CommonMethods.ipStringToInt(destAddress.getAddress().getHostAddress()));
        }
        String hostString = destAddress.getHostString();
        boolean isWahlapHost = (hostString != null && hostString.endsWith("wahlap.com")) ||
                (mappedHost != null && mappedHost.endsWith("wahlap.com"));
        if (isWahlapHost && destAddress.getPort() == 80) {
                Log.d(TAG, "Request for wahlap.com caught, routing to local capture page");
                return new HttpCapturerTunnel(
                        new InetSocketAddress("127.0.0.1", WahlapHookHttpService.REDIRECT_PORT),
                        selector);
        } else if (destAddress.isUnresolved()) {
            return new RawTunnel(new InetSocketAddress(destAddress.getHostName(), destAddress.getPort()), selector);
        } else {
            return new RawTunnel(destAddress, selector);
        }
    }
}

