package com.cocos.trading;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.cocos.trading", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final String CONTROLLER = "com.cocos.trading.controller..";

	private static final String SERVICE = "com.cocos.trading.service..";

	private static final String MODEL = "com.cocos.trading.model..";

	private static final String PERSISTENCE = "com.cocos.trading.persistence..";

	@ArchTest
	static final ArchRule calls_flow_from_controller_to_service_to_persistence = layeredArchitecture()
		.consideringOnlyDependenciesInLayers()
		.layer("Controller").definedBy(CONTROLLER)
		.layer("Service").definedBy(SERVICE)
		.layer("Model").definedBy(MODEL)
		.layer("Persistence").definedBy(PERSISTENCE)
		.whereLayer("Controller").mayNotBeAccessedByAnyLayer()
		.whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
		.whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
		.whereLayer("Model").mayOnlyBeAccessedByLayers("Controller", "Service", "Persistence");

	@ArchTest
	static final ArchRule the_model_is_plain_java = classes().that()
		.resideInAPackage(MODEL)
		.should()
		.onlyDependOnClassesThat()
		.resideInAnyPackage("java..", MODEL);

	@ArchTest
	static final ArchRule entities_do_not_leave_persistence = noClasses().that()
		.resideOutsideOfPackage(PERSISTENCE)
		.should()
		.dependOnClassesThat()
		.areAnnotatedWith(Entity.class);

	@ArchTest
	static final ArchRule only_services_open_transactions_on_classes = noClasses().that()
		.resideOutsideOfPackage(SERVICE)
		.should()
		.beAnnotatedWith(Transactional.class);

	@ArchTest
	static final ArchRule only_services_open_transactions_on_methods = noMethods().that()
		.areDeclaredInClassesThat()
		.resideOutsideOfPackage(SERVICE)
		.should()
		.beAnnotatedWith(Transactional.class);

	@ArchTest
	static final ArchRule request_and_response_dtos_stay_in_the_controller = noClasses().that()
		.resideInAnyPackage(SERVICE, MODEL)
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.cocos.trading.controller.dto..");

}
