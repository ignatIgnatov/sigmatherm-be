package com.ludogoriesoft.sigmatherm.controller;

import com.ludogoriesoft.sigmatherm.service.SkroutzFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/skroutz")
public class SkroutzFeedController {

    private final SkroutzFeedService feedService;

    @GetMapping("/feed.xml")
    public Resource serveFeed() {
        return new FileSystemResource(feedService.getFeedFile());
    }
}
