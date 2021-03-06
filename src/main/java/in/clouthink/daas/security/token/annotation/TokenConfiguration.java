package in.clouthink.daas.security.token.annotation;

import in.clouthink.daas.security.token.configure.TokenConfigurer;
import in.clouthink.daas.security.token.configure.TokenConfigurerAdapter;
import in.clouthink.daas.security.token.configure.UrlAclProviderBuilder;
import in.clouthink.daas.security.token.core.*;
import in.clouthink.daas.security.token.core.acl.AccessRequestRoleVoter;
import in.clouthink.daas.security.token.core.acl.AccessRequestUserVoter;
import in.clouthink.daas.security.token.federation.DefaultFederationService;
import in.clouthink.daas.security.token.federation.FederationService;
import in.clouthink.daas.security.token.spi.*;
import in.clouthink.daas.security.token.spi.impl.DefaultUrlAuthorizationProvider;
import in.clouthink.daas.security.token.spi.impl.SimpleFederationProvider;
import in.clouthink.daas.security.token.spi.impl.TokenAuthenticationProvider;
import in.clouthink.daas.security.token.spi.impl.UsernamePasswordAuthenticationProvider;
import in.clouthink.daas.security.token.spi.impl.memory.CaptchaProviderMemoryImpl;
import in.clouthink.daas.security.token.spi.impl.memory.IdentityProviderMemoryImpl;
import in.clouthink.daas.security.token.spi.impl.memory.LoginAttemptProviderMemoryImpl;
import in.clouthink.daas.security.token.spi.impl.memory.TokenProviderMemoryImpl;
import in.clouthink.daas.security.token.support.i18n.DefaultMessageProvider;
import in.clouthink.daas.security.token.support.i18n.MessageProvider;
import in.clouthink.daas.security.token.support.web.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collection;

@Configuration
public class TokenConfiguration implements ImportAware, BeanFactoryAware {

    protected ListableBeanFactory beanFactory;

    protected BeanDefinitionRegistry beanDefinitionRegistry;

    protected AnnotationAttributes enableToken;

    protected TokenConfigurer tokenConfigurer = new TokenConfigurerAdapter();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = (ListableBeanFactory) beanFactory;
        this.beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;
    }

    @Autowired(required = false)
    void setConfigurers(Collection<TokenConfigurer> configurers) {
        if (CollectionUtils.isEmpty(configurers)) {
            return;
        }
        if (configurers.size() > 1) {
            throw new IllegalStateException("Only one TokenConfigurer may exist");
        }
        TokenConfigurer configurer = configurers.iterator().next();
        this.tokenConfigurer = configurer;
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.enableToken = AnnotationAttributes.fromMap(importMetadata.getAnnotationAttributes(EnableToken.class.getName(),
                                                                                               false));
        Assert.notNull(this.enableToken,
                       "@EnableToken is not present on importing class " + importMetadata.getClassName());
    }

    @Bean
    @Autowired
    @DependsOn({"daasAuthorizationManager", "daasFeatureConfigurer"})
    public AuthorizationFilter daasTokenAuthorizationFilter(AuthorizationManager authorizationManager,
                                                            FeatureConfigurer featureConfigurer,
                                                            MessageProvider messageProvider) {
        AuthorizationFilter authorizationFilter = new AuthorizationFilter();
        authorizationFilter.setAuthorizationManager(authorizationManager);
        authorizationFilter.setAuthorizationFailureHandler(new DefaultAuthorizationFailureHandler(messageProvider));
        authorizationFilter.setFeatureConfigurer(featureConfigurer);
        tokenConfigurer.configure(authorizationFilter);
        return authorizationFilter;
    }

    @Bean
    @Autowired
    @DependsOn({"daasAuthenticationManager", "daasFeatureConfigurer"})
    public AuthenticationFilter daasTokenAuthenticationFilter(AuthenticationManager authenticationManager,
                                                              FeatureConfigurer featureConfigurer,
                                                              MessageProvider messageProvider) {
        AuthenticationFilter authenticationFilter = new AuthenticationFilter();
        authenticationFilter.setAuthenticationFailureHandler(new DefaultAuthenticationFailureHandler(messageProvider));
        authenticationFilter.setFeatureConfigurer(featureConfigurer);
        tokenConfigurer.configure(authenticationFilter);
        return authenticationFilter;
    }

    @Bean
    @Autowired
    @DependsOn({"daasAuthenticationManager", "daasFeatureConfigurer"})
    public TokenAuthenticationFilter daasTokenPreAuthenticationFilter(AuthenticationManager authenticationManager,
                                                                      FeatureConfigurer featureConfigurer,
                                                                      MessageProvider messageProvider) {
        TokenAuthenticationFilter tokenAuthenticationFilter = new TokenAuthenticationFilter();
        tokenAuthenticationFilter.setAuthenticationManager(authenticationManager);
        tokenAuthenticationFilter.setAuthorizationFailureHandler(new DefaultAuthorizationFailureHandler(messageProvider));
        tokenAuthenticationFilter.setFeatureConfigurer(featureConfigurer);
        tokenConfigurer.configure(tokenAuthenticationFilter);
        return tokenAuthenticationFilter;
    }

    @Bean
    @Autowired
    @DependsOn("daasAuthenticationManager")
    public LogoutEndpoint daasTokenLogoutEndpoint(AuthenticationManager authenticationManager,
                                                  MessageProvider messageProvider) {
        LogoutEndpoint logoutEndpoint = new LogoutEndpoint();
        logoutEndpoint.setAuthenticationManager(authenticationManager);
        logoutEndpoint.setAuthorizationFailureHandler(new DefaultAuthorizationFailureHandler(messageProvider));
        tokenConfigurer.configure(logoutEndpoint);
        return logoutEndpoint;
    }

    @Bean
    @Autowired
    @DependsOn({"daasAuthenticationManager", "daasCaptchaManager", "daasFeatureConfigurer"})
    public LoginEndpoint daasTokenLoginEndpoint(AuthenticationManager authenticationManager,
                                                CaptchaManager captchaManager,
                                                FeatureConfigurer featureConfigurer,
                                                MessageProvider messageProvider) {
        LoginEndpoint loginEndpoint = new LoginEndpoint();
        loginEndpoint.setAuthenticationManager(authenticationManager);
        loginEndpoint.setFeatureConfigurer(featureConfigurer);
        loginEndpoint.setCaptchaManager(captchaManager);
        loginEndpoint.setAuthenticationFailureHandler(new DefaultAuthenticationFailureHandler(messageProvider));
        tokenConfigurer.configure(loginEndpoint);
        return loginEndpoint;
    }

    @Bean
    @Autowired
    @DependsOn({"daasUsernamePasswordAuthenticationProvider", "daasTokenAuthenticationProvider", "daasLoginAttemptProvider", "daasFeatureConfigurer"})
    public AuthenticationManager daasAuthenticationManager(IdentityProvider identityProvider,
                                                           LoginAttemptManager loginAttemptManager,
                                                           TokenManager tokenManager,
                                                           FeatureConfigurer featureConfigurer) {
        DefaultAuthenticationManager result = new DefaultAuthenticationManager();
        result.addProvider(daasUsernamePasswordAuthenticationProvider(identityProvider,
                                                                      loginAttemptManager,
                                                                      tokenManager,
                                                                      featureConfigurer));
        result.addProvider(daasTokenAuthenticationProvider(identityProvider, tokenManager));
        return result;
    }

    @Bean
    @Autowired
    @DependsOn({"daasSimpleFederationProvider", "daasTokenAuthenticationProvider"})
    public FederationService daasFederationService(IdentityProvider identityProvider,
                                                   TokenManager tokenManager) {
        DefaultFederationService result = new DefaultFederationService();
        result.addProvider(daasSimpleFederationProvider(tokenManager));
        result.addProvider(daasTokenAuthenticationProvider(identityProvider, tokenManager));
        return result;
    }

    @Bean
    @Autowired
    public AuthorizationManager daasAuthorizationManager(AuthorizationProvider authorizationProvider) {
        DefaultAuthorizationManager result = new DefaultAuthorizationManager();
        result.getProviders().add(authorizationProvider);
        return result;
    }

    @Bean
    @Autowired
    public AuthenticationProvider daasUsernamePasswordAuthenticationProvider(IdentityProvider identityProvider,
                                                                             LoginAttemptManager loginAttemptManager,
                                                                             TokenManager tokenManager,
                                                                             FeatureConfigurer featureConfigurer) {
        UsernamePasswordAuthenticationProvider result = new UsernamePasswordAuthenticationProvider();
        result.setIdentityProvider(identityProvider);
        result.setLoginAttemptManager(loginAttemptManager);
        result.setTokenManager(tokenManager);
        result.setFeatureConfigurer(featureConfigurer);
        return result;
    }

    @Bean
    @Autowired
    public FederationProvider daasSimpleFederationProvider(TokenManager tokenManager) {
        SimpleFederationProvider result = new SimpleFederationProvider();
        result.setTokenManager(tokenManager);
        return result;
    }

    @Bean
    @Autowired
    public AuthenticationProvider daasTokenAuthenticationProvider(IdentityProvider identityProvider,
                                                                  TokenManager tokenManager) {
        TokenAuthenticationProvider result = new TokenAuthenticationProvider();
        result.setIdentityProvider(identityProvider);
        result.setTokenManager(tokenManager);
        return result;
    }

    @Bean
    @Autowired
    public AuthorizationProvider daasUrlAuthorizationProvider(AclProvider aclProvider) {
        DefaultUrlAuthorizationProvider result = new DefaultUrlAuthorizationProvider();
        result.getVoters().add(new AccessRequestRoleVoter());
        result.getVoters().add(new AccessRequestUserVoter());
        result.setProvider(aclProvider);
        return result;
    }

    @Bean
    @Autowired
    public TokenManager daasTokenManager(TokenProvider tokenProvider) {
        DefaultTokenManager tokenManager = new DefaultTokenManager();
        tokenManager.setTokenProvider(tokenProvider);
        tokenConfigurer.configure(tokenManager);
        return tokenManager;
    }

    @Bean
    public AclProvider daasUrlAclProvider() {
        UrlAclProviderBuilder urlAclProviderBuilder = UrlAclProviderBuilder.newInstance();
        tokenConfigurer.configure(urlAclProviderBuilder);
        return urlAclProviderBuilder.build();
    }

    @Bean
    public TokenProvider daasTokenProvider() {
        return new TokenProviderMemoryImpl();
    }

    @Bean
    public IdentityProvider daasIdentityProvider() {
        return new IdentityProviderMemoryImpl();
    }

    @Bean
    public MessageProvider messageProvider() {
        MessageProvider result = new DefaultMessageProvider();
        tokenConfigurer.configure(result);
        return result;
    }

    @Bean
    public FeatureConfigurer daasFeatureConfigurer() {
        FeatureConfigurer featureConfigurer = new FeatureConfigurer();
        tokenConfigurer.configure(featureConfigurer);
        return featureConfigurer;
    }

    @Bean
    @Autowired
    public LoginAttemptManager daasLoginAttemptManager(LoginAttemptProvider loginAttemptProvider) {
        DefaultLoginAttemptManager result = new DefaultLoginAttemptManager();
        result.setLoginAttemptProvider(loginAttemptProvider);
        tokenConfigurer.configure(result);
        return result;
    }

    @Bean
    public LoginAttemptProvider daasLoginAttemptProvider() {
        return new LoginAttemptProviderMemoryImpl();
    }

    @Bean
    @Autowired
    public CaptchaManager daasCaptchaManager(CaptchaProvider daasCaptchaProvider) {
        DefaultCaptchaManager result = new DefaultCaptchaManager();
        result.setCaptchaProvider(daasCaptchaProvider);
        tokenConfigurer.configure(result);
        return result;
    }

    @Bean
    public CaptchaProvider daasCaptchaProvider() {
        return new CaptchaProviderMemoryImpl();
    }

}
