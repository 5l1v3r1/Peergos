package peergos.shared.storage;

import peergos.shared.cbor.*;
import peergos.shared.crypto.asymmetric.*;
import peergos.shared.crypto.hash.*;
import peergos.shared.io.ipfs.cid.*;
import peergos.shared.io.ipfs.multiaddr.*;
import peergos.shared.io.ipfs.multihash.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

public class HashVerifyingStorage implements ContentAddressedStorage {

    private final ContentAddressedStorage source;

    public HashVerifyingStorage(ContentAddressedStorage source) {
        this.source = source;
    }

    private <T> T verify(byte[] data, Multihash claimed, Supplier<T> result) {
        switch (claimed.type) {
            case sha2_256:
                Multihash computed = new Multihash(Multihash.Type.sha2_256, Hash.sha256(data));
                if (claimed instanceof Cid)
                    computed = new Cid(((Cid) claimed).version, ((Cid) claimed).codec, computed);

                if (computed.equals(claimed))
                    return result.get();

                throw new IllegalStateException("Incorrect hash! Are you under attack? Expected: " + claimed + " actual: " + computed);
            default: throw new IllegalStateException("Unimplemented hash algorithm: " + claimed.type);
        }
    }

    @Override
    public CompletableFuture<Multihash> id() {
        return source.id();
    }

    @Override
    public CompletableFuture<List<Multihash>> put(PublicKeyHash writer, List<byte[]> signatures, List<byte[]> blocks) {
        return source.put(writer, signatures, blocks)
                .thenApply(hashes -> hashes.stream()
                        .map(h -> verify(blocks.get(hashes.indexOf(h)), h, () -> h))
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Optional<CborObject>> get(Multihash hash) {
        return source.get(hash)
                .thenApply(cborOpt -> cborOpt.map(cbor -> verify(cbor.toByteArray(), hash, () -> cbor)));
    }

    @Override
    public CompletableFuture<List<Multihash>> putRaw(PublicKeyHash writer, List<byte[]> signatures, List<byte[]> blocks) {
        return source.putRaw(writer, signatures, blocks)
                .thenApply(hashes -> hashes.stream()
                        .map(h -> verify(blocks.get(hashes.indexOf(h)), h, () -> h))
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getRaw(Multihash hash) {
        return source.getRaw(hash)
                .thenApply(arrOpt -> arrOpt.map(bytes -> verify(bytes, hash, () -> bytes)));
    }

    @Override
    public CompletableFuture<List<MultiAddress>> pinUpdate(Multihash existing, Multihash updated) {
        return source.pinUpdate(existing, updated);
    }

    @Override
    public CompletableFuture<List<Multihash>> recursivePin(Multihash h) {
        return source.recursivePin(h);
    }

    @Override
    public CompletableFuture<List<Multihash>> recursiveUnpin(Multihash h) {
        return source.recursiveUnpin(h);
    }

    @Override
    public CompletableFuture<List<Multihash>> getLinks(Multihash root) {
        return source.getLinks(root);
    }

    @Override
    public CompletableFuture<Optional<Integer>> getSize(Multihash block) {
        return source.getSize(block);
    }
}
