package eu.socialsensor.framework;

public class Credentials {

	private String key;
	private String secret;
	
	private String accessToken;
	private String accessTokenSecret;
	
	private String clientId;
	
	public Credentials() {
		
	}
	
	public Credentials(String key, String secret, String accessToken, String accessTokenSecret) {
		
	}
	
	public void setKey(String key) {
		this.key = key;
	}
	
	public String getKey() {
		return key;
	}
	
	public void setSecret(String secret) {
		this.secret = secret;
	}
	
	public String getSecret() {
		return secret;
	}
	
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
	
	public String getAccessToken() {
		return accessToken;
	}
	
	public void setAccessTokenSecret(String accessTokenSecret) {
		this.accessTokenSecret = accessTokenSecret;
	}
	
	public String getAccessTokenSecret() {
		return accessTokenSecret;
	}
	
	public void setClientId(String clientId) {
		this.clientId = clientId;
	}
	
	public String getClientId() {
		return clientId;
	}
	
}
