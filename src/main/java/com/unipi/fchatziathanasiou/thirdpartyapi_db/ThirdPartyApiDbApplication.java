package com.unipi.fchatziathanasiou.thirdpartyapi_db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ThirdPartyApiDbApplication {

	public static void main(String[] args) {
		SpringApplication.run(ThirdPartyApiDbApplication.class, args);
	}

}
