package io.cloudonix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.jitsi.impl.neomedia.device.AudioSilenceMediaDevice;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.utils.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ARItySipLayer implements SipListener {
	private String username;
	private SipStack sipStack;
	private SipFactory sipFactory;
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
	private SipProvider sipProvider;
	private Dialog dialog;
	private boolean byeTaskRunning;
	private Request ackRequest;
	private ClientTransaction inviteTid;
	private int port;
	private String ip;
	private ListeningPoint listeningPoint;
	private boolean active = true;
	private int remoteRtpAudioPort;
	private int remoteRtcpAudioPort;
	private int localRtpAudioPort;
	private int localRtcpAudioPort;
	private String remoteRtpAudioIp;
	private CompletableFuture<Void> waitForBye = new CompletableFuture<Void>();

	private final static Logger logger = LoggerFactory.getLogger(ARItySipInitiator.class);

	public ARItySipLayer(String username, String ip, int port) throws PeerUnavailableException,
			TransportNotSupportedException, InvalidArgumentException, TooManyListenersException, ObjectInUseException, FileNotFoundException {
		setUsername(username);
		this.port = port;
		this.ip = ip;
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		Properties properties = new Properties();
		logger.info("Starting stack: SipInitiator_" + Instant.now());
		properties.setProperty("javax.sip.STACK_NAME", "SipInitiator_" + Instant.now() + " with port: " + port);
		properties.setProperty("javax.sip.IP_ADDRESS", ip);

		sipStack = sipFactory.createSipStack(properties);
		headerFactory = sipFactory.createHeaderFactory();
		addressFactory = sipFactory.createAddressFactory();
		messageFactory = sipFactory.createMessageFactory();

		createRandomPort();

		logger.info("port is:" + port);
		logger.info("ip is:" + ip);

		listeningPoint = sipStack.createListeningPoint(ip, port, ListeningPoint.UDP);
		sipProvider = sipStack.createSipProvider(listeningPoint);
		sipProvider.addSipListener(this);
		setProperties();
	}

	public void close() {

	}

	private void setProperties() throws FileNotFoundException {
		InputStream in = new FileInputStream(new File("src/test/java/io/cloudonix/properties.txt"));

		Properties p = new Properties(System.getProperties());
		try {
			p.load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.setProperties(p);
		logger.info("Successfuly set properties");
	}

	public CompletableFuture<Void> sendInvite(String username, String address, String token)
			throws ParseException, InvalidArgumentException, SipException, SdpException, MalformedURLException {
		String host = address.replaceAll("localhost", "127.0.0.1");
		createRandomPort();
		SipURI requestURI = addressFactory.createSipURI(username, host);
		requestURI.setTransportParam("udp");

		Request request = messageFactory.createRequest(requestURI, Request.INVITE, sipProvider.getNewCallId(),
				headerFactory.createCSeqHeader(1L, Request.INVITE), configureFrom(), configureTo(username, host),
				new ArrayList<ViaHeader>(), headerFactory.createMaxForwardsHeader(70));

		SipURI contactURI = addressFactory.createSipURI(getUsername(), this.ip);
		contactURI.setPort(this.port);
		Address contactAddress = addressFactory.createAddress(contactURI);
		contactAddress.setDisplayName(getUsername());
		request.addHeader(headerFactory.createContactHeader(contactAddress));

		if (Objects.nonNull(token))
			request.addHeader(headerFactory.createHeader("X-Cloudonix-Session", token));
		request.addHeader(headerFactory.createHeader("X-Cloudonix-IP", host));
		request.addHeader(headerFactory.createHeader("X-Cloudonix-Port", String.valueOf(port)));

		SdpFactory sdp = SdpFactory.getInstance();
		SessionDescription session = sdp.createSessionDescription();
		session.setVersion(sdp.createVersion(0));
		session.setOrigin(sdp.createOrigin(username, 13760799956958020L, 13760799956958020L, "IN", "IP4", this.ip));
		session.setSessionName(sdp.createSessionName("mysession session"));
		session.setConnection(sdp.createConnection(this.ip));
		session.setTimeDescriptions(createVector(sdp.createTimeDescription()));
		session.setMediaDescriptions(
				createVector(sdp.createMediaDescription("audio", localRtpAudioPort, 1, "RTP/AVP", new int[] { 0, 8 })));
		session.setAttributes(createVector(sdp.createAttribute("rtpmap", "0 PCMU/8000"),
				sdp.createAttribute("rtpmap", "8 PCMA/8000"), sdp.createAttribute("ptime", "20")));

		request.setContent(session.toString().getBytes(), headerFactory.createContentTypeHeader("application", "sdp"));

		inviteTid = sipProvider.getNewClientTransaction(request);
		logger.info("Sending request: " + inviteTid.getRequest());
		inviteTid.sendRequest();
		dialog = inviteTid.getDialog();
		return waitForBye;
	}

	private void createRandomPort() {
		localRtpAudioPort = ThreadLocalRandom.current().nextInt(10000, 30001);
		if ((localRtpAudioPort % 2) != 0)
			localRtpAudioPort++;
		localRtcpAudioPort = localRtpAudioPort + 1;
	}

	private Vector<Object> createVector(Object... args) {
		return new Vector<Object>(Arrays.asList(args));
	}

	private void createRtpStack() throws SocketException, UnknownHostException {
		LibJitsi.start();
		AudioSilenceMediaDevice mutedDevice = new AudioSilenceMediaDevice();
		MediaService mediaService = LibJitsi.getMediaService();
		MediaStream audioMediaStream = mediaService.createMediaStream(mutedDevice);
		MediaFormat audioFormat = mediaService.getFormatFactory().createMediaFormat("PCMU");
		audioMediaStream.setFormat(audioFormat);
		audioMediaStream.setName(MediaType.AUDIO.toString());
		InetAddress remoteAddress = InetAddress.getByName(remoteRtpAudioIp);
		int count = 0;
		int maxTries = 10;
		while (true) {
			logger.info("localRtpAudioPort: " + localRtpAudioPort);
			logger.info("remoteRtpAudioPort: " + remoteRtpAudioPort);
			try {
				audioMediaStream
						.setTarget(new MediaStreamTarget(new InetSocketAddress(remoteAddress, remoteRtpAudioPort),
								new InetSocketAddress(remoteAddress, remoteRtcpAudioPort)));
				StreamConnector audioConnector = new DefaultStreamConnector(new DatagramSocket(localRtpAudioPort),
						new DatagramSocket(localRtcpAudioPort));
				audioMediaStream.setConnector(audioConnector);
				break;
			} catch (BindException e) {
				if (++count == maxTries) {
					logger.error("couldn't connect rtp beacause of", e);
					break;
				}
			}
		}
		audioMediaStream.start();
	}

	private FromHeader configureFrom() throws ParseException {
		SipURI from = addressFactory.createSipURI(getUsername(), this.ip + ":" + this.port);
		Address fromNameAddress = addressFactory.createAddress(from);
		fromNameAddress.setDisplayName(getUsername());
		return headerFactory.createFromHeader(fromNameAddress, "sipinitiator");
	}

	private ToHeader configureTo(String username, String address) throws ParseException {
		SipURI toAddress = addressFactory.createSipURI(username, address);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		toNameAddress.setDisplayName(username);
		return headerFactory.createToHeader(toNameAddress, null);
	}

	class ByeTask extends TimerTask {
		Dialog dialog;

		public ByeTask(Dialog dialog) {
			this.dialog = dialog;
		}

		public void run() {
			try {
				logger.info("ByeTask dialog: " + dialog);
				Request byeRequest = this.dialog.createRequest(Request.BYE);
				ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
				dialog.sendRequest(ct);
			} catch (Exception ex) {
				ex.printStackTrace();
			}

		}
	}

	public void processResponse(ResponseEvent evt) {
		logger.info("Got a response");
		Response response = (Response) evt.getResponse();
		ClientTransaction tid = evt.getClientTransaction();
		CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

		logger.info("Response received : Status Code = " + response.getStatusCode() + ", cseq = " + cseq);

		if (tid == null) {

			// RFC3261: MUST respond to every 2xx
			if (ackRequest != null && dialog != null) {
				logger.info("re-sending ACK");
				try {
					dialog.sendAck(ackRequest);
				} catch (SipException se) {
					se.printStackTrace();
				}
			}
			return;
		}
		// If the caller is supposed to send the bye
		if (!byeTaskRunning) {
			logger.info("sending bye in 30 sec");
			byeTaskRunning = true;
			new Timer().schedule(new ByeTask(dialog), 30000);
		}
		logger.info("transaction state is " + tid.getState());
		logger.info("Dialog = " + tid.getDialog());
		logger.info("Dialog State is " + tid.getDialog().getState());

		try {
			if (response.getStatusCode() == Response.OK) {
				if (cseq.getMethod().equals(Request.INVITE)) {

					logger.info("Dialog after 200 OK  " + dialog);
					logger.info("Dialog State after 200 OK  " + dialog.getState());

					String content = response.toString();
					remoteRtpAudioPort = Integer.parseInt(
							content.substring(content.indexOf("m=", 533) + 8, content.indexOf("m=", 533) + 8 + 5));
					remoteRtcpAudioPort = remoteRtpAudioPort + 1;
					logger.info("mediaPort: " + remoteRtpAudioPort);
					remoteRtpAudioIp = content.substring(content.indexOf("c=", 533) + 9,
							content.indexOf("c=", 533) + 9 + 13);
					logger.info("remoteIp: " + remoteRtpAudioIp);
					logger.info("Sending ACK");
					ackRequest = dialog.createAck(((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getSeqNumber());
					logger.info("ack request: " + ackRequest);
					dialog.sendAck(ackRequest);

					try {
						logger.info("Starting RTP stack");
						createRtpStack();
						logger.info("RTP stack sent from port: " + this.port);
					} catch (SocketException | UnknownHostException e) {
						logger.warn("RTP stack failed");
						e.printStackTrace();
					}
					Thread.sleep(300);

					// JvB: test REFER, reported bug in tag handling
					// dialog.sendRequest( sipProvider.getNewClientTransaction(
					// dialog.createRequest("REFER") ));

				} else if (cseq.getMethod().equals(Request.CANCEL)) {
					if (dialog.getState() == DialogState.CONFIRMED) {
						// oops cancel went in too late. Need to hang up the
						// dialog.
						logger.info("Sending BYE -- cancel went in too late !!");
						Request byeRequest = dialog.createRequest(Request.BYE);
						ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
						dialog.sendRequest(ct);

					}

				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void processRequest(RequestEvent requestReceivedEvent) {
		Request request = requestReceivedEvent.getRequest();
		ServerTransaction serverTransactionId = requestReceivedEvent.getServerTransaction();

		logger.info("\n\nRequest " + request.getMethod() + " received at " + sipStack.getStackName()
				+ " with server transaction id " + serverTransactionId);

		if (request.getMethod().equals(Request.BYE)) {
			processBye(request, serverTransactionId);
			waitForBye.complete(null);
		}

		else {
			try {
				serverTransactionId.sendResponse(messageFactory.createResponse(202, request));
			} catch (SipException e) {
				e.printStackTrace();
			} catch (InvalidArgumentException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

	}

	public void processBye(Request request, ServerTransaction serverTransactionId) {
		try {
			logger.info("SipInitiator:  got a bye .");
			if (serverTransactionId == null) {
				logger.info("SipInitiator:  null TID.");
				return;
			}
			Dialog dialog = serverTransactionId.getDialog();
			logger.info("Dialog State = " + dialog.getState());
			Response response = messageFactory.createResponse(200, request);
			serverTransactionId.sendResponse(response);
			logger.info("SipInitiator:  Sending OK.");
			logger.info("Dialog State = " + dialog.getState());

		} catch (Exception ex) {
			ex.printStackTrace();

		}
	}

	public void sendCancel() {
		try {
			System.out.println("Sending cancel");
			ClientTransaction ct = sipProvider.getNewClientTransaction(this.dialog.createRequest(Request.CANCEL));
			Request cancelRequest = ct.createCancel();
			ClientTransaction cancelTid = sipProvider.getNewClientTransaction(cancelRequest);
			cancelTid.sendRequest();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void processTimeout(TimeoutEvent evt) {
		logger.info("Transaction Time out");
	}

	public void processIOException(IOExceptionEvent evt) {
		logger.info("IOException happened");
	}

	public void processDialogTerminated(DialogTerminatedEvent evt) {
		logger.info("dialogTerminatedEvent");
		sipProvider.removeSipListener(this);
		try {
			sipStack.deleteSipProvider(sipProvider);
			sipStack.deleteListeningPoint(listeningPoint);
		} catch (ObjectInUseException e) {
			e.printStackTrace();
		}
		active = false;
	}

	public void processTransactionTerminated(TransactionTerminatedEvent evt) {
		logger.info("Transaction terminated event recieved");
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String newUsername) {
		username = newUsername;
	}

	public int getPort() {
		return port;
	}

	public boolean isActive() {
		return active;
	}

}
