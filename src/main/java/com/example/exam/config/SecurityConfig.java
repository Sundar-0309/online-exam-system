package com.example.exam.config;

import com.example.exam.service.CustomUserDetailsService;
import com.example.exam.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;



@Configuration
@EnableWebSecurity
public class SecurityConfig {


    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests((authorize) ->
                        authorize
                                .requestMatchers("/js/**","/static/js/**", "/css/**", "/images/**", "/uploads/**").permitAll()
                                // Allow static resources and public pages
                                .requestMatchers("/register").hasRole("ADMIN")
                                .requestMatchers("/", "/login", "/h2-console/**").permitAll()
                                // Admin-only pages
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                // Student-only pages
                                .requestMatchers("/student/**", "/exam/**").hasRole("STUDENT")
                                // All other requests need authentication
                                .anyRequest().authenticated()
                )
                .formLogin(
                        form -> form
                                .loginPage("/login")
                                .loginProcessingUrl("/login")
                                .failureUrl("/login?error=true")
                                .permitAll()
                )
                .logout(
                        logout -> logout
                                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                                .permitAll()
                )
                .userDetailsService(userDetailsService)
                .addFilterBefore(new ActiveStudentFilter(userRepository), UsernamePasswordAuthenticationFilter.class)
                // This is needed for H2 console
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));



        return http.build();
    }
}
/***
 Subscribe Lazycoder - https://www.youtube.com/c/LazyCoderOnline?sub_confirmation=1
 whatsapp - https://wa.me/919572181024
 email - wapka1503@gmail.com
 ***/