# Multi-Tenant OAuth2 Spring Boot Starter
This library provides support for integrating multiple JWT issuer URLs,
allowing the use of multiple realms within a single Spring Boot application.
It is designed to simplify the configuration and management of multiple OAuth2 identity providers for multi-tenant applications.


### Features

* Support for multiple JWT issuers
* Configuration of multiple realms
* Automatic JWT validation for each realm
* Easy integration with Spring Security

### Requirements
* Java 17 or higher
* Spring Boot 3.x or higher

### Installation
Add the following dependency to your pom.xml file:
```xml
<dependency>
<groupId>com.xenotech</groupId>
<artifactId>multitenant-oauth2-spring-boot-starter</artifactId>
<version>0.0.1-SNAPSHOT</version>
</dependency>
```
For Gradle, add the following line to your build.gradle file:
```groovy
implementation 'com.xenotech:multitenant-oauth2-spring-boot-starter:0.0.1-SNAPSHOT'
```
### Configuration
1. **Define JWT Issuers**
   In your application.yml or application.properties, define the JWT issuers and their configurations:

```yaml
multitenancy:
  http:
    header-name: X-TenantId
  tenants:
    - identifier: tenant_1
      enabled: true
      server-url: https://issuer1.com
      realm: realm_1
      client-id: client_1
      client-secret: S3CR3T
      issuer: https://issuer1.com/realms/realm_1
      lazy-load-paths: true
      paths:
        - path: /api/v1/xx/xx/*
          name: public
          enforcement-mode: DISABLED
    - identifier: tenant_2
      enabled: true
      server-url: https://issuer2.com
      realm: realm_2
      client-id: client_2
      client-secret: S3CR3T
      issuer:  https://issuer2.com/realms/realm_2
      lazy-load-paths: true
      paths:
        - path: /api/v1/xx/xx/*
          name: public
          enforcement-mode: DISABLED
```

### Usage
Once configured, the library will automatically handle the validation of JWTs from the specified issuers.
You can add as many issuers as needed to support multiple realms within your application.

### License
This project is licensed under the MIT License - see the LICENSE file for details.

### Acknowledgements
This library was inspired by the need to support multi-tenant OAuth2 configurations in Spring Boot applications.

Contact
For questions or support, please contact vkakama@gmail.com.
