package com.leafy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class LeafyApplication {

	public static void main(String[] args) {
		// .env 파일에서 환경 변수를 로드하여 시스템 속성으로 설정합니다.
		// 이를 통해 애플리케이션 전체에서 @Value 어노테이션이나 Environment 객체를 통해 환경 변수를 사용할 수 있습니다.
		Dotenv dotenv = Dotenv.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(LeafyApplication.class, args);
	}

}
