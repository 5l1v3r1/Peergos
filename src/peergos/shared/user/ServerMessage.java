package peergos.shared.user;

import jsinterop.annotations.*;
import peergos.shared.cbor.*;

import java.time.*;
import java.util.*;

@JsType
public class ServerMessage implements Comparable<ServerMessage>, Cborable {
    private static final Map<Integer, Type> byValue = new HashMap<>();
    public enum Type {
        FromServer(1),
        FromUser(2),
        Dismiss(3);

        public final int value;
        Type(int value) {
            this.value = value;
            byValue.put(value, this);
        }

        public static Type byValue(int val) {
            if (!byValue.containsKey(val))
                throw new IllegalStateException("Unknown server message type: " + val);
            return byValue.get(val);
        }
    }

    // id is unique to server. Allocated by server.
    // For replies/dismissals this is the id of the message being replied to.
    // For messages from user that aren't replies this is -1.
    @JsIgnore
    public final long id;
    public final Type type;
    @JsIgnore
    public final long sentEpochMillis;
    public final String contents;
    public final Optional<Long> replyToId;

    public ServerMessage(long id, Type type, long sentEpochMillis, String contents, Optional<Long> replyToId) {
        this.id = id;
        this.type = type;
        this.sentEpochMillis = sentEpochMillis;
        this.contents = contents;
        this.replyToId = replyToId;
    }

    @JsMethod
    public String id() {
        return Long.toString(id);
    }

    @JsMethod
    public String getContents() {
        return contents;
    }

    @JsMethod
    public LocalDateTime getSendTime() {
        return LocalDateTime.ofEpochSecond(sentEpochMillis/1000, (int)(sentEpochMillis % 1000)*1000, ZoneOffset.UTC);
    }

    @Override
    public int compareTo(ServerMessage other) {
        return Long.compare(sentEpochMillis, other.sentEpochMillis);
    }

    public static ServerMessage buildUserMessage(String body) {
        return new ServerMessage(-1, Type.FromUser, System.currentTimeMillis(), body, Optional.empty());
    }

    @Override
    public CborObject toCbor() {
        SortedMap<String, Cborable> state = new TreeMap<>();
        state.put("id", new CborObject.CborLong(id));
        state.put("s", new CborObject.CborLong(sentEpochMillis));
        state.put("t", new CborObject.CborLong(type.value));
        state.put("b", new CborObject.CborString(contents));
        replyToId.ifPresent(rid -> state.put("r", new CborObject.CborLong(rid)));
        return CborObject.CborMap.build(state);
    }

    public static ServerMessage fromCbor(Cborable cbor) {
        if (! (cbor instanceof CborObject.CborMap))
            throw new IllegalStateException("Invalid cbor for ServerMessage! " + cbor);
        CborObject.CborMap m = (CborObject.CborMap) cbor;
        long id = m.getLong("id");
        long sentMillis = m.getLong("s");
        Type type = Type.byValue((int)m.getLong("t"));
        String contents = m.getString("b");
        Optional<Long> replyToId = m.getOptionalLong("r");
        return new ServerMessage(id, type, sentMillis, contents, replyToId);
    }
}
