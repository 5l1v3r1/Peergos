package peergos.shared.merklebtree;

import peergos.shared.cbor.*;
import peergos.shared.io.ipfs.cid.*;

import java.util.*;

public class HashCasPair implements Cborable {

    public final MaybeMultihash original;
    public final MaybeMultihash updated;

    public HashCasPair(MaybeMultihash original, MaybeMultihash updated) {
        if (original.equals(updated))
            throw new IllegalStateException("Tried to create a CAS pair with original == target!");
        this.original = original;
        this.updated = updated;
    }

    @Override
    public String toString() {
        return "(" + original.toString() + ", " + updated.toString() + ")";
    }

    @Override
    public CborObject toCbor() {
        return new CborObject.CborList(Arrays.asList(
                original.toCbor(),
                updated.toCbor()
        ));
    }

    public static HashCasPair fromCbor(CborObject cbor) {
        if (! (cbor instanceof CborObject.CborList))
            throw new IllegalStateException("Incorrect cbor for HashCasPair: " + cbor);

        List<? extends Cborable> value = ((CborObject.CborList) cbor).value;
        return new HashCasPair(MaybeMultihash.fromCbor(value.get(0)), MaybeMultihash.fromCbor(value.get(1)));
    }
}
