
package ug.daes.ra;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import jakarta.annotation.PostConstruct;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ug.daes.DAESService;
import ug.daes.PKICoreServiceException;
import ug.daes.Result;

@SpringBootApplication
public class RAApplication extends SpringBootServletInitializer {

	private static final Logger appLogger = LoggerFactory.getLogger(RAApplication.class);
	private static final String CLASS = "RAApplication";

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(RAApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(RAApplication.class, args);
		appLogger.info("{} :: Application started successfully", CLASS);
	}

	/**
	 * Configures RestTemplate with a custom SSL context to accept all certificates.
	 * Replaced generic Exception with GeneralSecurityException.
	 */
	@Bean
	public RestTemplate restTemplate() throws GeneralSecurityException {
		TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

		SSLContext sslContext = SSLContextBuilder.create()
				.loadTrustMaterial(null, acceptingTrustStrategy)
				.build();

		var tlsStrategy = new DefaultClientTlsStrategy(sslContext, NoopHostnameVerifier.INSTANCE);

		HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
				.setTlsSocketStrategy(tlsStrategy)
				.build();

		CloseableHttpClient httpClient = HttpClients.custom()
				.setConnectionManager(connectionManager)
				.build();

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		return new RestTemplate(requestFactory);
	}

	@Bean("jasyptStringEncryptor")
	public StringEncryptor stringEncryptor() {
		PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
		SimpleStringPBEConfig config = new SimpleStringPBEConfig();
		config.setPassword("$DttKycImplEngin@@r");
		config.setAlgorithm("PBEWithHMACSHA512AndAES_256");
		config.setKeyObtentionIterations("1000");
		config.setPoolSize("1");
		config.setProviderName("SunJCE");
		config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
		config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
		config.setStringOutputType("base64");
		encryptor.setConfig(config);
		return encryptor;
	}

	/**
	 * Handles the initialization of Native PKI utilities.
	 */
	@Component
	public static class SignatureServiceInitializer {

		private static final Logger logger = LoggerFactory.getLogger(SignatureServiceInitializer.class);

		@PostConstruct
		public void init() {
			try {
				Result result = DAESService.initPKINativeUtils();

				if (result.getStatus() == 0) {
					String message = result.getStatusMessage() != null ? new String(result.getStatusMessage()) : "OK";
					logger.info("PKI native utils started successfully: {}", message);
				} else {
					String error = result.getResponse() != null ? new String(result.getResponse()) : "No error message provided";
					logger.error("PKI native initialization failed with status {}: {}", result.getStatus(), error);


					throw new IllegalStateException("Critical failure: PKI native initialization returned non-zero status.");
				}
			} catch (PKICoreServiceException e) {
				throw new IllegalStateException("PKI Initialization Error: Failed to initialize native PKI core service during application startup sequence.", e);
			}
		}
	}
}