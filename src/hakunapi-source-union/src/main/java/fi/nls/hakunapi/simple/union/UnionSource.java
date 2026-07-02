package fi.nls.hakunapi.simple.union;

import java.nio.file.Path;
import java.util.Map;

import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.SimpleSource;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.core.config.SourceLoader;

/**
 * A {@link SimpleSource} (type {@code union}) that publishes the UNION-distinct-by-id overlay of two
 * inline child collections — a {@code branch} laid over a {@code master}, branch winning on an id
 * collision.
 *
 * <p>Each child is an ordinary collection config nested under the union collection and parsed by the
 * child's own backend source (mixed backends allowed). The child sources are resolved from
 * {@code db.classes} via {@link SourceLoader} (the same list the servlet bootstrap uses), because a
 * {@code SimpleSource} is not handed the {@code type -> source} map. Children are parsed with a dotted
 * collection id so the base parsers read the nested prefix.
 *
 * <pre>
 * collections.mycol.type = union
 *
 * collections.mycol.union.master.type  = pg          # any registered backend
 * collections.mycol.union.master.table = base_table
 * collections.mycol.union.master. ...              # a full collection config
 *
 * collections.mycol.union.branch.type  = pg
 * collections.mycol.union.branch.table = edits_table
 * collections.mycol.union.branch. ...
 * </pre>
 *
 * <p>The children must share one schema (same id, geometry and properties in the same order); the
 * union publishes the master's schema. Enable by listing this class in {@code db.classes}.
 */
public class UnionSource implements SimpleSource {

    public UnionSource() {
        // Public no-arg constructor: instantiated reflectively from the db.classes list.
    }

    @Override
    public String getType() {
        return "union";
    }

    @Override
    public SimpleFeatureType parse(HakunaConfigParser cfg, Path path, String collectionId, int[] srids)
            throws Exception {
        Map<String, SimpleSource> sourcesByType = SourceLoader.byType(cfg);

        SimpleFeatureType master = parseChild(cfg, path, sourcesByType, collectionId, "master", srids);
        SimpleFeatureType branch = parseChild(cfg, path, sourcesByType, collectionId, "branch", srids);

        UnionFeatureType union = new UnionFeatureType(branch, master);
        union.setName(collectionId);
        // Schema (id/geom/properties) is delegated to master by UnionFeatureType; only carry the
        // non-schema, non-rebinding presentation state.
        union.setQueryableProperties(master.getQueryableProperties());
        union.setDatetimeProperties(master.getDatetimeProperties());
        union.setPaginationStrategy(master.getPaginationStrategy());
        union.setDefaultOrderBy(master.getDefaultOrderBy());
        union.setStaticFilters(master.getStaticFilters());
        union.setProjectionTransformerFactory(master.getProjectionTransformerFactory());
        union.setSpatialExtent(master.getSpatialExtent());
        union.setTemporalExtent(master.getTemporalExtent());
        union.setMetadata(master.getMetadata());
        return union;
    }

    private SimpleFeatureType parseChild(HakunaConfigParser cfg, Path path,
            Map<String, SimpleSource> sourcesByType, String collectionId, String role, int[] srids)
            throws Exception {
        // Dotted child id → the base parser reads "collections.<id>.union.<role>." as its prefix.
        String childId = collectionId + ".union." + role;
        String p = "collections." + childId + ".";

        SimpleSource source;
        if (sourcesByType.size() == 1) {
            source = sourcesByType.values().iterator().next();
        } else {
            String type = cfg.get(p + "type", cfg.get("default.collections.type", "pg"));
            source = sourcesByType.get(type);
            if (source == null) {
                throw new IllegalArgumentException("Unknown type: " + type
                        + " for union " + role + " of collection " + collectionId);
            }
        }
        return source.parse(cfg, path, childId, srids);
    }

}
