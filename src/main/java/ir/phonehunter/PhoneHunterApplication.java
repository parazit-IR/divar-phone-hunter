package ir.phonehunter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PhoneHunterApplication {

    public static void main(String[] args) {
        new File("data").mkdirs(); // SQLite file lives in ./data
        SpringApplication.run(PhoneHunterApplication.class, args);
    }
}
