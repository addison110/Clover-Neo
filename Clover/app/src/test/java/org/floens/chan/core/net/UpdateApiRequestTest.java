package org.floens.chan.core.net;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UpdateApiRequestTest {
    @Test
    public void versionCodeFromTag() {
        assertEquals(30100, UpdateApiRequest.versionCodeFromTag("v3.1.0"));
        assertEquals(30002, UpdateApiRequest.versionCodeFromTag("3.0.2"));
        assertEquals(41200, UpdateApiRequest.versionCodeFromTag("v4.12"));
        assertEquals(30101, UpdateApiRequest.versionCodeFromTag("v3.1.1-beta"));
    }

    @Test
    public void versionCodeFromTagWithoutVersion() {
        assertEquals(0, UpdateApiRequest.versionCodeFromTag("latest"));
        assertEquals(0, UpdateApiRequest.versionCodeFromTag(""));
    }
}
