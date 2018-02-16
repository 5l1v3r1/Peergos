package peergos.shared.merklebtree;

import peergos.shared.cbor.*;
import peergos.shared.io.ipfs.multihash.*;

import java.io.*;
import java.util.*;

public class MerkleNode implements Cborable {
    public final byte[] data;
    public final List<Link> links;

    public MerkleNode(byte[] data, List<Link> links) {
        this.data = data;
        this.links = links;
        Collections.sort(this.links);
    }

    public MerkleNode(byte[] data) {
        this(data, Collections.emptyList());
    }

    public static class Link implements Comparable<Link> {
        public final String label;
        public final Multihash target;

        public Link(String label, Multihash target) {
            this.label = label;
            this.target = target;
        }

        @Override
        public int compareTo(Link link) {
            return label.compareTo(link.label);
        }
    }

    public MerkleNode addLink(String label, Multihash linkTarget) {
        List<Link> tmp = new ArrayList<>(links);
        tmp.add(new Link(label, linkTarget));
        return new MerkleNode(data, tmp);
    }

    public MerkleNode setData(byte[] newData) {
        return new MerkleNode(newData, links);
    }

    public CborObject.CborMap toCbor() {
        SortedMap<CborObject, CborObject> cbor = new TreeMap<>();
        cbor.put(new CborObject.CborString("Data"), new CborObject.CborByteArray(data));
        for (Link link: links) {
            cbor.put(new CborObject.CborString(link.label), new CborObject.CborMerkleLink((link.target)));
        }
        return new CborObject.CborMap(cbor);
    }

    public static MerkleNode fromCbor(CborObject obj) {
        CborObject.CborMap map = (CborObject.CborMap) obj;
        CborObject.CborString dataLabel = new CborObject.CborString("Data");
        CborObject.CborByteArray data = (CborObject.CborByteArray) map.values.get(dataLabel);
        List<Link> links = new ArrayList<>(map.values.size() - 1);
        for (Map.Entry<CborObject, ? extends Cborable> entry : map.values.entrySet()) {
            if (entry.getKey().equals(dataLabel))
                continue;
            String label = ((CborObject.CborString) entry.getKey()).value;
            Multihash value = ((CborObject.CborMerkleLink) entry.getValue()).target;
            links.add(new Link(label, value));
        }
        return new MerkleNode(data.value, links);
    }

    /**
     *
     * @param in a CBOR encoding of a merkle node
     * @return
     * @throws IOException for an invalid encoding
     */
    public static MerkleNode deserialize(byte[] in) {
        CborObject cbor = CborObject.fromByteArray(in);
        return fromCbor(cbor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MerkleNode that = (MerkleNode) o;

        if (!Arrays.equals(data, that.data)) return false;
        return links != null ? links.equals(that.links) : that.links == null;

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(data);
        result = 31 * result + (links != null ? links.hashCode() : 0);
        return result;
    }
}