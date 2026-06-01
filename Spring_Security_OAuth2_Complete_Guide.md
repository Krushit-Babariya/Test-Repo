# Spring Security + OAuth 2.0 — The Complete Deep-Dive Guide

> A thorough, beginner-friendly documentation of how Spring Security implements OAuth 2.0 — every role, every class, every configuration property, every flow — explained with real code and plain-English commentary.

---

## Table of Contents

1. [How Spring Security Fits Into the Picture](#1-how-spring-security-fits-into-the-picture)
2. [The Three Roles Spring Security Plays](#2-the-three-roles-spring-security-plays)
3. [Project Setup — Dependencies You Need](#3-project-setup)
4. [Spring Security Architecture Primer](#4-spring-security-architecture-primer)
5. [OAuth2 Login — "Sign in with Google/GitHub"](#5-oauth2-login)
   - ClientRegistration
   - ClientRegistrationRepository
   - The Login Endpoint & Redirect Endpoint
   - What Happens Under the Hood
   - Full Working Configuration
6. [OAuth2 Client — Calling Protected APIs](#6-oauth2-client)
   - OAuth2AuthorizedClient
   - OAuth2AuthorizedClientManager
   - OAuth2AuthorizedClientProvider
   - Using RestClient with OAuth2
   - Using WebClient with OAuth2
   - Client Credentials Grant
7. [OAuth2 Resource Server — Protecting Your API](#7-oauth2-resource-server)
   - JWT Token Validation
   - JwtDecoder Explained
   - Opaque Token Introspection
   - Customizing Authorities from JWT Claims
   - Full Working Configuration
8. [OAuth2 Authorization Server — Being the Auth Server](#8-oauth2-authorization-server)
9. [All Configuration Properties Explained](#9-all-configuration-properties)
10. [JWT Deep Dive in Spring Security](#10-jwt-deep-dive)
11. [Custom Token Claim Mapping](#11-custom-token-claim-mapping)
12. [Method-Level Security with OAuth2 Scopes](#12-method-level-security)
13. [Testing OAuth2 in Spring Boot](#13-testing-oauth2)
14. [Complete Real-World Example — All Three Roles Together](#14-complete-real-world-example)
15. [Common Mistakes and How to Avoid Them](#15-common-mistakes)
16. [Quick Reference Card](#16-quick-reference-card)

---

## 1. How Spring Security Fits Into the Picture

Before writing a single line of code, you need to understand *why* Spring Security is involved in OAuth2 at all.

OAuth2 is just a specification — a document that says "here is the protocol for delegated authorization." Someone still has to implement it. In the Spring world, Spring Security is the library that does that implementation for you. It provides the filters, the classes, the request handlers, and the auto-configuration that translate the abstract OAuth2 protocol into working Java code.

Think of it this way: OAuth2 is the recipe, and Spring Security is the chef that cooks it. You just need to tell the chef what ingredients to use (your client ID, secret, scopes, provider URLs), and it handles the complex cooking process — request signing, token exchange, validation, session management — automatically.

Spring Security integrates OAuth2 at the Servlet Filter level. Every HTTP request to your application passes through a chain of security filters before it reaches your controllers. When OAuth2 is configured, Spring Security adds specific filters to this chain that know how to initiate authorization flows, handle callbacks, validate tokens, and set up the security context.

---

## 2. The Three Roles Spring Security Plays

Spring Security implements three distinct roles from the OAuth2 specification. It is important to understand that a single Spring Boot application can play **one, two, or even all three** of these roles simultaneously, depending on how it is configured.

**Role 1: OAuth2 Client.** Your application wants to access a protected resource on behalf of a user. This includes "Sign in with Google" flows (which is actually a special case of the client role). The client role handles authorization code flows, token exchange, and storing obtained tokens.

**Role 2: OAuth2 Resource Server.** Your application exposes a REST API that is protected by OAuth2 tokens. Other applications (clients) will send Bearer tokens when calling your API, and your application must validate those tokens and enforce access control.

**Role 3: OAuth2 Authorization Server.** Your application *is* the authorization server — the entity that issues tokens, handles user login and consent, and manages client registrations. This is the most complex role and is handled by the Spring Authorization Server project (a separate library built on top of Spring Security).

A typical microservices architecture has one Authorization Server (or uses a third-party like Keycloak/Okta), several Resource Servers (your backend APIs), and one or more Clients (your frontend app or gateway).

---

## 3. Project Setup

Let's start with the right dependencies. Spring Boot makes this easy with starters — each starter corresponds to one of the three roles.

### For OAuth2 Client (Login + Calling APIs on behalf of users)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### For OAuth2 Resource Server (Protecting your API)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### For OAuth2 Authorization Server (Being the auth provider)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
```

### If You Need All Three Roles in One App

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
```

> **Mental Model:** Think of each starter as unlocking a different capability. The client starter says "I want to *use* OAuth2 to get tokens." The resource-server starter says "I want to *accept* OAuth2 tokens and validate them." The authorization-server starter says "I want to *issue* OAuth2 tokens."

---

## 4. Spring Security Architecture Primer

To understand how OAuth2 works in Spring Security, you need to understand the basic architecture first. Don't worry — the key concepts are straightforward.

### The Security Filter Chain

When an HTTP request arrives at your Spring Boot application, it doesn't go directly to your controller. It first passes through a **SecurityFilterChain** — an ordered list of security filters. Each filter inspects or modifies the request. If a filter decides the request is unauthorized, it stops the chain and sends back an error response (401 or 403). Only if all filters pass does the request reach your controller.

You configure the SecurityFilterChain using a `@Bean` of type `SecurityFilterChain`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // HttpSecurity is a fluent builder that lets you configure
        // which filters are added to the chain and how they behave.
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()   // every request needs authentication
            )
            .oauth2Login(Customizer.withDefaults()); // add OAuth2 login filters

        return http.build(); // build and return the chain
    }
}
```

### The SecurityContext and Authentication Object

Once a user is authenticated, Spring Security stores their identity in a **SecurityContext**, which is tied to the current thread. The identity is represented by an **Authentication** object. In the OAuth2 world:

- After OAuth2 Login, the `Authentication` is typically an `OAuth2AuthenticationToken`, which contains an `OAuth2User` (the user's profile from the provider).
- After JWT validation in a Resource Server, the `Authentication` is a `JwtAuthenticationToken`, which contains the decoded JWT claims.

You can access the current user anywhere in your code like this:

```java
// Get the currently authenticated user
Authentication auth = SecurityContextHolder.getContext().getAuthentication();

// If the user logged in via OAuth2
if (auth instanceof OAuth2AuthenticationToken token) {
    OAuth2User user = token.getPrincipal();
    String email = user.getAttribute("email");
    String name  = user.getAttribute("name");
}

// If this is a Resource Server validating a JWT
if (auth instanceof JwtAuthenticationToken token) {
    Jwt jwt = token.getToken();
    String subject = jwt.getSubject(); // "sub" claim
    List<String> scopes = jwt.getClaimAsStringList("scope");
}
```

---

## 5. OAuth2 Login — "Sign in with Google/GitHub"

OAuth2 Login is the feature that lets users click "Sign in with Google" (or GitHub, Okta, Keycloak, Facebook, etc.) and get authenticated in your application using their existing account at that provider.

Under the hood, this uses the **Authorization Code flow** with OpenID Connect. It is technically an OAuth2 Client feature, but it's so common and important that Spring Security gives it its own dedicated configuration section.

### Understanding `ClientRegistration`

A `ClientRegistration` is a Java object that holds ALL the information about one OAuth2 provider relationship — your client ID, secret, the scopes you need, the URLs of the provider's endpoints, and more.

Think of it as a filled-out registration form that describes: "I am registered with Google as client ID `abc123`, my secret is `xyz`, I need the `openid` and `profile` scopes, and here are Google's authorization and token URLs."

```java
// This is what a ClientRegistration looks like when built manually.
// In practice, Spring Boot builds this from your application.yml properties.
ClientRegistration googleRegistration = ClientRegistration
    .withRegistrationId("google")         // unique name you give this registration
    .clientId("your-google-client-id")    // from Google Developer Console
    .clientSecret("your-google-secret")   // from Google Developer Console
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) // the flow type
    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")  // your callback URL
    .scope("openid", "profile", "email")  // what you're asking for
    .authorizationUri("https://accounts.google.com/o/oauth2/auth")  // where user logs in
    .tokenUri("https://oauth2.googleapis.com/token")                 // where tokens come from
    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")    // where to get user profile
    .userNameAttributeName("sub")         // which claim is the unique user ID
    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs") // for JWT verification
    .clientName("Google")                 // display name
    .build();
```

### The Easy Way: Spring Boot `application.yml`

Spring Boot has built-in support for common providers (Google, GitHub, Facebook, Okta). For these, you only need to provide the client ID and secret — Spring Boot fills in all the endpoint URLs automatically:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          # "google" is a magic keyword Spring Boot recognizes
          google:
            client-id: your-google-client-id
            client-secret: your-google-client-secret
            scope:
              - openid
              - profile
              - email
          
          # "github" is also recognized
          github:
            client-id: your-github-client-id
            client-secret: your-github-client-secret
```

That's truly all you need in `application.yml` for Google and GitHub. Spring Boot's auto-configuration fills in all the endpoint URLs from its built-in defaults.

### Custom Provider (Keycloak, Okta, Your Own Auth Server)

For providers Spring Boot doesn't know about by default, you also configure the provider section:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          my-keycloak:                      # you choose this name
            provider: keycloak-local        # points to the provider section below
            client-id: my-app-client
            client-secret: my-secret
            authorization-grant-type: authorization_code
            scope:
              - openid
              - profile
              - email
        
        provider:
          keycloak-local:                   # this name must match registration's provider
            # issuer-uri is the magic property — Spring will auto-discover
            # all other endpoints from the /.well-known/openid-configuration URL
            issuer-uri: http://localhost:8080/realms/myrealm
```

The `issuer-uri` property is powerful: Spring Security automatically appends `/.well-known/openid-configuration` to that URL and fetches the provider's metadata document, which contains all the endpoint URLs. This means you don't have to manually specify `authorization-uri`, `token-uri`, `jwks-uri`, etc. — they're all discovered automatically.

### Understanding `ClientRegistrationRepository`

`ClientRegistrationRepository` is a Spring bean that stores all your `ClientRegistration` objects. When Spring Boot sees your `application.yml` configuration, it automatically creates an `InMemoryClientRegistrationRepository` containing all the registrations you defined.

Spring Security uses this repository throughout the OAuth2 flow to look up registration details by the registration ID (like "google" or "my-keycloak").

```java
// You rarely need to interact with this directly,
// but you can inject it to look up registrations programmatically.
@RestController
public class InfoController {

    private final ClientRegistrationRepository clientRegistrationRepository;

    public InfoController(ClientRegistrationRepository repo) {
        this.clientRegistrationRepository = repo;
    }

    @GetMapping("/client-info")
    public String clientInfo() {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        return "Client ID: " + google.getClientId();
    }
}
```

### The Login and Redirect Endpoints

When you add `.oauth2Login()` to your security configuration, Spring Security automatically registers two special URL endpoints in your application:

**The Authorization Endpoint** (also called the Login Initiation Endpoint):
`/oauth2/authorization/{registrationId}`

When a user visits this URL (e.g., `/oauth2/authorization/google`), Spring Security builds the OAuth2 authorization request URL and redirects the user's browser to Google's login page. This is the URL your "Sign in with Google" button should link to.

**The Redirect Endpoint** (also called the Callback Endpoint):
`/login/oauth2/code/{registrationId}`

This is where Google (or any provider) redirects the user back after login. Spring Security handles this URL automatically — it extracts the `code` parameter, exchanges it for tokens, fetches the user profile, and creates an authenticated session.

You must register the redirect endpoint URL with your OAuth2 provider. For example, in Google Developer Console you'd add: `http://localhost:8080/login/oauth2/code/google`

### What Happens Under the Hood — Step by Step

Understanding this internal flow will help you debug problems and understand error messages:

**Step 1:** User visits `/oauth2/authorization/google`. The `OAuth2AuthorizationRequestRedirectFilter` intercepts this, builds the authorization URL (with client_id, scope, state, redirect_uri), and sends a redirect response to the browser.

**Step 2:** The user's browser follows the redirect to `https://accounts.google.com/o/oauth2/auth?...` and Google shows the login/consent page. The `state` parameter (a random string) is stored in the user's session for CSRF protection.

**Step 3:** The user logs in and approves. Google redirects the browser back to your app at `/login/oauth2/code/google?code=AUTH_CODE&state=RANDOM_STATE`.

**Step 4:** The `OAuth2LoginAuthenticationFilter` intercepts this request. It verifies the `state` matches what was stored (CSRF check). Then it calls Google's token endpoint to exchange the authorization code for tokens (Access Token, ID Token, optionally Refresh Token).

**Step 5:** Spring Security uses the Access Token to call Google's UserInfo endpoint (`https://www.googleapis.com/oauth2/v3/userinfo`) and gets the user's profile (name, email, picture, etc.).

**Step 6:** The `OAuth2UserService` creates an `OAuth2User` object from the profile data. If OpenID Connect is being used (scope includes `openid`), an `OidcUser` is created instead, which also includes ID Token claims.

**Step 7:** Spring Security creates an `OAuth2AuthenticationToken`, stores it in the `SecurityContext`, and redirects the user to the originally requested page (or `/` by default).

### Full Working Configuration — OAuth2 Login

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Define which URLs require authentication
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/public/**").permitAll()  // these are open to everyone
                .anyRequest().authenticated()                     // everything else requires login
            )

            // Enable OAuth2 Login with default settings.
            // Spring Security will handle /oauth2/authorization/* and /login/oauth2/code/*
            .oauth2Login(oauth2 -> oauth2
                // Optional: customize where users go after login
                .defaultSuccessUrl("/dashboard", true)
                // Optional: customize the login page URL
                .loginPage("/login")
                // Optional: customize what happens on failure
                .failureUrl("/login?error=true")
            );

        return http.build();
    }
}
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}      # use environment variables for secrets!
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - openid
              - profile
              - email
```

```java
// A controller that uses the logged-in user's information
@RestController
public class UserController {

    // Method 1: Get user info from @AuthenticationPrincipal annotation
    @GetMapping("/me")
    public Map<String, Object> currentUser(
            @AuthenticationPrincipal OAuth2User principal) {
        // OAuth2User.getAttributes() contains all claims from the UserInfo endpoint
        return Map.of(
            "name",  principal.getAttribute("name"),
            "email", principal.getAttribute("email")
        );
    }

    // Method 2: Get more details including OIDC ID Token claims
    @GetMapping("/me/oidc")
    public Map<String, Object> currentOidcUser(
            @AuthenticationPrincipal OidcUser principal) {
        // OidcUser extends OAuth2User with ID Token claims
        return Map.of(
            "name",    principal.getFullName(),
            "email",   principal.getEmail(),
            "subject", principal.getSubject(),  // the "sub" claim (unique user ID)
            "issued",  principal.getIssuedAt()
        );
    }
}
```

---

## 6. OAuth2 Client — Calling Protected APIs

Beyond just logging users in, the OAuth2 Client role handles a broader use case: making HTTP requests to protected third-party APIs using OAuth2 tokens. This is where your Spring app acts as a "client" that fetches data from another API on behalf of the user.

### Understanding `OAuth2AuthorizedClient`

An `OAuth2AuthorizedClient` is a Spring Security object that bundles together everything needed to make authorized API calls for a specific user to a specific provider:

- The `ClientRegistration` (provider details, client ID/secret)
- The `principalName` (which user this is for)
- The `OAuth2AccessToken` (the actual bearer token to send)
- Optionally, the `OAuth2RefreshToken`

Think of it as a "ready-to-use authorization package" for one user + one provider combination. Spring Security creates and manages these automatically when users log in.

### Understanding `OAuth2AuthorizedClientManager`

The `OAuth2AuthorizedClientManager` is the central coordinator for obtaining and managing `OAuth2AuthorizedClient` instances. When you want to make an API call, you ask the manager to give you an authorized client, and it handles all the complexity:

- If the user already has a valid token stored, it returns it immediately.
- If the token has expired, it uses the Refresh Token to get a new one automatically.
- If no token exists yet, it initiates the authorization flow.

Spring Security automatically registers a default `OAuth2AuthorizedClientManager` bean for you when you include the client starter and have registrations configured. You rarely need to configure it manually.

### Understanding `OAuth2AuthorizedClientProvider`

An `OAuth2AuthorizedClientProvider` is a strategy for obtaining an access token using a specific grant type. There is one provider per grant type:

- `AuthorizationCodeOAuth2AuthorizedClientProvider` — handles the authorization code flow
- `ClientCredentialsOAuth2AuthorizedClientProvider` — handles the client credentials flow
- `RefreshTokenOAuth2AuthorizedClientProvider` — handles token refresh
- `JwtBearerOAuth2AuthorizedClientProvider` — handles the JWT Bearer grant (token exchange)

The `OAuth2AuthorizedClientManager` uses these providers to get tokens. You can customize specific providers (e.g., adjust clock skew tolerance) by publishing a bean of the provider type.

### Using RestClient with OAuth2

The cleanest way to make OAuth2-protected API calls is to configure a `RestClient` with the `OAuth2ClientHttpRequestInterceptor`. This interceptor automatically attaches the Bearer token to every outgoing request.

```java
// Configuration class — sets up a pre-authorized RestClient
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient oauthRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        // Create the interceptor that adds "Authorization: Bearer <token>" to requests
        OAuth2ClientHttpRequestInterceptor requestInterceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);

        return RestClient.builder()
                .requestInterceptor(requestInterceptor)
                .build();
    }
}
```

```java
// Using the RestClient in a service or controller
@Service
public class GitHubService {

    private final RestClient restClient;

    public GitHubService(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<Map> getUserRepositories() {
        // The clientRegistrationId("github") tells the interceptor WHICH
        // registered client's token to use for this request.
        return restClient.get()
                .uri("https://api.github.com/user/repos")
                .attributes(clientRegistrationId("github"))  // use the "github" registration
                .retrieve()
                .body(List.class);
    }
}
```

The import for `clientRegistrationId` is:
```java
import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
```

### Using WebClient with OAuth2 (for Reactive/Non-Blocking)

If you need non-blocking HTTP calls, use `WebClient` with the `ServletOAuth2AuthorizedClientExchangeFilterFunction`:

```java
// Add these dependencies in pom.xml first:
// spring-webflux and reactor-netty

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient oauthWebClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        // Create the filter function that handles token injection
        ServletOAuth2AuthorizedClientExchangeFilterFunction filter =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        return WebClient.builder()
                .apply(filter.oauth2Configuration()) // apply the OAuth2 filter
                .build();
    }
}
```

```java
// Using the WebClient
@Service
public class GitHubWebClientService {

    private final WebClient webClient;

    public GitHubWebClientService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<Map>> getUserRepositories() {
        return webClient.get()
                .uri("https://api.github.com/user/repos")
                .attributes(clientRegistrationId("github"))
                .retrieve()
                .bodyToFlux(Map.class)
                .collectList();
    }
}
```

### Client Credentials Grant — No User Involved (Machine-to-Machine)

The Client Credentials grant is used when your application needs to call another API as itself — not on behalf of any user. This is common in microservice architectures where Service A calls Service B directly.

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          # This registration uses client_credentials, not authorization_code
          my-service-client:
            provider: my-auth-server
            client-id: my-service-client-id
            client-secret: my-service-secret
            authorization-grant-type: client_credentials   # key difference!
            scope:
              - api.read
              - api.write
        
        provider:
          my-auth-server:
            issuer-uri: https://auth.mycompany.com
```

```java
// For client credentials, we scope the token to the application, not to a user.
// We use RequestAttributePrincipalResolver to provide a fixed principal name.

@Configuration
public class ServiceRestClientConfig {

    @Bean
    public RestClient serviceRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        OAuth2ClientHttpRequestInterceptor requestInterceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);

        // RequestAttributePrincipalResolver allows us to specify a principal name
        // per request, so the token is scoped to the application, not a user.
        requestInterceptor.setPrincipalResolver(new RequestAttributePrincipalResolver());

        return RestClient.builder()
                .requestInterceptor(requestInterceptor)
                .build();
    }
}
```

```java
// Using the service client
@Service
public class OrderService {

    private final RestClient restClient;

    @GetMapping("/orders")
    public List<Order> fetchOrders() {
        return restClient.get()
                .uri("https://orders-api.internal/orders")
                .attributes(clientRegistrationId("my-service-client"))
                // "my-application" is the principal name for the application token
                .attributes(principal("my-application"))
                .retrieve()
                .body(List.class);
    }
}
```

---

## 7. OAuth2 Resource Server — Protecting Your API

The Resource Server role is what you implement when you build a REST API that should only be accessible to callers who present a valid Bearer token. This is the most common setup in microservice architectures.

Your API doesn't handle login — it just checks the token. The token was issued by a separate Authorization Server (Google, Okta, Keycloak, or your own).

### JWT Token Validation

JWT (JSON Web Token) is the most common token format. A JWT is self-contained — it carries claims (data) inside it and is cryptographically signed. The Resource Server can validate it without calling the Authorization Server.

The minimum configuration to protect your API with JWT validation:

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Spring Security fetches the public key from this URL automatically
          # by appending /.well-known/openid-configuration or /oauth2/jwks
          issuer-uri: https://your-auth-server.com
```

```java
// The equivalent Java configuration
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers("/actuator/health", "/api/public/**").permitAll()
                // Everything else requires a valid JWT
                .anyRequest().authenticated()
            )
            // Configure this app as a Resource Server that validates JWTs
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults()) // use defaults — auto-detects JwtDecoder bean
            );

        return http.build();
    }

    // JwtDecoder is the component that validates and decodes incoming JWTs.
    // Spring Boot creates this automatically from the issuer-uri property,
    // but you can define it manually for more control.
    @Bean
    public JwtDecoder jwtDecoder() {
        // This fetches the JWKS (public keys) from the auth server and
        // uses them to verify JWT signatures. It also validates:
        // - The token hasn't expired (exp claim)
        // - The issuer matches (iss claim)
        // - The audience matches (aud claim) if configured
        return JwtDecoders.fromIssuerLocation("https://your-auth-server.com");
    }
}
```

### JwtDecoder Explained

`JwtDecoder` is the interface whose job is to take a raw JWT string and produce a validated `Jwt` object. Spring Security provides `NimbusJwtDecoder` as the implementation (using the Nimbus JOSE library internally).

When a request comes in with `Authorization: Bearer eyJhbGci...`, the `BearerTokenAuthenticationFilter` extracts the token string and passes it to the `JwtDecoder`. The decoder:

1. Decodes the base64-encoded header and payload.
2. Fetches the JSON Web Key Set (JWKS) from the Authorization Server's public key endpoint.
3. Verifies the JWT signature using the matching public key.
4. Validates the `exp` (expiry), `iss` (issuer), and optionally `aud` (audience) claims.
5. Returns a `Jwt` object containing all the claims, or throws a `JwtException` if anything is invalid.

```java
// Manual JwtDecoder configuration — for when you need fine-grained control
@Bean
public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri("https://your-auth-server.com/oauth2/jwks") // public keys URL
            .build();

    // Add additional validators on top of the defaults
    // Default validators check: expiry, not-before, issuer
    OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator("my-api");
    OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("https://your-auth-server.com"),
            audienceValidator
    );

    decoder.setJwtValidator(withAudience);
    return decoder;
}

// Custom audience validator
class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = jwt.getAudience();
        if (audiences.contains(this.audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error("invalid_token", "Required audience not found", null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
```

### Opaque Token Introspection

Opaque tokens are tokens whose content is not directly readable — unlike JWTs, you cannot decode them to see the claims. To validate an opaque token, your Resource Server must call the Authorization Server's introspection endpoint and ask "is this token valid, and what does it represent?"

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          # The Authorization Server's introspection endpoint
          introspection-uri: https://your-auth-server.com/oauth2/introspect
          # Credentials your Resource Server uses to authenticate itself
          # when calling the introspection endpoint
          client-id: my-resource-server
          client-secret: my-resource-server-secret
```

```java
@Configuration
@EnableWebSecurity
public class OpaqueTokenResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .opaqueToken(Customizer.withDefaults()) // use OpaqueTokenIntrospector bean
            );
        return http.build();
    }

    @Bean
    public OpaqueTokenIntrospector introspector() {
        // For every incoming request, this will call the introspection endpoint
        // to validate the token and get its metadata.
        return new SpringOpaqueTokenIntrospector(
            "https://your-auth-server.com/oauth2/introspect",
            "my-resource-server",
            "my-resource-server-secret"
        );
    }
}
```

The main trade-off: JWTs are faster (no network call needed for validation) but opaque tokens allow the Authorization Server to instantly revoke them (because every request checks in with the auth server). For high-security use cases, opaque tokens with introspection give you stronger revocation guarantees.

### Customizing Authorities from JWT Claims

By default, Spring Security maps the JWT's `scope` claim (e.g., `"message.read message.write"`) to Spring Security authorities with the prefix `SCOPE_` (e.g., `SCOPE_message.read`). You can customize this mapping.

```java
// Custom JwtAuthenticationConverter — tells Spring Security how to
// extract roles/authorities from the JWT claims.
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
            new JwtGrantedAuthoritiesConverter();

    // By default, Spring looks in the "scope" or "scp" claim.
    // Change this to look in "roles" claim instead:
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

    // By default, authorities get a "SCOPE_" prefix.
    // Change this to "ROLE_" so @PreAuthorize("hasRole('admin')") works:
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return jwtAuthenticationConverter;
}
```

Then wire it into your resource server configuration:

```java
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .jwtAuthenticationConverter(jwtAuthenticationConverter())
    )
)
```

### Accessing JWT Claims in Controllers

Once a request passes JWT validation, the decoded token is available in your controllers:

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    // Method 1: Via @AuthenticationPrincipal — cleanest approach
    @GetMapping("/messages")
    public List<String> getMessages(
            @AuthenticationPrincipal Jwt jwt) {
        // jwt.getClaims() returns all claims as a Map
        String userId = jwt.getSubject();         // the "sub" claim
        List<String> scopes = jwt.getClaimAsStringList("scope");
        String issuer = jwt.getIssuer().toString();

        System.out.println("Request from user: " + userId);
        return List.of("Hello, " + userId);
    }

    // Method 2: Via SecurityContextHolder
    @GetMapping("/admin")
    public String adminOnly() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken)
                SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        return "Admin area. Your subject: " + jwt.getSubject();
    }
}
```

### Protecting Specific Endpoints with Scopes

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            // Only requests with the "message.read" scope can access GET /messages
            .requestMatchers(HttpMethod.GET, "/api/messages")
                .hasAuthority("SCOPE_message.read")
            // Only requests with "message.write" scope can POST
            .requestMatchers(HttpMethod.POST, "/api/messages")
                .hasAuthority("SCOPE_message.write")
            // Admin endpoints require admin scope
            .requestMatchers("/api/admin/**")
                .hasAuthority("SCOPE_admin")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

---

## 8. OAuth2 Authorization Server — Being the Auth Server

The Authorization Server role means your Spring Boot application itself issues OAuth2 tokens, handles user login, shows consent screens, and manages client registrations. This is handled by the **Spring Authorization Server** — a dedicated project that builds on Spring Security.

### Minimal Authorization Server Setup

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class AuthServerConfig {

    // Security chain for the Authorization Server's protocol endpoints
    // (token endpoint, authorization endpoint, etc.)
    @Bean
    @Order(1) // Higher priority than the default security chain
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        // Apply default Auth Server security settings
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http
            // Enable OpenID Connect 1.0 (for ID tokens and userinfo endpoint)
            .getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults());

        http
            // If someone hits a protected auth server endpoint without being logged in,
            // redirect them to the login page (handled by the second security chain)
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            );

        return http.build();
    }

    // Security chain for the rest of the app (login form, etc.)
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .formLogin(Customizer.withDefaults()); // show a standard login form
        return http.build();
    }

    // Define which clients are allowed to request tokens from this server.
    // In production, this would come from a database.
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient myClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("my-client-app")
            .clientSecret("{noop}my-client-secret")  // {noop} means no encoding (dev only!)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("http://localhost:3000/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("message.read")
            .scope("message.write")
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(true) // show consent screen
                .build())
            .build();

        return new InMemoryRegisteredClientRepository(myClient);
    }

    // This key pair is used to sign the JWTs (Access Tokens and ID Tokens)
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey  = (RSAPublicKey)  keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString())
            .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048); // 2048-bit RSA key
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // AuthorizationServerSettings defines your server's public-facing URLs.
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            .issuer("https://my-auth-server.com") // your auth server's URL
            .build();
    }
}
```

This minimal setup gives you a fully functional OAuth2 Authorization Server with the following auto-configured endpoints:

| Endpoint | Path | Purpose |
|---|---|---|
| Authorization | `/oauth2/authorize` | Where users log in and give consent |
| Token | `/oauth2/token` | Where clients exchange codes for tokens |
| JWKS | `/oauth2/jwks` | Public keys for JWT verification |
| Token Revocation | `/oauth2/revoke` | Revoke an access or refresh token |
| Token Introspection | `/oauth2/introspect` | Check if a token is valid |
| OIDC Discovery | `/.well-known/openid-configuration` | Provider metadata document |

---

## 9. All Configuration Properties Explained

Here is a complete reference of every significant Spring Security OAuth2 configuration property.

### OAuth2 Client Properties

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          {registrationId}:       # you name this — e.g., "google", "github", "my-server"
            
            # REQUIRED: Credentials from your OAuth2 provider
            client-id: your-client-id
            client-secret: your-client-secret
            
            # The grant type (flow) to use
            # Options: authorization_code, client_credentials, password, refresh_token
            authorization-grant-type: authorization_code
            
            # Points to a key in the "provider" section below.
            # For well-known providers (google, github, okta, facebook),
            # you can skip this — Spring Boot knows the endpoints already.
            provider: my-provider
            
            # The scopes to request. "openid" triggers OIDC mode.
            scope:
              - openid
              - profile
              - email
              - my-custom-scope
            
            # The URL your auth server redirects to after user login.
            # Default: {baseUrl}/login/oauth2/code/{registrationId}
            # You can use {baseUrl} and {registrationId} as placeholders.
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            
            # Display name used in the auto-generated login page
            client-name: My OAuth Provider
            
            # How the client authenticates itself to the token endpoint.
            # Options: client_secret_basic (HTTP Basic), client_secret_post, none, private_key_jwt
            client-authentication-method: client_secret_basic
        
        provider:
          my-provider:
            # If the provider supports OpenID Connect discovery,
            # just provide the issuer URI — all other URIs are auto-discovered.
            issuer-uri: https://my-auth-server.com
            
            # OR specify each endpoint manually:
            authorization-uri: https://my-auth-server.com/oauth2/authorize
            token-uri: https://my-auth-server.com/oauth2/token
            user-info-uri: https://my-auth-server.com/userinfo
            jwk-set-uri: https://my-auth-server.com/oauth2/jwks
            
            # The claim in the UserInfo response that contains the unique user ID
            user-name-attribute: sub
```

### OAuth2 Resource Server Properties

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        
        # === JWT Mode ===
        jwt:
          # Auto-discovers JWKS URI and validates issuer from this URL
          issuer-uri: https://my-auth-server.com
          
          # OR: Specify JWKS URI directly (where public keys are published)
          jwk-set-uri: https://my-auth-server.com/oauth2/jwks
          
          # OR: For simple cases (custom JWT with local key),
          # provide a public key file on the classpath
          public-key-location: classpath:public.pem
          
          # The expected audience value in the JWT's "aud" claim.
          # If specified, tokens without this audience are rejected.
          audiences:
            - my-api-service
        
        # === Opaque Token Mode ===
        opaquetoken:
          # The auth server's introspection endpoint
          introspection-uri: https://my-auth-server.com/oauth2/introspect
          # Credentials your resource server uses when calling the introspection endpoint
          client-id: my-resource-server-id
          client-secret: my-resource-server-secret
```

---

## 10. JWT Deep Dive in Spring Security

Understanding how Spring Security handles JWTs will help you debug authentication errors and customize token processing.

### The JWT Validation Pipeline

When a request arrives with `Authorization: Bearer <token>`, here's what Spring Security does internally:

```
Request arrives
    ↓
BearerTokenAuthenticationFilter
    → Extracts the token string from the Authorization header
    ↓
JwtDecoder.decode(tokenString)
    → Decodes the base64 header and payload
    → Fetches JWKS (public keys) from the auth server (cached)
    → Verifies the signature
    → Validates: exp (not expired), iss (correct issuer), nbf (not before), aud (audience)
    → Returns a Jwt object, or throws JwtException
    ↓
JwtAuthenticationConverter.convert(jwt)
    → Extracts authorities from the "scope" or "scp" claim
    → Creates JwtAuthenticationToken(jwt, authorities)
    ↓
SecurityContext.setAuthentication(token)
    → The request is now authenticated
    ↓
AuthorizationFilter
    → Checks if this user has permission to access the requested URL
```

### What's Inside a JWT — The `Jwt` Object

After decoding, you get a `Jwt` object with convenient accessor methods:

```java
@GetMapping("/example")
public void example(@AuthenticationPrincipal Jwt jwt) {
    // Standard registered claims
    String subject       = jwt.getSubject();              // "sub" — user's unique ID
    String issuer        = jwt.getIssuer().toString();    // "iss" — who issued this token
    Instant issuedAt     = jwt.getIssuedAt();             // "iat" — when issued
    Instant expiresAt    = jwt.getExpiresAt();            // "exp" — when it expires
    List<String> audience = jwt.getAudience();            // "aud" — intended recipients
    String jwtId         = jwt.getId();                   // "jti" — unique token ID

    // Custom claims (whatever your auth server puts in)
    String email         = jwt.getClaimAsString("email");
    List<String> roles   = jwt.getClaimAsStringList("roles");
    Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
    Map<String, Object> address = jwt.getClaimAsMap("address");

    // Raw access to all claims
    Map<String, Object> allClaims = jwt.getClaims();
}
```

### Using a Local Public Key (for Custom JWT Issuers)

When you issue your own JWTs (without a full authorization server), you can configure the Resource Server to validate them using a local RSA public key:

```bash
# Generate RSA key pair (run in terminal)
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
```

```yaml
# application.yml for the Resource Server
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          public-key-location: classpath:public.pem  # place in src/main/resources
```

```java
// How to create/sign JWTs (for the issuer side):
@Service
public class JwtIssuerService {

    // In the issuer app, load the PRIVATE key to sign tokens
    @Value("classpath:private.pem")
    private RSAPrivateKey privateKey;

    @Value("classpath:public.pem")
    private RSAPublicKey publicKey;

    public String issueToken(String subject, List<String> scopes) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(
            new JWKSet(new RSAKey.Builder(publicKey).privateKey(privateKey).build())
        );
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("https://my-app.com")
            .subject(subject)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .claim("scope", String.join(" ", scopes))
            .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
```

---

## 11. Custom Token Claim Mapping

One of the most common real-world requirements is extracting roles, groups, or other custom data from your JWT and making them available as Spring Security authorities (for use in `hasRole()`, `hasAuthority()`, `@PreAuthorize`, etc.).

### Extracting Roles from a Nested Claim (like Keycloak)

Keycloak, for example, puts roles in a nested structure like:
```json
{
  "realm_access": {
    "roles": ["admin", "user", "manager"]
  }
}
```

Here's how to extract those and make them work with Spring Security:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    // A custom converter that knows how to extract Keycloak's nested roles
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(keycloakRolesConverter());
    return converter;
}

private Converter<Jwt, Collection<GrantedAuthority>> keycloakRolesConverter() {
    return jwt -> {
        // Extract realm_access.roles from the JWT claims
        Map<String, Object> realmAccess =
                jwt.getClaimAsMap("realm_access");

        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");

        // Map each role string to a GrantedAuthority with "ROLE_" prefix
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    };
}
```

Then wire it in:

```java
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .jwtAuthenticationConverter(jwtAuthenticationConverter())
    )
)
```

Now you can protect endpoints with:

```java
.requestMatchers("/admin/**").hasRole("ADMIN")
// or
@PreAuthorize("hasRole('MANAGER')")
```

---

## 12. Method-Level Security with OAuth2 Scopes

Method security allows you to place security checks directly on your service or controller methods using annotations. This works beautifully with OAuth2 scopes.

```java
// Enable method security in your configuration class
@Configuration
@EnableMethodSecurity // This annotation enables @PreAuthorize, @PostAuthorize, etc.
public class SecurityConfig {
    // ...
}
```

```java
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    // Only callers with "SCOPE_message.read" authority can call this
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_message.read')")
    public List<Message> getMessages() {
        return messageService.findAll();
    }

    // Only callers with "SCOPE_message.write" can call this
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_message.write')")
    public Message createMessage(@RequestBody MessageRequest request) {
        return messageService.create(request);
    }

    // Multiple conditions — needs BOTH scopes
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_message.write') and hasAuthority('SCOPE_message.delete')")
    public void deleteMessage(@PathVariable Long id) {
        messageService.delete(id);
    }

    // Access JWT claims directly in SpEL expressions
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<Message> getMyMessages(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return messageService.findByUserId(userId);
    }
}
```

---

## 13. Testing OAuth2 in Spring Boot

Spring Security provides excellent test support so you don't need a running auth server during tests.

### Testing Resource Server Endpoints

```java
@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMessages_withValidJwt_returns200() throws Exception {
        mockMvc.perform(
            get("/api/messages")
                // .with(jwt()) simulates a valid JWT — no real auth server needed
                .with(jwt()
                    .jwt(token -> token
                        .subject("user-123")           // set the "sub" claim
                        .claim("scope", "message.read") // set scopes
                        .claim("email", "user@example.com")
                    )
                )
        )
        .andExpect(status().isOk());
    }

    @Test
    void getMessages_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/messages"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMessage_withoutDeleteScope_returns403() throws Exception {
        mockMvc.perform(
            delete("/api/messages/1")
                .with(jwt().jwt(token -> token
                    .claim("scope", "message.read") // has read but not delete
                ))
        )
        .andExpect(status().isForbidden()); // 403 because scope is missing
    }
}
```

The `jwt()` import is:
```java
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
```

### Testing OAuth2 Login Endpoints

```java
@Test
void dashboard_withOAuth2Login_returns200() throws Exception {
    mockMvc.perform(
        get("/dashboard")
            // .with(oauth2Login()) simulates a completed OAuth2 login
            .with(oauth2Login()
                .attributes(attrs -> attrs
                    .put("name", "Test User")
                    .put("email", "test@example.com")
                )
                .authorities(new SimpleGrantedAuthority("SCOPE_profile"))
            )
    )
    .andExpect(status().isOk());
}
```

---

## 14. Complete Real-World Example — All Three Roles Together

Here is a realistic microservices setup with three separate Spring Boot apps demonstrating all roles.

### App 1: Authorization Server (port 9000)

```java
@SpringBootApplication
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
```

```java
@Configuration
@EnableWebSecurity
public class AuthServerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(Customizer.withDefaults()); // enable /userinfo endpoint
        http.exceptionHandling(e ->
            e.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain loginChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public RegisteredClientRepository clientRepository() {
        // The web frontend client
        RegisteredClient webClient = RegisteredClient.withId("web-client-id")
            .clientId("web-app")
            .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode("web-secret"))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://localhost:8080/login/oauth2/code/my-server")
            .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
            .scope("api.read").scope("api.write")
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
            .build();

        // The backend service client (client credentials)
        RegisteredClient serviceClient = RegisteredClient.withId("service-client-id")
            .clientId("backend-service")
            .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode("service-secret"))
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("api.read")
            .build();

        return new InMemoryRegisteredClientRepository(webClient, serviceClient);
    }

    @Bean public JWKSource<SecurityContext> jwkSource() { /* ... key generation ... */ }
    @Bean public AuthorizationServerSettings settings() {
        return AuthorizationServerSettings.builder()
            .issuer("http://localhost:9000").build();
    }

    // Simple in-memory user store for the auth server's login form
    @Bean
    public UserDetailsService users() {
        UserDetails alice = User.withDefaultPasswordEncoder()
            .username("alice").password("password").roles("USER").build();
        return new InMemoryUserDetailsManager(alice);
    }
}
```

### App 2: Resource Server — Messages API (port 8090)

```yaml
# application.yml
server:
  port: 8090
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000  # our auth server
```

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/messages").hasAuthority("SCOPE_api.read")
                .requestMatchers(HttpMethod.POST, "/messages").hasAuthority("SCOPE_api.write")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

```java
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final List<String> messages = new ArrayList<>(List.of("Hello", "World"));

    @GetMapping
    public List<String> getAll(@AuthenticationPrincipal Jwt jwt) {
        System.out.println("Accessed by: " + jwt.getSubject());
        return messages;
    }

    @PostMapping
    public String add(@RequestBody String message) {
        messages.add(message);
        return "Added: " + message;
    }
}
```

### App 3: Web Frontend — OAuth2 Client (port 8080)

```yaml
# application.yml
server:
  port: 8080
spring:
  security:
    oauth2:
      client:
        registration:
          my-server:
            provider: my-auth-server
            client-id: web-app
            client-secret: web-secret
            authorization-grant-type: authorization_code
            redirect-uri: http://localhost:8080/login/oauth2/code/my-server
            scope:
              - openid
              - profile
              - api.read
              - api.write
        provider:
          my-auth-server:
            issuer-uri: http://localhost:9000
```

```java
@Configuration
@EnableWebSecurity
public class ClientSecurityConfig {

    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())
            .oauth2Client(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public RestClient messagesClient(OAuth2AuthorizedClientManager manager) {
        OAuth2ClientHttpRequestInterceptor interceptor =
            new OAuth2ClientHttpRequestInterceptor(manager);
        return RestClient.builder()
            .baseUrl("http://localhost:8090")
            .requestInterceptor(interceptor)
            .build();
    }
}
```

```java
@Controller
public class FrontendController {

    private final RestClient messagesClient;

    public FrontendController(RestClient messagesClient) {
        this.messagesClient = messagesClient;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal OidcUser user) {
        // Fetch messages from the Resource Server — token is attached automatically
        List<String> messages = messagesClient.get()
            .uri("/messages")
            .attributes(clientRegistrationId("my-server"))
            .retrieve()
            .body(List.class);

        model.addAttribute("user", user.getFullName());
        model.addAttribute("messages", messages);
        return "dashboard"; // Thymeleaf template
    }
}
```

---

## 15. Common Mistakes and How to Avoid Them

**Mistake 1: Exposing `client-secret` directly in `application.yml` in source control.** Always use environment variables or a secrets manager:
```yaml
# WRONG
client-secret: my-actual-secret

# CORRECT — reads from environment variable OAUTH2_CLIENT_SECRET
client-secret: ${OAUTH2_CLIENT_SECRET}
```

**Mistake 2: Forgetting to add `issuer-uri` validation on the Resource Server.** If you only specify `jwk-set-uri` without `issuer-uri`, Spring Security won't validate the `iss` (issuer) claim, leaving you vulnerable to token substitution attacks. Always configure both or use `issuer-uri` alone (which validates both).

**Mistake 3: Using Implicit Grant for SPAs.** The Implicit Grant is deprecated. Use Authorization Code + PKCE. Spring Security supports PKCE on the client side via `ClientRegistration` — it's enabled automatically when `client-secret` is not set and `client-authentication-method: none` is configured.

**Mistake 4: Confusing SCOPE_ prefix with ROLE_ prefix.** JWT scopes become `SCOPE_api.read` in Spring Security. Database roles become `ROLE_ADMIN`. These are different and non-interchangeable in `hasRole()` vs `hasAuthority()`:
```java
// Scope check (use hasAuthority, NOT hasRole)
.hasAuthority("SCOPE_api.read")   // CORRECT for JWT scopes

// Role check (use hasRole — automatically adds ROLE_ prefix)
.hasRole("ADMIN")   // equivalent to hasAuthority("ROLE_ADMIN")
```

**Mistake 5: Not handling token expiry in RestClient calls.** The default `OAuth2AuthorizedClientManager` automatically refreshes expired tokens when a Refresh Token is available. However, if you construct your own `RestClient` without the interceptor, you'll need to handle this manually. Always use `OAuth2ClientHttpRequestInterceptor`.

**Mistake 6: Redirect URI mismatch.** The redirect URI in your `application.yml` must exactly match what you registered with your OAuth2 provider. Trailing slashes, HTTP vs HTTPS, and `localhost` vs `127.0.0.1` all cause mismatches.

**Mistake 7: Missing `@EnableMethodSecurity` when using `@PreAuthorize`.** Without this annotation on your `@Configuration` class, `@PreAuthorize` and `@PostAuthorize` do nothing silently.

---

## 16. Quick Reference Card

| What you want | Configuration |
|---|---|
| "Sign in with Google" | `oauth2Login()` + registration with `openid` scope |
| Call a protected API for logged-in user | `oauth2Client()` + `RestClient` with `OAuth2ClientHttpRequestInterceptor` |
| Call an API as the application (no user) | `client_credentials` grant type |
| Protect your REST API with JWT | `oauth2ResourceServer().jwt()` + `issuer-uri` |
| Protect your REST API with opaque tokens | `oauth2ResourceServer().opaqueToken()` + `introspection-uri` |
| Get user info in a controller | `@AuthenticationPrincipal OidcUser` or `@AuthenticationPrincipal Jwt` |
| Check scope on an endpoint | `.hasAuthority("SCOPE_my.scope")` |
| Check role on an endpoint | `.hasRole("ADMIN")` (adds ROLE_ prefix automatically) |
| Protect a method | `@PreAuthorize("hasAuthority('SCOPE_...')")` + `@EnableMethodSecurity` |
| Map JWT claims to roles | Custom `JwtAuthenticationConverter` |
| Extract Keycloak roles | Custom converter reading `realm_access.roles` claim |
| Test without auth server | `.with(jwt(...))` in MockMvc tests |
| Run your own auth server | `spring-boot-starter-oauth2-authorization-server` |

---

### The Mental Model That Ties Everything Together

Imagine three employees in an office building:

The **Authorization Server** is the security desk at the building entrance. They check your identity (authentication), decide what floors you can visit (scopes), and issue you a visitor badge (access token).

The **Client** is someone who needs to visit the building on your behalf — like a courier. You go to the security desk together, they verify you, you tell security "this courier can visit floors 3 and 5 for me" (consent + scopes), and the courier gets a badge.

The **Resource Server** is the lock on each floor's door. When the courier arrives, they scan their badge. The lock verifies the badge is genuine (JWT signature check), checks it hasn't expired, and confirms it says they're allowed on this floor (scope check). If all passes, the door opens.

Spring Security provides all the machinery for each of these roles — you just configure which role your application plays and provide the credentials (client ID, secret, issuer URI). The framework handles all the HTTP redirects, token exchange, signature verification, and session management automatically.

---

*Reference: Spring Security Documentation — OAuth2 (https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)*
*Version: Spring Security 7.0.x / Spring Boot 3.x*
