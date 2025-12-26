package com.liuqitech.codeagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAgentApplication.class, args);
    }

}

