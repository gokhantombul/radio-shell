package com.radio;

import com.radio.command.RadioCommands;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.core.command.annotation.EnableCommand;

@SpringBootApplication
@EnableCommand(RadioCommands.class)
public class RadioShellApplication {
    public static void main(String[] args) {
        SpringApplication.run(RadioShellApplication.class, args);
    }
}

