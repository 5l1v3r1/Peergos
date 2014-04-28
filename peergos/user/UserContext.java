package peergos.user;

import akka.actor.ActorSystem;
import peergos.corenode.AbstractCoreNode;
import peergos.corenode.HTTPCoreNode;
import peergos.corenode.HTTPCoreNodeServer;
import peergos.crypto.User;
import peergos.storage.net.IP;
import peergos.user.fs.Chunk;
import peergos.user.fs.EncryptedChunk;
import peergos.user.fs.Fragment;
import peergos.user.fs.MetadataBlob;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.PublicKey;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class UserContext
{
    public static final int MAX_USERNAME_SIZE = 1024;
    public static final int MAX_KEY_SIZE = 1024;

    String username;
    User us;
    DHTUserAPI dht;
    AbstractCoreNode core;

    public UserContext(String username, User user, DHTUserAPI dht, AbstractCoreNode core)
    {
        this.username = username;
        this.us = user;
        this.dht = dht;
        this.core = core;
    }

    public boolean register()
    {
        byte[] signedHash = us.hashAndSignMessage(username.getBytes());
        return core.addUsername(username, us.getPublicKey(), signedHash);
    }

    public boolean checkRegistered()
    {
        String name = core.getUsername(us.getPublicKey());
        return name.equals(username);
    }

    public boolean addSharingKey(PublicKey pub)
    {
        byte[] signedHash = us.hashAndSignMessage(pub.getEncoded());
        return core.allowSharingKey(username, pub.getEncoded(), signedHash);
    }

    public Future uploadFragment(Fragment f, String targetUser, User sharer)
    {
        return dht.put(f.getHash(), f.getData(), targetUser, sharer.getPublicKey(), sharer.hashAndSignMessage(f.getHash()));
    }

    public MetadataBlob uploadChunk(byte[] raw, byte[] initVector, String target, User sharer)
    {
        Chunk chunk = new Chunk(raw);
        EncryptedChunk encryptedChunk = new EncryptedChunk(chunk.encrypt(initVector));
        Fragment[] fragments = encryptedChunk.generateFragments();

        FiniteDuration timeout = Duration.create(30, TimeUnit.SECONDS);
        for (Fragment f: fragments)
            try {
                Await.result(uploadFragment(f, target, sharer), timeout);
            } catch (Exception e) {e.printStackTrace();}
        return new MetadataBlob(fragments, initVector);
    }

    public static class Test
    {
        public Test() {}

        @org.junit.Test
        public void all() throws IOException
        {
            HTTPCoreNodeServer server = null;
            ActorSystem system = null;
            try {
                system = ActorSystem.create("UserRouter");

                // create a CoreNode API
                AbstractCoreNode mockCoreNode = new AbstractCoreNode() {
                    @Override
                    public void close() throws IOException {

                    }
                };
                server = new HTTPCoreNodeServer(mockCoreNode, IP.getMyPublicAddress(), AbstractCoreNode.PORT);
                server.start();
                URL coreURL = new URL("http://"+IP.getMyPublicAddress().getHostAddress()+":"+ AbstractCoreNode.PORT+"/");
                HTTPCoreNode clientCoreNode = new HTTPCoreNode(coreURL);

                // create a new us
                User us = User.random();
                String ourname = "USER";

                // create a DHT API
                DHTUserAPI dht = new HttpsUserAPI(new InetSocketAddress(IP.getMyPublicAddress(), 8000), system);

                UserContext context = new UserContext(ourname, us, dht, clientCoreNode);
                assertTrue("Not already registered", !context.checkRegistered());
                assertTrue("Register", context.register());

                User sharer = User.random();
                context.addSharingKey(sharer.getKey());

                uploadChunkTest(context, sharer);

            } finally
            {
                if (server != null)
                    server.close();
                system.shutdown();
            }
        }

        public void uploadChunkTest(UserContext context, User sharer)
        {
            Random r = new Random();
            byte[] initVector = new byte[EncryptedChunk.IV_SIZE];
            r.nextBytes(initVector);
            byte[] raw = new byte[Chunk.MAX_SIZE];
            byte[] contents = "Hello secure cloud! Goodbye NSA!".getBytes();
            for (int i=0; i < raw.length/32; i++)
                System.arraycopy(contents, 0, raw, 32*i, 32);

            MetadataBlob meta = context.uploadChunk(raw, initVector, context.username, sharer);
            // upload metadata to core node
            byte[] metablob = meta.serialize();

        }
    }
}