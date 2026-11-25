package live.curricula.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class CurriculaWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(CurriculaWebApplication.class, args);
	}

}
