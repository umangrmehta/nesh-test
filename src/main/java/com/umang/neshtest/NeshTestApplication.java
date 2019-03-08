package com.umang.neshtest;

import com.umang.neshtest.corenlp.NLPPipeline;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NeshTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(NeshTestApplication.class, args);
		NLPPipeline.getSummaryAndSentiment("This is a Dummy Statement");
	}

}
