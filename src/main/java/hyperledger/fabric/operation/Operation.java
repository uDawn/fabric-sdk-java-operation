package hyperledger.fabric.operation;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import hyperledger.fabric.beans.KeyHistory;
import hyperledger.fabric.beans.KeyModifications;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.util.internal.StringUtil;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.Chaincode;
import org.hyperledger.fabric.protos.peer.FabricProposal;
import org.hyperledger.fabric.protos.peer.FabricTransaction;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

public class Operation {
    private Log logger = LogFactory.getLog(Operation.class);
    private Collection<SampleOrg> testSampleOrgs;
    private final TestConfig testConfig;
    private final Properties opProperties;
    private final String CHANNEL_NAME;

    private final String ADMIN_NAME = "admin";
    private final String USER_1_NAME = "ddd";
    //private final String FIXTURES_PATH = "src/test/fixture";

    private final String CHAIN_CODE_NAME;
    private final String CHAIN_CODE_PATH;
    private final String CHAIN_CODE_VERSION;

    private HFClient client;
    private SampleOrg sampleOrg;
    private Channel myChannel;
    private ChaincodeID chaincodeID;

    public Operation() {
        this.testConfig = TestConfig.getConfig();
        this.opProperties = this.testConfig.getOperationProperties();
        this.testSampleOrgs = this.testConfig.getIntegrationTestsSampleOrgs();
        this.CHANNEL_NAME = this.opProperties.getProperty("hyperledger.fabric.operation.channelname");
        this.CHAIN_CODE_NAME = this.opProperties.getProperty("hyperledger.fabric.operation.chaincodename");
        this.CHAIN_CODE_PATH = this.opProperties.getProperty("hyperledger.fabric.operation.chaincodepath");
        this.CHAIN_CODE_VERSION = this.opProperties.getProperty("hyperledger.fabric.operation.chaincodeversion");
    }
    //CHECKSTYLE.ON: Method length is 320 lines (max allowed is 150).

    public void constructSetup() throws Exception {
        logger.info(String.format("Construct setup."));

        // this.myChannel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        // this.myChannel.setDeployWaitTime(testConfig.getDeployWaitTime());
        //Set up hfca for each sample org
        try {

            for (SampleOrg sampleOrg : testSampleOrgs) {
                sampleOrg.setCAClient(HFCAClient.createNewInstance(sampleOrg.getCALocation(), sampleOrg.getCAProperties()));
            }

            this.client = HFClient.createNewInstance();
            this.client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
            //Set up USERS

            //Persistence is not part of SDK. Sample file store is for demonstration purposes only!
            //   MUST be replaced with more robust application implementation  (Database, LDAP)
            File sampleStoreFile = new File(this.opProperties.getProperty("hyperledger.fabric.operation.HFCpath"));
            if (sampleStoreFile.exists()) { //For testing start fresh
                sampleStoreFile.delete();
            }

            final SampleStore sampleStore = new SampleStore(sampleStoreFile);
            //  sampleStoreFile.deleteOnExit();

            //SampleUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface

            ////////////////////////////
            // get users for all orgs

            for (SampleOrg sampleOrg : testSampleOrgs) {

                HFCAClient ca = sampleOrg.getCAClient();
                final String orgName = sampleOrg.getName();
                final String mspid = sampleOrg.getMSPID();
                ca.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
                SampleUser admin = sampleStore.getMember(ADMIN_NAME, orgName);
                if (!admin.isEnrolled()) {  //Preregistered admin only needs to be enrolled with Fabric caClient.
                    admin.setEnrollment(ca.enroll(admin.getName(), "adminpw"));
                    admin.setMspId(mspid);
                }

                sampleOrg.setAdmin(admin); // The admin of this org --

                SampleUser user = sampleStore.getMember(USER_1_NAME, sampleOrg.getName());
                if (!user.isRegistered()) {  // users need to be registered AND enrolled
                    RegistrationRequest rr = new RegistrationRequest(user.getName(), "org1.department1");
                    //String tmp = ca.register(rr, admin);
                    user.setEnrollmentSecret(ca.register(rr, admin));
                    //user.setEnrollmentSecret(tmp);
                    //File secret = new File("src/secret.txt");
                    //FileOutputStream write_secret = new FileOutputStream(secret, false);
                    //write_secret.write(tmp.getBytes());
                }
                if (!user.isEnrolled()) {
                    user.setEnrollment(ca.enroll(user.getName(), user.getEnrollmentSecret()));
                    user.setMspId(mspid);
                }
                sampleOrg.addUser(user); //Remember user belongs to this Org

                final String sampleOrgName = sampleOrg.getName();
                final String sampleOrgDomainName = sampleOrg.getDomainName();

                //src/test/fixture/sdkintegration/e2e-2Orgs/channel/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp/keystore/

                SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                        Util.findFileSk(Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/",
                                sampleOrgDomainName, format("/users/Admin@%s/msp/keystore", sampleOrgDomainName)).toFile()),
                        Paths.get(testConfig.getTestChannelPath(), "crypto-config/peerOrganizations/", sampleOrgDomainName,
                                format("/users/Admin@%s/msp/signcerts/Admin@%s-cert.pem", sampleOrgDomainName, sampleOrgDomainName)).toFile());


                //sampleOrg.setPeerAdmin(sampleStore.getMember(orgName + "Admin", orgName));
                sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode
            }
            ////////////////////////////
            //Construct and run the channels
            this.sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
            this.chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();

            this.myChannel = constructChannel(this.CHANNEL_NAME, this.client, this.sampleOrg);
            this.installChaincode(this.client, this.myChannel, this.sampleOrg);
            this.instantiateChaincode(this.client, this.myChannel, this.sampleOrg);
            //this.myChannel = reconstructChannel(this.CHANNEL_NAME, this.client, this.sampleOrg);
            logger.info(String.format("Construct end."));
        } catch (Exception e) {
            logger.error(String.format("%s", e.getMessage()));
            throw e;
            //e.printStackTrace();
        }
    }

    private Channel constructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {
        ////////////////////////////
        //Construct the channel
        //

        logger.info(String.format("Running onstruct channel."));

        //Only peer Admin org
        client.setUserContext(sampleOrg.getPeerAdmin());

        Collection<Orderer> orderers = new LinkedList<>();

        for (String orderName : sampleOrg.getOrdererNames()) {

            Properties ordererProperties = testConfig.getOrdererProperties(orderName);

            //example of setting keepAlive to avoid timeouts on inactive http2 connections.
            // Under 5 minutes would require changes to server side to accept faster ping rates.
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});

            orderers.add(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
                    ordererProperties));
        }

        //Just pick the first orderer in the list to create the channel.

        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);

        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(this.opProperties.getProperty("hyperledger.fabric.operation.channelfilepath")));

        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        Channel newChannel = client.newChannel(name, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, sampleOrg.getPeerAdmin()));

        logger.info(String.format("Created channel %s.", name));

        for (String peerName : sampleOrg.getPeerNames()) {
            String peerLocation = sampleOrg.getPeerLocation(peerName);

            Properties peerProperties = testConfig.getPeerProperties(peerName); //test properties for peer.. if any.
            if (peerProperties == null) {
                peerProperties = new Properties();
            }
            //Example of setting specific options on grpc's NettyChannelBuilder
            peerProperties.put("grpc.NettyChannelBuilderOption.maxInboundMessageSize", 9000000);

            Peer peer = client.newPeer(peerName, peerLocation, peerProperties);
            newChannel.joinPeer(peer);
            logger.info(String.format("Peer %s joined channel %s", peerName, name));
            sampleOrg.addPeer(peer);
        }

        for (Orderer orderer : orderers) { //add remaining orderers if any.
            newChannel.addOrderer(orderer);
            logger.info(String.format("Orderer %s joined channel %s", orderer.getName(), name));
        }

        for (String eventHubName : sampleOrg.getEventHubNames()) {

            final Properties eventHubProperties = testConfig.getEventHubProperties(eventHubName);

            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
            eventHubProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});

            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    eventHubProperties);
            newChannel.addEventHub(eventHub);
        }

        newChannel.initialize();

        logger.info(String.format("Finished initialization channel %s", name));

        return newChannel;

    }

    private void installChaincode(HFClient client, Channel channel, SampleOrg sampleOrg) throws Exception {
        try {

            final String channelName = channel.getName();
            logger.info(String.format("Install chaincode for channel %s", channelName));
            channel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
            channel.setDeployWaitTime(testConfig.getDeployWaitTime());

            Collection<Peer> channelPeers = channel.getPeers();
            Collection<Orderer> orderers = channel.getOrderers();
            final ChaincodeID chaincodeID;
            Collection<ProposalResponse> responses;
            Collection<ProposalResponse> successful = new LinkedList<>();
            Collection<ProposalResponse> failed = new LinkedList<>();

            chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();

            ////////////////////////////
            // Install Proposal Request
            //

            client.setUserContext(sampleOrg.getPeerAdmin());

            logger.info("Creating install proposal");

            InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
            installProposalRequest.setChaincodeID(chaincodeID);


            // on foo chain install from directory.

            ////For GO language and serving just a single user, chaincodeSource is mostly likely the users GOPATH
            installProposalRequest.setChaincodeSourceLocation(new File(this.opProperties.getProperty("hyperledger.fabric.operation.chaincodefilelocation")));

            installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

            logger.info("Sending install proposal");

            ////////////////////////////
            // only a client from the same org as the peer can issue an install request
            int numInstallProposal = 0;
            //    Set<String> orgs = orgPeers.keySet();
            //   for (SampleOrg org : testSampleOrgs) {

            Set<Peer> peersFromOrg = sampleOrg.getPeers();
            numInstallProposal = numInstallProposal + peersFromOrg.size();
            responses = client.sendInstallProposal(installProposalRequest, peersFromOrg);

            for (ProposalResponse response : responses) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    logger.info(String.format("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName()));
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            SDKUtils.getProposalConsistencySets(responses);
            //   }
            logger.info(String.format("Received %d install proposal responses. Successful+verified: %d . Failed: %d", numInstallProposal, successful.size(), failed.size()));

            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
                fail("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
            }

            logger.info("Install chaincode successful.");
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }
    }

    private void instantiateChaincode(HFClient client, Channel channel, SampleOrg sampleOrg) throws Exception {
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        Collection<ProposalResponse> responses;
        Collection<Orderer> orderers = channel.getOrderers();

        try {
            logger.info(String.format("Instantiate chaincode for channel %s.", channel.getName()));
            InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
            instantiateProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            instantiateProposalRequest.setChaincodeID(chaincodeID);
            instantiateProposalRequest.setFcn("init");
            instantiateProposalRequest.setArgs(new String[]{"a", "500.5", "b", "200.5"});
            Map<String, byte[]> tm = new HashMap<>();
            tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
            tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
            instantiateProposalRequest.setTransientMap(tm);

            /*
              policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from someone in either Org1 or Org2
              See README.md Chaincode endorsement policies section for more details.
            */
            ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
            chaincodeEndorsementPolicy.fromYamlFile(new File(this.opProperties.getProperty("hyperledger.fabric.operation.endorsementpolicylocation")));
            instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

            logger.info("Sending instantiateProposalRequest to all peers with arguments: a and b set to 500 and 200 respectively");
            successful.clear();
            failed.clear();

            //Send responses both ways with specifying peers and by using those on the channel.
            responses = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());

            for (ProposalResponse response : responses) {
                if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    successful.add(response);
                    logger.info(String.format("Succesful instantiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName()));
                } else {
                    failed.add(response);
                }
            }
            logger.info(String.format("Received %d instantiate proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size()));
            if (failed.size() > 0) {
                ProposalResponse first = failed.iterator().next();
                logger.error("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
                fail("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified());
            }

            channel.sendTransaction(successful, orderers).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);
            logger.info("Instantiate end.");
        } catch (Exception e) {
            throw e;
        }

    }

    public void setup() throws Throwable {

        try {
            logger.info(String.format("Reconstruct setup."));

            this.client = HFClient.createNewInstance();

            this.client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            // client.setMemberServices(peerOrg1FabricCA);

            ////////////////////////////
            //Set up USERS

            //Persistence is not part of SDK. Sample file store is for demonstration purposes only!
            //   MUST be replaced with more robust application implementation  (Database, LDAP)
            File sampleStoreFile = new File(this.opProperties.getProperty("hyperledger.fabric.operation.HFCpath"));

            final SampleStore sampleStore = new SampleStore(sampleStoreFile);

            //SampleUser can be any implementation that implements org.hyperledger.fabric.sdk.User Interface

            ////////////////////////////
            // get users for all orgs

            for (SampleOrg sampleOrg : this.testSampleOrgs) {

                final String orgName = sampleOrg.getName();

                SampleUser admin = sampleStore.getMember(this.ADMIN_NAME, orgName);
                sampleOrg.setAdmin(admin); // The admin of this org.

                // No need to enroll or register all done in End2endIt !
                SampleUser user = sampleStore.getMember(this.USER_1_NAME, orgName);
                sampleOrg.addUser(user);  //Remember user belongs to this Org

                sampleOrg.setPeerAdmin(sampleStore.getMember(orgName + "Admin", orgName));
            }

            this.sampleOrg = testConfig.getIntegrationTestsSampleOrg("peerOrg1");
            this.chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME)
                    .setVersion(CHAIN_CODE_VERSION)
                    .setPath(CHAIN_CODE_PATH).build();

            this.myChannel = reconstructChannel(this.CHANNEL_NAME, this.client, this.sampleOrg);
            logger.info(String.format("Reconstruct end."));
        } catch (Exception e) {
            logger.error(String.format("%s", e.getMessage()));
            throw e;
            //e.printStackTrace();
            //fail(e.getMessage());
        }

    }

    private Channel reconstructChannel(String name, HFClient client, SampleOrg sampleOrg) throws Exception {

        try {
            logger.info("Running reconstruct channel");
            client.setUserContext(sampleOrg.getPeerAdmin());
            Channel newChannel = client.newChannel(name);

            for (String orderName : sampleOrg.getOrdererNames()) {
                newChannel.addOrderer(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
                        testConfig.getOrdererProperties(orderName)));
            }

            for (String peerName : sampleOrg.getPeerNames()) {
                String peerLocation = sampleOrg.getPeerLocation(peerName);
                Peer peer = client.newPeer(peerName, peerLocation, testConfig.getPeerProperties(peerName));

                //Query the actual peer for which channels it belongs to and check it belongs to this channel
                Set<String> channels = client.queryChannels(peer);
                if (!channels.contains(name)) {
                    //throw new AssertionError(format("Peer %s does not appear to belong to channel %s", peerName, name));
                }

                newChannel.addPeer(peer);
                sampleOrg.addPeer(peer);
            }

            for (String eventHubName : sampleOrg.getEventHubNames()) {
                EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                        testConfig.getEventHubProperties(eventHubName));
                newChannel.addEventHub(eventHub);
            }

            newChannel.initialize();

            //Just see if we can get channelConfiguration. Not required for the rest of scenario but should work.
        /*final byte[] channelConfigurationBytes = newChannel.getChannelConfigurationBytes();
        Configtx.Config channelConfig = Configtx.Config.parseFrom(channelConfigurationBytes);
        assertNotNull(channelConfig);
        Configtx.ConfigGroup channelGroup = channelConfig.getChannelGroup();
        assertNotNull(channelGroup);
        Map<String, Configtx.ConfigGroup> groupsMap = channelGroup.getGroupsMap();
        assertNotNull(groupsMap.get("Orderer"));
        assertNotNull(groupsMap.get("Application"));*/

            //Before return lets see if we have the chaincode on the peers that we expect from End2endIT
            //And if they were instantiated too.

            for (Peer peer : newChannel.getPeers()) {

                if (!checkInstalledChaincode(client, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                    logger.error(String.format(("Peer %s is missing chaincode name: %s, path:%s, version: %s"),
                            peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
                    throw new AssertionError(format("Peer %s is missing chaincode name: %s, path:%s, version: %s",
                            peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
                    //this.installChaincode(client, newChannel, sampleOrg);
                    //this.instantiateChaincode(client, newChannel, sampleOrg);
                }

                if (!checkInstantiatedChaincode(newChannel, peer, CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_VERSION)) {
                    logger.error(String.format(("Peer %s is missing instantiated chaincode name: %s, path:%s, version: %s"),
                            peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
                    throw new AssertionError(format("Peer %s is missing instantiated chaincode name: %s, path:%s, version: %s",
                            peer.getName(), CHAIN_CODE_NAME, CHAIN_CODE_PATH, CHAIN_CODE_PATH));
                }

            }
            logger.info("End reconstruct");
            return newChannel;
        } catch (Exception e) {
            throw e;
        }
    }

    private static void out(String format, Object... args) {

        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }

    private boolean checkInstalledChaincode(HFClient client, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {

        logger.info(String.format("Checking installed chaincode: %s, at version: %s, on peer: %s", ccName, ccVersion, peer.getName()));
        List<Query.ChaincodeInfo> ccinfoList = client.queryInstalledChaincodes(peer);

        boolean found = false;

        for (Query.ChaincodeInfo ccifo : ccinfoList) {

            found = ccName.equals(ccifo.getName()) && ccPath.equals(ccifo.getPath()) && ccVersion.equals(ccifo.getVersion());
            if (found) {
                break;
            }

        }

        return found;
    }

    private boolean checkInstantiatedChaincode(Channel channel, Peer peer, String ccName, String ccPath, String ccVersion) throws InvalidArgumentException, ProposalException {
        logger.info(String.format("Checking instantiated chaincode: %s, at version: %s, on peer: %s", ccName, ccVersion, peer.getName()));
        List<Query.ChaincodeInfo> ccinfoList = channel.queryInstantiatedChaincodes(peer);

        boolean found = false;

        for (Query.ChaincodeInfo ccifo : ccinfoList) {
            found = ccName.equals(ccifo.getName()) && ccPath.equals(ccifo.getPath()) && ccVersion.equals(ccifo.getVersion());
            if (found) {
                break;
            }

        }

        return found;
    }

    public KeyHistory queryHistoryByAccount(String accName) {
        KeyHistory history = new KeyHistory();
        this.myChannel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        this.myChannel.setDeployWaitTime(testConfig.getDeployWaitTime());
        final String channelName = this.myChannel.getName();
        String chaincodeHistoryJson;

        logger.info(String.format("Running queryHistory of account %s on channel %s.", accName, channelName));
        try {

            ////////////////////////////
            // Send Query Proposal to all peers
            //
            // String expect = "300";
            logger.info(String.format("Now query chaincode for the history of %s.", accName));
            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            queryByChaincodeRequest.setArgs(new String[]{accName});
            queryByChaincodeRequest.setFcn("queryHistory");
            queryByChaincodeRequest.setChaincodeID(chaincodeID);

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
            queryByChaincodeRequest.setTransientMap(tm2);

            Collection<ProposalResponse> queryProposals = this.myChannel.queryByChaincode(queryByChaincodeRequest, this.myChannel.getPeers());
            for (ProposalResponse proposalResponse : queryProposals) {
                if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                    logger.error("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                            ". Messages: " + proposalResponse.getMessage()
                            + ". Was verified : " + proposalResponse.isVerified());
                    fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                            ". Messages: " + proposalResponse.getMessage()
                            + ". Was verified : " + proposalResponse.isVerified());
                } else {
                    byte[] payloadByteArr = proposalResponse.getProposalResponse().getResponse().getPayload().toByteArray();
                    chaincodeHistoryJson = new String(payloadByteArr);
                    Gson gson = new Gson();
                    KeyModifications keyModi = gson.fromJson(chaincodeHistoryJson, KeyModifications.class);
                    for (KeyModifications.ModicationItem modicationItem : keyModi.getHistory()) {
                        KeyHistory.KeyHistoryItem historyItem = new KeyHistory.KeyHistoryItem();
                        historyItem.setTimeStamp(modicationItem.getTimestamp());
                        historyItem.setTxID(modicationItem.getTxId());
                        historyItem.setCurrentValue(Double.parseDouble(modicationItem.getValue()));


                        // TODO: Get the payload of Tx and then complete the rest fields of historyItem
                        TransactionInfo mTxInfo = this.myChannel.queryTransactionByID(modicationItem.getTxId());

                        FabricTransaction.Transaction pTx = FabricTransaction.
                                Transaction.parseFrom(mTxInfo.getProcessedTransaction().toByteArray());


                        logger.info("TxInfo");


                        /*
                        UniversalDetector detector = new UniversalDetector(null);
                        detector.handleData(payload, 0, payload.length);
                        detector.dataEnd();
                        */

                        logger.info(pTx.getActions(0).getPayload());
                        Common.Payload pay = Common.Payload.parseFrom(mTxInfo.getEnvelope().getPayload());

                        /*
                        Map<Descriptors.FieldDescriptor, Object> fields = pay.getAllFields();
                        for (Descriptors.FieldDescriptor key : fields.keySet()) {
                            logger.info("key: " + key.getFullName());
                            if (key.getType() == Descriptors.FieldDescriptor.Type.BYTES) {
                                logger.info("value: " + ((ByteString) fields.get(key)));
                            }

                        }
                        */

                        history.addItem(historyItem);
                    }
                    logger.info(String.format("Query key modification history of %s from peer %s returned %s", accName, proposalResponse.getPeer().getName(), chaincodeHistoryJson));
                }
            }
            logger.info(String.format("End queryHistory of account %s on channel %s.", accName, channelName));
            return history;
        } catch (InvalidArgumentException | InvalidProtocolBufferException | ProposalException e) {
            e.printStackTrace();
        }
        return history;
    }

    public boolean transfer(String account_1, String account_2, String amount) {
        String tmp_account_1 = account_1;
        String tmp_account_2 = account_2;
        String tmp_amount = amount;
        final String channelName = this.myChannel.getName();

        logger.info(String.format("Running transfer on channel %s", channelName));

        this.myChannel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        this.myChannel.setDeployWaitTime(testConfig.getDeployWaitTime());
        Collection<Peer> channelPeers = this.myChannel.getPeers();
        Collection<Orderer> orderers = this.myChannel.getOrderers();

        //Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        //responses = this.myChannel.sendTransactionProposal().sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());

        //this.myChannel.sendTransaction(successful, orderers).thenApply(transactionEvent -> {

        try {

            this.client.setUserContext(this.sampleOrg.getUser(USER_1_NAME));

            ///////////////
            /// Send transaction proposal to all peers
            TransactionProposalRequest transactionProposalRequest = this.client.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeID(this.chaincodeID);
            transactionProposalRequest.setFcn("transfer");
            transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            transactionProposalRequest.setArgs(new String[]{tmp_account_1, tmp_account_2, tmp_amount});

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
            tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
            transactionProposalRequest.setTransientMap(tm2);

            logger.info(String.format("sending transactionProposal to all peers with arguments: transfer(%s,%s,%s)", tmp_account_1, tmp_account_2, tmp_amount));

            Collection<ProposalResponse> transactionPropResp = this.myChannel.sendTransactionProposal(transactionProposalRequest, this.myChannel.getPeers());
            for (ProposalResponse response : transactionPropResp) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    logger.info(String.format("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName()));
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
            if (proposalConsistencySets.size() != 1) {
                fail(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
            }

            logger.info(String.format("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                    transactionPropResp.size(), successful.size(), failed.size()));
            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                logger.error(String.format("Not enough endorsers for invoke(transfer %s,%s,%s):", tmp_account_1, tmp_account_2, tmp_amount) + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
                fail(String.format("Not enough endorsers for invoke(transfer %s,%s,%s):", tmp_account_1, tmp_account_2, tmp_amount) + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
            }
            logger.info("Successfully received transaction proposal responses.");
            ////////////////////////////
            // Send Transaction Transaction to orderer
            logger.info(String.format("Sending chaincode transaction(transfer %s,%s,%s) to orderer.", tmp_account_1, tmp_account_2, tmp_amount));
            this.myChannel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);
            logger.info(String.format("End transfer on channel %s.", channelName));
            return true;
        } catch (Exception e) {
            logger.error(String.format("Caught an error while invoking chaincode , %s.", e.getMessage()));
            //e.printStackTrace();
            fail("Failed invoking chaincode with error : " + e.getMessage());
            return false;
        }

        //return null;

        //});

        //return true;
    }

    public String query(String account) {
        this.myChannel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        this.myChannel.setDeployWaitTime(testConfig.getDeployWaitTime());
        final String channelName = this.myChannel.getName();
        String tmp_amount = null;

        logger.info(String.format("Running query on channel %s.", channelName));
        //this.myChannel.sendTransaction(successful, orderers).thenApply(transactionEvent -> {
        try {
            //testTxID = transactionEvent.getTransactionID(); // used in the channel queries later

            ////////////////////////////
            // Send Query Proposal to all peers
            //
            // String expect = "300";
            logger.info(String.format("Now query chaincode for the value of %s.", account));
            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            queryByChaincodeRequest.setArgs(new String[]{account});
            queryByChaincodeRequest.setFcn("query");
            queryByChaincodeRequest.setChaincodeID(chaincodeID);

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
            queryByChaincodeRequest.setTransientMap(tm2);

            Collection<ProposalResponse> queryProposals = this.myChannel.queryByChaincode(queryByChaincodeRequest, this.myChannel.getPeers());
            for (ProposalResponse proposalResponse : queryProposals) {
                if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                    logger.error("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                            ". Messages: " + proposalResponse.getMessage()
                            + ". Was verified : " + proposalResponse.isVerified());
                    fail("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                            ". Messages: " + proposalResponse.getMessage()
                            + ". Was verified : " + proposalResponse.isVerified());
                } else {
                    byte[] payloadByteArr = proposalResponse.getProposalResponse().getResponse().getPayload().toByteArray();
                    double payload = ByteBuffer.wrap(payloadByteArr).getDouble();
                    //String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    tmp_amount = Double.toString(payload);
                    logger.info(String.format("Query payload of %s from peer %s returned %s", account, proposalResponse.getPeer().getName(), payload));
                }
            }
            logger.info(String.format("End query on channel %s.", channelName));
            return tmp_amount;
        } catch (Exception e) {
            logger.error(String.format("Caught error while running query , %s.", e.getMessage()));
            fail("Failed during chaincode query with error : " + e.getMessage());
            return null;
        }
    }

    public boolean initiate(String account, String amount) {
        String tmp_account = account;
        String tmp_amount = amount;

        final String channelName = this.myChannel.getName();

        logger.info(String.format("Running initiate on channel %s.", channelName));

        this.myChannel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        this.myChannel.setDeployWaitTime(testConfig.getDeployWaitTime());

        //Collection<ProposalResponse> responses;
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        //responses = this.myChannel.sendTransactionProposal().sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());

        try {

            this.client.setUserContext(this.sampleOrg.getUser(USER_1_NAME));

            ///////////////
            /// Send transaction proposal to all peers
            TransactionProposalRequest transactionProposalRequest = this.client.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeID(this.chaincodeID);
            transactionProposalRequest.setFcn("give");
            transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            transactionProposalRequest.setArgs(new String[]{tmp_account, tmp_amount});

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
            tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
            transactionProposalRequest.setTransientMap(tm2);

            logger.info(String.format("Sending initiate proposal to all peers with arguments: give(%s,%s)", tmp_account, tmp_amount));

            Collection<ProposalResponse> transactionPropResp = this.myChannel.sendTransactionProposal(transactionProposalRequest, this.myChannel.getPeers());
            for (ProposalResponse response : transactionPropResp) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    logger.info(String.format("Successful initiate proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName()));
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
            if (proposalConsistencySets.size() != 1) {
                logger.error(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
                fail(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
            }

            logger.info(String.format("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
                    transactionPropResp.size(), successful.size(), failed.size()));
            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                logger.error(String.format("Not enough endorsers for invoke(give %s,%s):", tmp_account, tmp_amount) + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
                fail(String.format("Not enough endorsers for invoke(give %s,%s):", tmp_account, tmp_amount) + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
            }
            logger.info("Successfully received transaction proposal responses.");

            /*
            ProposalResponse resp = transactionPropResp.iterator().next();
            byte[] x = resp.getChaincodeActionResponsePayload(); // This is the data returned by the chaincode.
            String resultAsString = null;
            if (x != null) {
                resultAsString = new String(x, "UTF-8");
            }
            //assertEquals(":)", resultAsString);

            assertEquals(200, resp.getChaincodeActionResponseStatus()); //Chaincode's status.

            TxReadWriteSetInfo readWriteSetInfo = resp.getChaincodeActionResponseReadWriteSetInfo();
            //See blockwalker below how to transverse this
            assertNotNull(readWriteSetInfo);
            assertTrue(readWriteSetInfo.getNsRwsetCount() > 0);

            ChaincodeID cid = resp.getChaincodeID();
            assertNotNull(cid);
            //assertEquals(CHAIN_CODE_PATH, cid.getPath());
            assertEquals(CHAIN_CODE_NAME, cid.getName());
            assertEquals(CHAIN_CODE_VERSION, cid.getVersion());
            */
            ////////////////////////////
            // Send Transaction Transaction to orderer
            logger.info(String.format("Sending chaincode transaction(give %s,%s) to orderer.", tmp_account, tmp_amount));
            this.myChannel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);
            logger.info("End initiate");
            return true;
        } catch (Exception e) {
            logger.error(String.format("Caught an error while invoking chaincode , %s.", e.getMessage()));
            fail("Failed invoking chaincode with error : " + e.getMessage());
            return false;
        }
    }

}
