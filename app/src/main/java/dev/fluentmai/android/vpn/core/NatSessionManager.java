package dev.fluentmai.android.vpn.core;

import android.util.Log;
import android.util.SparseArray;

import dev.fluentmai.android.vpn.tcpip.CommonMethods;

public class NatSessionManager {

    static final int MAX_SESSION_COUNT = 4096;
    static final long SESSION_TIMEOUT_NS = 120 * 1000000000L;
    static final SparseArray<NatSession> Sessions = new SparseArray<>();

    public static NatSession getSession(int portKey) {
        return Sessions.get(portKey);
    }

    public static int getSessionCount() {
        return Sessions.size();
    }

    static void clearExpiredSessions() {
        long now = System.nanoTime();
        for (int i = Sessions.size() - 1; i >= 0; i--) {
            NatSession session = Sessions.valueAt(i);
            if (now - session.LastNanoTime > SESSION_TIMEOUT_NS) {
                Sessions.removeAt(i);
            }
        }
    }

    public static void clearAllSessions() {
        Sessions.clear();
    }

    public static NatSession createSession(int portKey, int remoteIP, short remotePort) {
        if (Sessions.size() > MAX_SESSION_COUNT) {
            clearExpiredSessions();
        }

        NatSession session = new NatSession();
        session.LastNanoTime = System.nanoTime();
        session.RemoteIP = remoteIP;
        session.RemotePort = remotePort;

        String mappedHost = DnsProxy.reverseLookup(remoteIP);
        session.RemoteHost = mappedHost != null ? mappedHost : CommonMethods.ipIntToString(remoteIP);
        if ((remotePort & 0xFFFF) == 80 || mappedHost != null) {
            Log.i(Constant.TAG, "TCP session " + session.RemoteHost + ":" + (remotePort & 0xFFFF));
        }

        Sessions.put(portKey, session);
        return session;
    }
}

