package demo.coin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CollectorAppication {
    public static void main(String[] args) {
        SpringApplication.run(CollectorAppication.class, args);
    }
}
