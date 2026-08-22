package com.autoparts.inventory.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AwsProperties {
    private String s3Bucket;
    private String s3Region;
    private String accessKey;
    private String secretKey;
    private String cloudfrontUrl;
}
