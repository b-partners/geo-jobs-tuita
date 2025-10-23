package app.bpartners.geojobs.endpoint.rest.security;

import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.*;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.rest.readme.monitor.ReadmeMonitorConf;
import app.bpartners.geojobs.endpoint.rest.readme.monitor.ReadmeMonitorFilter;
import app.bpartners.geojobs.endpoint.rest.readme.monitor.factory.ReadmeLogFactory;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConf {
  private final HandlerExceptionResolver exceptionResolver;
  private final AuthenticationManager authenticationManager;
  private final EventProducer eventProducer;
  private final ReadmeMonitorConf readmeMonitorConf;
  private final ReadmeLogFactory readmeLogFactory;
  private final AuthProvider authProvider;

  public SecurityConf(
      // InternalToExternalErrorHandler behind
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
      AuthenticationManager authenticationManager,
      EventProducer eventProducer,
      ReadmeMonitorConf readmeMonitorConf,
      ReadmeLogFactory readmeLogFactory,
      AuthProvider authProvider) {
    this.exceptionResolver = exceptionResolver;
    this.authenticationManager = authenticationManager;
    this.eventProducer = eventProducer;
    this.readmeMonitorConf = readmeMonitorConf;
    this.readmeLogFactory = readmeLogFactory;
    this.authProvider = authProvider;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
    var anonymousPath =
        new OrRequestMatcher(
            new AntPathRequestMatcher("/**", OPTIONS.toString()),
            new AntPathRequestMatcher("/ping", GET.name()),
            new AntPathRequestMatcher("/health/event/uuids", POST.name()),
            new AntPathRequestMatcher("/health/**", GET.name()),
            new AntPathRequestMatcher("/**", OPTIONS.toString()),
            new AntPathRequestMatcher("/readme/webhook", POST.name()));
    httpSecurity
        .exceptionHandling(
            (exceptionHandler) ->
                exceptionHandler
                    .authenticationEntryPoint(
                        // note(spring-exception)
                        // https://stackoverflow.com/questions/59417122/how-to-handle-usernamenotfoundexception-spring-security
                        // issues like when a user tries to access a resource
                        // without appropriate authentication elements
                        (req, res, e) ->
                            exceptionResolver.resolveException(
                                req, res, null, forbiddenWithRemoteInfo(e, req)))
                    .accessDeniedHandler(
                        // note(spring-exception): issues like when a user not having required roles
                        (req, res, e) ->
                            exceptionResolver.resolveException(
                                req, res, null, forbiddenWithRemoteInfo(e, req))))
        .addFilterBefore(
            apiKeyAuthFilter(new NegatedRequestMatcher(anonymousPath)),
            AnonymousAuthenticationFilter.class)
        .addFilterAfter(
            // TODO: Activate monitoring on all authenticated endpoints after test
            readmeMonitorFilter(new AntPathRequestMatcher("/usage", GET.name())),
            AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorizationManagerRequestMatcherRegistry ->
                authorizationManagerRequestMatcherRegistry
                    .requestMatchers(anonymousPath)
                    .anonymous()
                    .requestMatchers(OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(GET, "/image")
                    .authenticated()
                    .requestMatchers("/jobs/*/annotationProcessing")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/tilingJobs", "/tilingJobs/**")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/tilingJobs/*/taskStatistics")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/tilingJobs")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/tilingJobs/import")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/tilingJobs/*/duplications")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/tilingJobs/*/taskFiltering")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(PUT, "/tilingJobs/*/retry")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/detectionJobs", "/detectionJobs/**")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/detectionJobs/*/taskStatistics")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/detectionJobs/**")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(PUT, "/detectionJobs/*/taskFiltering")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(PUT, "/detectionJobs/*/retry")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/detectionJobs/*/detectedParcels")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/detectionJobs/*/humanVerificationStatus")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/detectionJobs/*/geojsonsUrl")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(PUT, "/parcelization")
                    .hasAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/detections/*")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(PUT, "/detections/*/step")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(PUT, "/communities/*/detections/*/step")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/detections/*/roofer")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/detections/*/sync")
                    .authenticated() // TODO: change later
                    .requestMatchers(POST, "/detections/*/roofDelimiter")
                    .authenticated() // TODO: change later
                    .requestMatchers(POST, "/detections/*/addresses")
                    .authenticated() // TODO: change later
                    .requestMatchers(POST, "/detections/*/roofer/email")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(GET, "/detections/*")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(PUT, "/detections/*/roofs/properties")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/detections/*/geojson")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(POST, "/detections/*/shape")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/detections/*/excel")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/detections/*/image")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/detections/*/pdf")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/detections/*/geoJsonResult")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(GET, "/detections")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(GET, "/usage")
                    .hasAnyAuthority(
                        ROLE_ADMIN.name(), ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .requestMatchers(POST, "/api/keys")
                    .hasAnyAuthority(ROLE_ADMIN.name())
                    .requestMatchers(DELETE, "/keys")
                    .hasAnyAuthority(ROLE_COMMUNITY.name(), ROLE_INSURANCE.name())
                    .anyRequest()
                    .denyAll())
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(Customizer.withDefaults())
        .sessionManagement(
            httpSecuritySessionManagementConfigurer ->
                httpSecuritySessionManagementConfigurer.sessionCreationPolicy(STATELESS));

    return httpSecurity.build();
  }

  private Exception forbiddenWithRemoteInfo(Exception e, HttpServletRequest req) {
    log.info(
        String.format(
            "Access is denied for remote caller: address=%s, host=%s, port=%s",
            req.getRemoteAddr(), req.getRemoteHost(), req.getRemotePort()));
    return new ForbiddenException(e.getMessage());
  }

  private ApiKeyAuthFilter apiKeyAuthFilter(RequestMatcher requestMatcher) {
    ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(requestMatcher);
    apiKeyFilter.setAuthenticationManager(authenticationManager);
    apiKeyFilter.setAuthenticationSuccessHandler(
        (httpServletRequest, httpServletResponse, authentication) -> {});
    apiKeyFilter.setAuthenticationFailureHandler(
        (req, res, e) ->
            // note(spring-exception)
            // issues like when a user is not found(i.e. UsernameNotFoundException)
            // or other exceptions thrown inside authentication provider.
            // In fact, this handles other authentication exceptions that are
            // not handled by AccessDeniedException and AuthenticationEntryPoint
            exceptionResolver.resolveException(req, res, null, forbiddenWithRemoteInfo(e, req)));
    return apiKeyFilter;
  }

  private ReadmeMonitorFilter readmeMonitorFilter(RequestMatcher requestMatcher) {
    return new ReadmeMonitorFilter(
        requestMatcher, readmeMonitorConf, readmeLogFactory, eventProducer, authProvider);
  }
}
