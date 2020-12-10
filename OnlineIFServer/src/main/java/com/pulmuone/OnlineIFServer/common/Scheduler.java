package com.pulmuone.OnlineIFServer.common;

import java.util.Calendar;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pulmuone.OnlineIFServer.config.DBCleanConfig;

@Component
public class Scheduler {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	DBCleanConfig dbCleanConfig;

    @Scheduled(initialDelay = 6000,fixedDelay = 60000)	//fixedDelay: 86400000 = 1day (milli-seconds)
    public void fixedDelayJob() {
        //logger.debug("> fixedDelayJob");

        try {
        	dbCleanConfig.clearTables();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
        
        //logger.debug("< fixedDelayJob");
    }

    /**
    @Scheduled(cron = "0,30 * * * * *")
    public void cronJob() {
        logger.info("> cronJob");

        // Add scheduled logic here
        logger.info("There are {} greetings in the data store.");

        logger.info("< cronJob");
    }

    @Scheduled(initialDelay = 5000,fixedRate = 15000)
    public void fixedRateJobWithInitialDelay() {
        logger.info("> fixedRateJobWithInitialDelay");

        // Add scheduled logic here
        // Simulate job processing time
        long pause = 5000;
        long start = System.currentTimeMillis();
        do {
            if (start + pause < System.currentTimeMillis()) {
                break;
            }
        } while (true);
        logger.info("Processing time was {} seconds.", pause / 1000);

        logger.info("< fixedRateJobWithInitialDelay");
    }

    @Scheduled(initialDelay = 5000,fixedDelay = 15000)
    @Async
    public void fixedDelayJobWithInitialDelay() {
        logger.info("> fixedDelayJobWithInitialDelay");

        // Add scheduled logic here
        // Simulate job processing time
        long pause = 5000;
        long start = System.currentTimeMillis();
        do {
            if (start + pause < System.currentTimeMillis()) {
                break;
            }
        } while (true);
        logger.info("Processing time was {} seconds.", pause / 1000);

        logger.info("< fixedDelayJobWithInitialDelay");
    }
    **/
}
