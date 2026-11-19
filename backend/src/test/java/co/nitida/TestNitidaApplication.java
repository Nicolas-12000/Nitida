package co.nitida;

import org.springframework.boot.SpringApplication;

public class TestNitidaApplication {

	public static void main(String[] args) {
		SpringApplication.from(NitidaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
