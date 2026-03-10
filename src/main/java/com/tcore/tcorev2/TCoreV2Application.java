package com.tcore.tcorev2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // Import added

@SpringBootApplication
@EnableScheduling // Annotation added
public class TCoreV2Application {

    public static void main(String[] args) {
        SpringApplication.run(TCoreV2Application.class, args);
    }

}
