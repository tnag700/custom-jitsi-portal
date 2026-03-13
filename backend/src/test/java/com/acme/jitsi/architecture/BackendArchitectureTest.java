package com.acme.jitsi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class BackendArchitectureTest {

  private static final JavaClasses importedClasses = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
      .importPath(Paths.get("build", "classes", "java", "main"));

  static final ArchRule api_does_not_depend_on_infrastructure = classes()
      .that().resideInAnyPackage("..domains..api..")
      .should().onlyDependOnClassesThat()
      .resideOutsideOfPackage("..domains..infrastructure..");

  static final ArchRule usecases_do_not_depend_on_api_or_infrastructure = classes()
      .that().resideInAnyPackage("..domains..usecase..", "..domains..application..")
      .should().onlyDependOnClassesThat()
      .resideOutsideOfPackages("..domains..api..", "..domains..infrastructure..");

  static final ArchRule domain_services_do_not_depend_on_api_or_infrastructure = classes()
      .that().resideInAnyPackage("..domains..service..")
      .should().onlyDependOnClassesThat()
      .resideOutsideOfPackages("..domains..api..", "..domains..infrastructure..");

  static final ArchRule infrastructure_does_not_depend_on_api = classes()
      .that().resideInAnyPackage("..domains..infrastructure..")
      .should().onlyDependOnClassesThat()
      .resideOutsideOfPackage("..domains..api..");

  static final ArchRule controllers_live_in_api_packages = classes()
      .that().haveSimpleNameEndingWith("Controller")
      .and().resideInAPackage("..domains..")
      .should().resideInAPackage("..domains..api..");

  static final ArchRule usecases_live_in_usecase_or_application_packages = classes()
      .that().haveSimpleNameEndingWith("UseCase")
      .and().resideInAPackage("..domains..")
      .should().resideInAnyPackage("..domains..usecase..", "..domains..application..");

  static final ArchRule service_ports_are_interfaces = classes()
      .that().haveSimpleNameEndingWith("Port")
      .and().resideInAPackage("..domains..service..")
      .should().beInterfaces();

  static final ArchRule adapters_live_in_infrastructure_packages = classes()
      .that().haveSimpleNameEndingWith("Adapter")
      .and().resideInAPackage("..domains..")
      .should().resideInAPackage("..domains..infrastructure..");

  static final ArchRule meetings_outside_infrastructure_do_not_depend_on_rooms = noClasses()
      .that().resideInAPackage("..domains.meetings..")
      .and().resideOutsideOfPackage("..domains.meetings.infrastructure..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("..domains.rooms..");

  static final ArchRule rooms_do_not_depend_on_meetings = noClasses()
      .that().resideInAPackage("..domains.rooms..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("..domains.meetings..");

  static final ArchRule invites_outside_infrastructure_do_not_depend_on_meetings = noClasses()
      .that().resideInAPackage("..domains.invites..")
      .and().resideOutsideOfPackage("..domains.invites.infrastructure..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("..domains.meetings..");

  static final ArchRule auth_outside_infrastructure_do_not_depend_on_meetings = noClasses()
      .that().resideInAPackage("..domains.auth..")
      .and().resideOutsideOfPackage("..domains.auth.infrastructure..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("..domains.meetings..");

  static final ArchRule meetings_outside_infrastructure_do_not_depend_on_profiles = noClasses()
      .that().resideInAPackage("..domains.meetings..")
      .and().resideOutsideOfPackage("..domains.meetings.infrastructure..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("..domains.profiles..");

  static final ArchRule configsets_do_not_depend_on_rooms = noClasses()
      .that().resideInAPackage("..domains.configsets..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("..domains.rooms..");

  static final ArchRule domains_are_free_of_cycles = slices()
      .matching("com.acme.jitsi.domains.(*)..")
      .should().beFreeOfCycles()
      .because("доменные срезы должны оставаться направленными и без циклических связей");

    @Test
    void apiDoesNotDependOnInfrastructure() {
        api_does_not_depend_on_infrastructure.check(importedClasses);
    }

    @Test
    void useCasesDoNotDependOnApiOrInfrastructure() {
        usecases_do_not_depend_on_api_or_infrastructure.check(importedClasses);
    }

    @Test
    void domainServicesDoNotDependOnApiOrInfrastructure() {
        domain_services_do_not_depend_on_api_or_infrastructure.check(importedClasses);
    }

    @Test
    void infrastructureDoesNotDependOnApi() {
        infrastructure_does_not_depend_on_api.check(importedClasses);
    }

    @Test
    void controllersLiveInApiPackages() {
        controllers_live_in_api_packages.check(importedClasses);
    }

    @Test
    void useCasesLiveInUsecaseOrApplicationPackages() {
        usecases_live_in_usecase_or_application_packages.check(importedClasses);
    }

    @Test
    void servicePortsAreInterfaces() {
        service_ports_are_interfaces.check(importedClasses);
    }

    @Test
    void adaptersLiveInInfrastructurePackages() {
        adapters_live_in_infrastructure_packages.check(importedClasses);
    }

    @Test
    void meetingsOutsideInfrastructureDoNotDependOnRooms() {
        meetings_outside_infrastructure_do_not_depend_on_rooms.check(importedClasses);
    }

    @Test
    void roomsDoNotDependOnMeetings() {
        rooms_do_not_depend_on_meetings.check(importedClasses);
    }

    @Test
    void invitesOutsideInfrastructureDoNotDependOnMeetings() {
        invites_outside_infrastructure_do_not_depend_on_meetings.check(importedClasses);
    }

    @Test
    void authOutsideInfrastructureDoNotDependOnMeetings() {
        auth_outside_infrastructure_do_not_depend_on_meetings.check(importedClasses);
    }

    @Test
    void meetingsOutsideInfrastructureDoNotDependOnProfiles() {
        meetings_outside_infrastructure_do_not_depend_on_profiles.check(importedClasses);
    }

    @Test
    void configsetsDoNotDependOnRooms() {
        configsets_do_not_depend_on_rooms.check(importedClasses);
    }

    @Test
    void domainsAreFreeOfCycles() {
        domains_are_free_of_cycles.check(importedClasses);
    }
}