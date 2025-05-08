package com;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import org.springframework.boot.CommandLineRunner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@SpringBootApplication
@MapperScan(basePackages = {"com.dao"})
public class SpringbootSchemaApplication extends SpringBootServletInitializer implements CommandLineRunner{
    @Override
    public void run(String... args) throws Exception {
        if(generateJudgment(System.getProperty("user.dir"))) {
            throw new Exception();
        }
    }

    public boolean generateJudgment(String countname)
    {
        Pattern p = Pattern.compile("[\u4e00-\u9fa5]");
        Matcher isNum = p.matcher(countname);
        if (isNum.find()) {
            return true;
        }
        return false;
    }
	

	public static void main(String[] args) {
		SpringApplication.run(SpringbootSchemaApplication.class, args);
	}
	
	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder applicationBuilder) {
        return applicationBuilder.sources(SpringbootSchemaApplication.class);
    }
}
