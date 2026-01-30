package com.magenta.security;

import com.magenta.config.Config.SecurityConfig;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityManagerTest {

    @Test
    void testSingleton() {
        SecurityManager instance1 = SecurityManager.getInstance();
        SecurityManager instance2 = SecurityManager.getInstance();

        assertSame(instance1, instance2, "SecurityManager should be a singleton");
    }

    @Test
    void testConfigUpdate() {
        SecurityManager manager = SecurityManager.getInstance();
        
        // Initial config should be empty (default) or whatever was set last
        // We set a new one to be sure
        SecurityConfig config1 = new SecurityConfig(
            List.of("tool1"), 
            Collections.emptyList(), 
            Collections.emptyList()
        );
        manager.setConfig(config1);
        
        assertEquals(config1, manager.getConfig());
        assertEquals(List.of("tool1"), manager.getConfig().approvalRequiredFor());

        // Update config
        SecurityConfig config2 = new SecurityConfig(
            List.of("tool2"), 
            Collections.emptyList(), 
            Collections.emptyList()
        );
        manager.setConfig(config2);
        
        assertEquals(config2, manager.getConfig());
        assertEquals(List.of("tool2"), manager.getConfig().approvalRequiredFor());
    }
}
