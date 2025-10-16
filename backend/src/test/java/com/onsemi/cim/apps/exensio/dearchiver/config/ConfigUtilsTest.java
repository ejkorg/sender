package com.onsemi.cim.apps.exensio.dearchiver.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUtilsTest {

    @Test
    void getBooleanFlag_nullEnv_returnsDefault() {
        boolean v = ConfigUtils.getBooleanFlag(null, "a", "b", true);
        assertTrue(v);
        v = ConfigUtils.getBooleanFlag(null, "a", "b", false);
        assertFalse(v);
    }

    @Test
    void getBooleanFlag_primaryWins_overFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("primary.flag", "false");
        env.setProperty("fallback.flag", "true");

        boolean v = ConfigUtils.getBooleanFlag(env, "primary.flag", "fallback.flag", true);
        assertFalse(v, "primary property should take precedence over fallback");
    }

    @Test
    void getBooleanFlag_parsesTrueAndOne_andTrimsCaseInsensitive() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("p1", "  TRUE  ");
        env.setProperty("p2", "1");

        assertTrue(ConfigUtils.getBooleanFlag(env, "p1", null, false));
        assertTrue(ConfigUtils.getBooleanFlag(env, null, "p2", false));
    }

    @Test
    void getBooleanFlag_missingProperties_returnsDefault() {
        MockEnvironment env = new MockEnvironment();
        assertTrue(ConfigUtils.getBooleanFlag(env, "no.such", "neither", true));
        assertFalse(ConfigUtils.getBooleanFlag(env, "no.such", "neither", false));
    }

    @Test
    void getString_prefersPrimary_thenFallback_thenDefault() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("primary.val", "one");
        env.setProperty("fallback.val", "two");

        assertEquals("one", ConfigUtils.getString(env, "primary.val", "fallback.val", "def"));
        // remove primary
        env = new MockEnvironment();
        env.setProperty("fallback.val", "two");
        assertEquals("two", ConfigUtils.getString(env, "primary.val", "fallback.val", "def"));
        // neither present -> default
        env = new MockEnvironment();
        assertEquals("def", ConfigUtils.getString(env, "primary.val", "fallback.val", "def"));
    }
}
