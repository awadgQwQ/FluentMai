package dev.fluentmai.android.vpn.tunnel;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HttpCapturerTunnelTest {
    @Test
    public void getUrlPreservesAbsoluteFormRequestTarget() {
        String[] lines = new String[] {
                "GET http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=abc&state=def HTTP/1.1",
                "Host: tgk-wcaime.wahlap.com",
        };

        assertEquals(
                "http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=abc&state=def",
                HttpCapturerTunnel.getUrl(lines)
        );
    }

    @Test
    public void getUrlBuildsOriginFormRequestTargetWithHost() {
        String[] lines = new String[] {
                "GET /wc_auth/oauth/callback/maimai-dx?code=abc&state=def HTTP/1.1",
                "Host: tgk-wcaime.wahlap.com",
        };

        assertEquals(
                "http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=abc&state=def",
                HttpCapturerTunnel.getUrl(lines)
        );
    }
}
