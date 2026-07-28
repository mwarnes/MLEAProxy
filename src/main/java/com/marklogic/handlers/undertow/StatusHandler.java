package com.marklogic.handlers.undertow;

import com.marklogic.service.StartupDisplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handler for /status endpoint - displays server configuration and endpoints.
 */
@Controller
public class StatusHandler {

    @Autowired
    private StartupDisplayService startupDisplayService;

    @GetMapping("/status")
    public String getStatus(Model model) {
        // Server info
        model.addAttribute("serverInfo", startupDisplayService.getServerInfo());
        
        // LDAP
        model.addAttribute("ldapInfo", startupDisplayService.getLDAPInfo());
        
        // OAuth
        model.addAttribute("oauthInfo", startupDisplayService.getOAuthInfo());
        
        // SAML
        model.addAttribute("samlInfo", startupDisplayService.getSAMLInfo());
        
        // Users
        model.addAttribute("users", startupDisplayService.getConfiguredUsers());
        
        return "status"; // renders templates/status.html
    }
}
