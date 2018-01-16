package io.cloudonix.service;

import ch.loway.oss.ari4java.ARI;

public class Verb {

private String channelID;
private ARIty aRIty;
private ARI ari;

public Verb (String chanId , ARIty s, ARI a) {
	channelID = chanId;
	aRIty = s;
	ari = a;
}

public ARI getAri () {
	return ari;
}

public String getChanneLID () {
	return channelID;
}

public ARIty getService () {
	return aRIty;
}


}
