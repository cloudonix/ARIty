package io.cloudonix.service;

import ch.loway.oss.ari4java.ARI;

public class Verb {

private String channelID;
private Service service;
private ARI ari;

public Verb (String chanId , Service s, ARI a) {
	channelID = chanId;
	service = s;
	ari = a;
}

public ARI getAri () {
	return ari;
}

public String getChanneLID () {
	return channelID;
}

public Service getService () {
	return service;
}


}
