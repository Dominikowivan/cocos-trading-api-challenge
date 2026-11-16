package com.cocos.trading.controller;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport.Level;

@TestConfiguration(proxyBeanMethods = false)
class ContractValidationConfiguration {

	private static final Path CONTRACT = Path.of("docs/api/openapi.yaml");

	@Bean
	MockMvcBuilderCustomizer everyResponseMatchesTheContract() {
		OpenApiInteractionValidator validator = validatorFor(CONTRACT);
		return builder -> builder.alwaysExpect(openApi().isValid(validator));
	}

	private static OpenApiInteractionValidator validatorFor(Path contract) {
		return OpenApiInteractionValidator.createForInlineApiSpecification(read(contract))
			.withLevelResolver(responsesAndRoutesOnly())
			.build();
	}

	private static LevelResolver responsesAndRoutesOnly() {
		return LevelResolver.create()
			.withLevel("validation.request", Level.IGNORE)
			.withLevel("validation.request.path.missing", Level.ERROR)
			.withLevel("validation.request.operation.notAllowed", Level.ERROR)
			.build();
	}

	private static String read(Path contract) {
		try {
			return Files.readString(contract);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
