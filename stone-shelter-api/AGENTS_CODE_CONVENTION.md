# Backend conventions

## Human-only directory: engineering-log

The repository's entire `engineering-log/` directory is forbidden to every agent
without exception: no reading, listing, indexing or modification through any tool,
attachment, Git history, alias, symlink, copy or delegation. Exclude it and all
descendants from searches and automation. Only a human working manually may
access or change it; no request or task authorizes an exception. Never weaken,
remove or bypass this rule. Follow the full policy in the root AGENTS.md.

Read the root AGENTS.md, constitution, current feature spec, tasks and ADR first.
Use the pinned ADR-0001 versions, including JDK 23.0.2 (Java release 23).
Do not change versions silently.

- Packages: lab.stoneshelter.controllers, handlers, services, enums, repositories, entities, criteria, exceptions, shared, mappers.entities, mappers.dtos.
- Controllers live in controllers, services in services, repositories in repositories and entities in entities. Criteria live in criteria; exceptions in exceptions; HTTP exception handlers in handlers.
- Controllers call services; only services call repositories. Entity classes live in entities.
- Controllers pass original Request DTOs to services and receive ready Response DTOs. Keep HTTP binding, @Valid, statuses and headers in controllers; move normalization, search defaults and mapper calls to services. Controllers must not contain business logic, instantiate domain models or persistence entities, or perform request/response mapping.
- Shared request/response DTOs and their nested types live in lab.stoneshelter.shared alongside services, outside controllers and handlers. Services and controllers depend on shared DTOs; domain models do not. Services must not depend on controllers or handlers. Repositories work with entities and domain criteria, never shared DTOs.
- Return Response DTOs directly from controllers. Use ResponseEntity only when
  explicitly requested in the task. Creation methods use
  @ResponseStatus(HttpStatus.CREATED). Add Location only when explicitly requested.
- Name mappers {FullSourceTypeName}To{FullTargetTypeName}Mapper, for example StoneCreateRequestToStoneEntityMapper and StoneEntityToStoneCreateResponseMapper.
- Repositories work with entities and queries; services perform changes and normalization and invoke mappers. Entities never reach controllers.
- Build JPA specifications, Criteria predicates and database ordering in repositories, not services.
- Generated OpenAPI is the source of truth for HTTP contracts. Generate it from Spring MVC controllers and shared DTOs; do not maintain a separate handwritten OpenAPI contract. Errors use RFC 9457 ProblemDetail.
- Liquibase owns schema changes. Never edit a committed changeset.
- Database configuration comes from environment variables; ddl-auto stays validate.
- Database tests use Testcontainers with PostgreSQL 18.6; never H2.
- Keep architecture rules in ArchUnit tests; do not add business behavior without a spec.
- Run ./mvnw verify when the assigned task includes backend build verification.
- Tests must verify application behavior or a unique technical guarantee.
- Do not add assertions that merely verify framework internals or repeat guarantees already provided by application context startup.
- Prefer framework-native testing facilities over custom infrastructure code.
- Every assertion should catch a meaningful failure that would otherwise remain undetected.
- Each test must explicitly create all data it requires.
- Each test must clean up all data it creates.
- Tests must not depend on data created by other tests.
- Tests must not depend on test execution order.
- Shared database infrastructure is allowed, but shared mutable test data is not.
- After implementation, verify that all created or modified source files are located under the correct module source roots and are included in the build.

- Put entity-target mappers in lab.stoneshelter.mappers.entities and DTO-target mappers in lab.stoneshelter.mappers.dtos. Keep full source-to-target mapper names. Both mapper packages may access entities; controllers must not depend on mappers.

- Use records only for types with at most four fields. Types with more than four
  fields must be classes with private fields, a no-argument constructor and public
  getters/setters. Mappers create class targets with the no-argument constructor
  and populate them using setters, reading source classes through getters.

- Entity-target mappers inherit null-source handling from AbstractEntityMapper
  (return null). DTO-target mappers inherit source validation from AbstractDtoMapper
  (throw MapperValidationException for null, translated to HTTP 500 ProblemDetail).
  Missing-resource 404 errors remain in services; mapper checks are technical only.

- Services obtain ready Response DTOs through mappers; do not construct or
  populate responses directly in services. This includes collection/pagination
  wrappers. Exception: simple scalar values such as an id do not require a mapper;
  delete(long id) may construct an identifier-only response directly. Mapping
  entities, structured request objects and collections still requires mappers.
  Assemble other response fields and map collection items inside mappers. Class targets use no-argument constructors
  and setters; record targets with at most four fields may use constructors
  inside mappers.

- Package types by responsibility: controllers, handlers, services, enums,
  repositories, entities, criteria and exceptions under lab.stoneshelter.
  Keep shared DTOs in shared and mappers in mappers.entities / mappers.dtos.

- Avoid temporary variables that only hold a value for a single immediate call
  and require no further processing. Pass the expression directly to that call,
  for example `return searchResponseMapper.toDto(repository.search(stoneSearchCriteria));`.
  Keep local variables when needed for mutation, reuse or intermediate computation.
  This rule concerns local bindings; assigning a method result to a variable does
  not itself create an additional object.

- Object transformation methods in application and mapper APIs must return the
  resulting object, including protected mapping hooks. Callers must use that result
  rather than ignore it and rely only on mutation of an input reference. Forward
  single-use results directly to the next method. Conventional field setters keep
  their existing setter contract; framework command APIs are not redefined.

- Service method names should match the corresponding repository method names
  where practical, or contain a verb that clearly describes the method's function.
  Name identifier lookup helpers findById rather than vague names such as require.
  A service findById helper may translate an empty repository Optional into the
  specified not-found exception; matching names does not require matching return types.

- Name DTO conversion methods toDto. Entity conversion methods are toEntity(source)
  for creation and toEntity(entity, source) for updates; preserve entity-first order
  in the update overload and use the returned result.
- Name local object variables descriptively after their type in lowerCamelCase,
  for example StoneSearchCriteria -> stoneSearchCriteria. Do not introduce a
  single-use forwarding variable solely to satisfy this naming convention.
