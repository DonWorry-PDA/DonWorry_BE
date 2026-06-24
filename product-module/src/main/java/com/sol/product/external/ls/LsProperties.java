package com.sol.product.external.ls;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ls.api")
public class LsProperties {
    private String baseUrl;
    private String wsUrl;
    private String appKey;
    private String appSecret;
}
