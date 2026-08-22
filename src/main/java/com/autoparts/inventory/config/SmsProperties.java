package com.autoparts.inventory.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SmsProperties {
    private String provider;
    private String authKey;
    private String senderId;
}
