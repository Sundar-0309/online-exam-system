package com.example.exam;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class OnlineExamApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnlineExamApplication.class, args);
	}


	//  will run once when the application starts
	@Bean
	@Profile("!prod")
	CommandLineRunner createLocalAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			String adminUsername = "admin";

			// Check if the admin user already exists
			if (userRepository.findByUsername(adminUsername).isEmpty()) {
				User adminUser = new User();
				adminUser.setUsername(adminUsername);
				// Make sure to use the password encoder
				adminUser.setPassword(passwordEncoder.encode("adminpass"));
				adminUser.setRole("ROLE_ADMIN");
				adminUser.setFullName("Admin");

				userRepository.save(adminUser);
			}
		};
	}

	@Bean
	@Profile("prod")
	CommandLineRunner createProductionAdminUser(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${ADMIN_USERNAME}") String adminUsername,
			@Value("${ADMIN_PASSWORD}") String adminPassword) {
		return args -> {
			if (userRepository.findByUsername(adminUsername).isEmpty()) {
				User adminUser = new User();
				adminUser.setUsername(adminUsername);
				adminUser.setPassword(passwordEncoder.encode(adminPassword));
				adminUser.setRole("ROLE_ADMIN");
				adminUser.setFullName("Admin");
				userRepository.save(adminUser);
			}
		};
	}
}
