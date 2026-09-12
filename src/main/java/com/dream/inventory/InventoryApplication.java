package com.dream.inventory;

import com.dream.inventory.common.TimeZones;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class InventoryApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(TimeZones.ID));
		SpringApplication.run(InventoryApplication.class, args);
	}

}
