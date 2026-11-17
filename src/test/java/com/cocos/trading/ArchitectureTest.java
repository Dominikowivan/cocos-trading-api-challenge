package com.cocos.trading;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import com.cocos.trading.persistence.OrderIdempotencyRepository;
import com.cocos.trading.persistence.UserRepository;

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

	private static final String USER_WRITE = "com.cocos.trading.service.userwrite..";

	private static final String USER_LOCK = "com.cocos.trading.service.userwrite.UserLock";

	private static final String IDEMPOTENT_WRITE = "com.cocos.trading.service.userwrite.IdempotentWrite";

	@ArchTest
	static final ArchRule calls_flow_from_controller_to_service_to_persistence = layeredArchitecture()
		.consideringOnlyDependenciesInLayers()
		.layer("Controller")
		.definedBy(CONTROLLER)
		.layer("Service")
		.definedBy(SERVICE)
		.layer("Model")
		.definedBy(MODEL)
		.layer("Persistence")
		.definedBy(PERSISTENCE)
		.whereLayer("Controller")
		.mayNotBeAccessedByAnyLayer()
		.whereLayer("Service")
		.mayOnlyBeAccessedByLayers("Controller")
		.whereLayer("Persistence")
		.mayOnlyBeAccessedByLayers("Service")
		.whereLayer("Model")
		.mayOnlyBeAccessedByLayers("Controller", "Service", "Persistence");

	@ArchTest
	static final ArchRule the_model_is_plain_java = classes().that()
		.resideInAPackage(MODEL)
		.should()
		.onlyDependOnClassesThat()
		.resideInAnyPackage("java..", MODEL);

	@ArchTest
	static final ArchRule the_model_names_no_framework_type = noClasses().that()
		.resideInAPackage(MODEL)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..");

	@ArchTest
	static final ArchRule the_model_reads_no_clock = noClasses().that()
		.resideInAPackage(MODEL)
		.should()
		.dependOnClassesThat()
		.haveFullyQualifiedName("java.time.Clock");

	@ArchTest
	static final ArchRule each_service_lives_in_its_use_case_package = classes().that()
		.areAnnotatedWith(Service.class)
		.should()
		.resideInAPackage("com.cocos.trading.service.*");

	@ArchTest
	static final ArchRule nothing_sits_at_the_root_of_the_service_package = noClasses().should()
		.resideInAPackage("com.cocos.trading.service");

	@ArchTest
	static final ArchRule use_cases_do_not_depend_on_each_other = slices().matching("com.cocos.trading.service.(*)..")
		.should()
		.notDependOnEachOther()
		.ignoreDependency(alwaysTrue(), resideInAPackage(USER_WRITE));

	@ArchTest
	static final ArchRule the_shared_write_protection_depends_on_no_use_case = noClasses().that()
		.resideInAPackage(USER_WRITE)
		.should()
		.dependOnClassesThat(resideInAPackage(SERVICE).and(not(resideInAPackage(USER_WRITE))));

	@ArchTest
	static final ArchRule the_shared_write_protection_is_not_a_use_case = noClasses().that()
		.resideInAPackage(USER_WRITE)
		.should()
		.beAnnotatedWith(Service.class)
		.orShould()
		.beAnnotatedWith(RestController.class);

	@ArchTest
	static final ArchRule only_the_user_lock_locks_a_user = noClasses().that()
		.doNotHaveFullyQualifiedName(USER_LOCK)
		.should()
		.callMethod(UserRepository.class, "lockById", Integer.class);

	@ArchTest
	static final ArchRule only_the_idempotent_write_claims_a_key = noClasses().that()
		.doNotHaveFullyQualifiedName(IDEMPOTENT_WRITE)
		.should()
		.callMethod(OrderIdempotencyRepository.class, "claim", Integer.class, String.class, String.class,
				LocalDateTime.class);

	@ArchTest
	static final ArchRule a_claim_is_inserted_only_through_the_claim = noClasses().that()
		.doNotHaveFullyQualifiedName(OrderIdempotencyRepository.class.getName())
		.should()
		.callMethod(OrderIdempotencyRepository.class, "insertClaim", Integer.class, String.class, String.class,
				LocalDateTime.class);

	@ArchTest
	static final ArchRule the_user_lock_knows_nothing_of_idempotency = noClasses().that()
		.haveFullyQualifiedName(USER_LOCK)
		.should()
		.dependOnClassesThat()
		.haveFullyQualifiedName(IDEMPOTENT_WRITE);

	@ArchTest
	static final ArchRule idempotency_knows_nothing_of_the_user_lock = noClasses().that()
		.haveFullyQualifiedName(IDEMPOTENT_WRITE)
		.should()
		.dependOnClassesThat()
		.haveFullyQualifiedName(USER_LOCK);

	@ArchTest
	static final ArchRule entities_do_not_leave_persistence = noClasses().that()
		.resideOutsideOfPackage(PERSISTENCE)
		.should()
		.dependOnClassesThat()
		.areAnnotatedWith(Entity.class);

	@ArchTest
	static final ArchRule entity_types_stay_in_persistence = noClasses().that()
		.resideOutsideOfPackage(PERSISTENCE)
		.should()
		.dependOnClassesThat()
		.resideInAPackage("com.cocos.trading.persistence.entity..");

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
