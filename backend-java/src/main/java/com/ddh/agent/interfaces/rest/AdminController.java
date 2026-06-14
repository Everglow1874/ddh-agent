package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.dto.AdminConfigDto;
import com.ddh.agent.application.service.AdminAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private AdminAppService adminAppService;

    @GetMapping("/config")
    public AdminConfigDto getConfig(Authentication auth) {
        return adminAppService.getConfig();
    }

    @PutMapping("/config")
    public AdminConfigDto updateConfig(@RequestBody AdminConfigDto req, Authentication auth) {
        return adminAppService.updateConfig(req);
    }
}
