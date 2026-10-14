# Multi-Tenant OAuth2 Spring Boot Starter

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![CI](https://github.com/XENO-OS/multi-tenant-oauth2-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/XENO-OS/multi-tenant-oauth2-spring-boot-starter/actions/workflows/ci.yml)

Multi-tenant JWT validation for Spring Security resource servers. A single application can accept
tokens from several issuers (for example several Keycloak realms); the issuer of each incoming token
selects the tenant, and the token is validated against that tenant's configured issuer.

## Features

* Multiple JWT issuers / realms in one application, configured through `application.yml`
* Tenant resolved automatically from the `iss` claim of the bearer token
* Per-tenant `AuthenticationManager` (JWT decoder built on first use from the issuer and cached)
* Public endpoints can be bound to a tenant by path, so requests without a token still get a tenant context
* `TenantContextHolder` exposes the current tenant to the rest of the application
* Spring Boot auto-configuration; every bean is `@ConditionalOnMissingBean`, so any of them can be replaced

## Releases and compatibility

Spring Boot 4 is the only supported line. The Spring Boot 3 line is **end of life**: `0.3.0` is its final
release and it will receive no further releases, fixes or security updates.

| Spring Boot | Java | Artifact                                  | Version | Branch  | Status                    |
|-------------|------|-------------------------------------------|---------|---------|---------------------------|
| 4.x         | 25   | `multitenant-oauth2-spring-boot4-starter` | `0.4.0` | `main`  | Supported                 |
| 3.x         | 17+  | `multitenant-oauth2-spring-boot3-starter` | `0.3.0` | `3.x.x` | **End of life** (final)   |

Both lines share the same package (`com.xenotech.multitenant`) and configuration properties, so moving from
the Spring Boot 3 artifact to the Spring Boot 4 one is a dependency swap (and a Spring Boot / Java 25 upgrade
of your application); no configuration changes are needed. Use exactly one of the two artifacts.
The `3.x.x` branch is frozen and kept only so existing `0.3.0` users can read its source.

> The older `com.xenotech:multitenant-oauth2-spring-boot-starter` (`0.0.x`) artifact is the previous
> naming. New work should use one of the artifacts above.

## Installation

The artifacts are published to the Xeno Maven repository (`repo.myxeno.com`), which must be
available to your build.

### Spring Boot 4

```groovy
dependencies {
    implementation 'com.xenotech:multitenant-oauth2-spring-boot4-starter:0.4.0'
}
```

```xml
<dependency>
    <groupId>com.xenotech</groupId>
    <artifactId>multitenant-oauth2-spring-boot4-starter</artifactId>
    <version>0.4.0</version>
</dependency>
```

### Spring Boot 3 (end of life)

> No further releases. Plan your upgrade to Spring Boot 4 and the `0.4.x` artifact.

```groovy
dependencies {
    implementation 'com.xenotech:multitenant-oauth2-spring-boot3-starter:0.3.0'
}
```

```xml
<dependency>
    <groupId>com.xenotech</groupId>
    <artifactId>multitenant-oauth2-spring-boot3-starter</artifactId>
    <version>0.3.0</version>
</dependency>
```

The starter brings in `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security` and
`spring-boot-starter-web`. It only activates in servlet web applications.

## Configuration

Tenants are defined under `spring.security.oauth2.resourceserver.multitenancy.tenants`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        multitenancy:
          tenants:
            - identifier: realm_1
              enabled: true
              server-url: https://issuer1.com
              realm: realm_1
              client-id: client_1
              client-secret: S3CR3T
              issuer: https://issuer1.com/realms/realm_1
              lazy-load-paths: true
              paths:
                - path: /api/v1/public/*
                  name: public
                  enforcement-mode: DISABLED
            - identifier: realm_2
              enabled: true
              server-url: https://issuer2.com
              realm: realm_2
              client-id: client_2
              client-secret: S3CR3T
              issuer: https://issuer2.com/realms/realm_2
              lazy-load-paths: true
              paths: []
```

| Property                                            | Description |
|-----------------------------------------------------|-------------|
| `identifier`                                        | Tenant key. Must equal the last path segment of the token issuer (the realm name); see [How a tenant is resolved](#how-a-tenant-is-resolved). |
| `enabled`                                           | Disabled tenants are rejected. |
| `issuer`                                            | Issuer URI used to build the JWT decoder (issuer discovery / JWKS). |
| `server-url`, `realm`, `client-id`, `client-secret` | Identity provider details carried on the tenant for use by your own code. |
| `lazy-load-paths`, `enforcement-mode`, `name`       | Carried on the tenant for use by your own code; the starter does not act on them. |
| `paths[].path`                                      | Pattern (`*` matches any characters) of endpoints bound to this tenant when no bearer token is sent. Use `paths: []` when a tenant has none. |

Keep `client-secret` out of source control; use your config server's encryption or environment variables.

## Usage

### 1. Wire the filter and resolver into Spring Security

The starter provides a `TenantContextFilter` and a `TenantAuthenticationManagerResolver` bean. Register the
filter **before** `BearerTokenAuthenticationFilter` so the tenant is known when the token is authenticated,
and plug the resolver into your resource-server configuration:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            TenantAuthenticationManagerResolver resolver,
                                            TenantContextFilter tenantContextFilter) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**", "/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(resolver))
                .addFilterBefore(tenantContextFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
```

Without `addFilterBefore`, the filter runs after Spring Security and every authenticated request fails with
`TenantNotFoundException`.

### 2. Read the current tenant

```java
String tenant = TenantContextHolder.getRequiredTenantIdentifier(); // throws TenantNotFoundException if unset
String maybe  = TenantContextHolder.getTenantIdentifier();         // nullable
```

The context is stored in a `ThreadLocal` and cleared when the request finishes. If you hand work to
another thread, pass the identifier along yourself.

### How a tenant is resolved

For each request, `TenantContextFilter` (registered before `BearerTokenAuthenticationFilter`, see above) runs:

1. If the request carries `Authorization: Bearer <jwt>`, the `iss` claim is read (without validating
   the signature) and the last path segment of the issuer (`https://issuer1.com/realms/realm_1` →
   `realm_1`) is used as the tenant identifier.
2. If no tenant came from a token, the request URI is matched against every tenant's `paths` and the
   matching tenant is used. This is how public endpoints get a tenant.
3. The tenant must exist and be `enabled`, otherwise `TenantNotFoundException` is thrown. If no tenant can
   be determined at all, `TenantResolutionException` is thrown.
4. `TenantAuthenticationManagerResolver` then validates the token against that tenant's configured
   `issuer`. The `iss` claim only selects one of your configured tenants; it is never used as the trust
   source itself.

Requests to `/actuator/**` bypass the filter.

### Customising

Declare your own bean of any of these types and the starter's default backs off:

| Bean                                  | Purpose                                                       |
|---------------------------------------|---------------------------------------------------------------|
| `TenantDetailsService`                | Load tenants from a database or service instead of properties |
| `TenantAuthenticationManagerResolver` | Change how a tenant's `AuthenticationManager` is built        |
| `OidcTenantResolver`                  | Change how the tenant identifier is derived from a request    |
| `TenantContextFilter`                 | Change the filter behaviour                                   |

## Building and testing

The supported line is built from `main` (Java 25). The end-of-life `3.x.x` branch (Java 17) is frozen.
Unit and integration tests live in separate source sets (see `aaaaa-standards` §13.6):

| Task                       | Source set                | What it covers                                                                 |
|----------------------------|---------------------------|--------------------------------------------------------------------------------|
| `./gradlew test`           | `src/test`                | Unit tests: tenant context, resolver, filter, tenant details, token utils      |
| `./gradlew integrationTest`| `src/integrationTest`     | Full Spring Boot context over HTTP against an in-process stub OIDC issuer: multiple tenants, public paths, forged/expired tokens, unknown and disabled tenants. No Docker needed. |
| `./gradlew build`          | both                      | Compiles, runs both test tasks and assembles the jars                          |
| `./gradlew jacocoTestReport` | -                       | Coverage report in `build/reports/jacoco`                                      |

The `CI` workflow (`.github/workflows/ci.yml`) runs unit tests and then integration tests on every push to `main`.

Publishing goes to the Xeno Maven repository (`https://repo.myxeno.com/m2`) and needs `mavenU` and `mavenP`, either as environment variables or in `~/.gradle/gradle.properties`. Without them the repository is not registered, so `publishToMavenLocal` still works.
Versions ending in `-SNAPSHOT` go to the snapshots repository, all others to releases:

```bash
./gradlew publish
```

To cut a release: drop `-SNAPSHOT` from `version` in `build.gradle`, build, publish, tag
(e.g. `v0.4.0`), then move to the next `-SNAPSHOT`.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions or support, please contact vkakama@gmail.com.
