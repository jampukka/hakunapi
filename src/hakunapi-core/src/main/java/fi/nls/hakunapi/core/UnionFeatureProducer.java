package fi.nls.hakunapi.core;

import java.util.List;

import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;

/**
 * A {@link FeatureProducer} that overlays a "branch" producer onto a "master" producer with
 * UNION-distinct-by-id semantics: the branch wins on an id collision. See
 * {@code docs/design/union-overlay.md}.
 *
 * <p>The participating feature types share one schema (identical {@link HakunaProperty} instances,
 * same id, same order), so this producer just fans the incoming request/collection to each child
 * producer — re-bound to that child's own {@link FeatureType} via
 * {@link GetFeatureCollection#withFt} — and merges the two ordered streams with
 * {@link MergeByIdFeatureStream}. All ordering, filtering and cursor paging have already been baked
 * into the incoming collection by the engine; this producer adds no query logic of its own.
 *
 * <p>{@code numberMatched} is out of scope for union responses and returns {@code -1} (omitted).
 */
public class UnionFeatureProducer implements FeatureProducer {

    /** wins on id collision. */
    private final FeatureType branchFt;
    private final FeatureProducer branch;
    /** loses on id collision. */
    private final FeatureType masterFt;
    private final FeatureProducer master;

    public UnionFeatureProducer(FeatureType branchFt, FeatureProducer branch, FeatureType masterFt,
            FeatureProducer master) {
        this.branchFt = branchFt;
        this.branch = branch;
        this.masterFt = masterFt;
        this.master = master;
    }

    @Override
    public FeatureStream getFeatures(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        int idPos = idPosition(col);
        FeatureStream branchStream = branch.getFeatures(request, col.withFt(branchFt));
        FeatureStream masterStream;
        try {
            masterStream = master.getFeatures(request, col.withFt(masterFt));
        } catch (Exception e) {
            branchStream.close();
            throw e;
        }
        return new MergeByIdFeatureStream(branchStream, masterStream, idPos);
    }

    /**
     * {@code numberMatched} is not computed for union responses (a cross-source distinct count is out
     * of scope); returning {@code -1} signals "omit".
     */
    @Override
    public int getNumberMatched(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        return -1;
    }

    private static int idPosition(GetFeatureCollection col) {
        HakunaProperty id = col.getFt().getId();
        List<HakunaProperty> props = col.getProperties();
        for (int i = 0; i < props.size(); i++) {
            if (props.get(i) == id) {
                return i;
            }
        }
        // Fall back to name match (schemas share id name across participants).
        for (int i = 0; i < props.size(); i++) {
            if (props.get(i).getName().equals(id.getName())) {
                return i;
            }
        }
        throw new IllegalStateException("id property not present in collection properties");
    }

}
