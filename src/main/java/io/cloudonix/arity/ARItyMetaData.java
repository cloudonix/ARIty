package io.cloudonix.arity;

/**
 * save meta data of ARIty connection
 * 
 * @author naamag
 *
 */
public class ARItyMetaData {

	private String uri;
	private String login;
	private String password;

	public ARItyMetaData(String uri, String login, String password) {
		this.uri = uri;
		this.login = login;
		this.password = password;
	}

	/**
	 * get the URL of the Asterisk web server that the application is connected to
	 * 
	 * @return
	 */
	public String getAppUri() {
		return uri;
	}

	/**
	 * get login
	 * @return
	 */
	public String getLogin() {
		return login;
	}

	/**
	 * get password
	 * @return
	 */
	public String getPassword() {
		return password;
	}
}
