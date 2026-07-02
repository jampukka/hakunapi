package fi.nls.hakunapi.simple.postgis.branch;

import java.nio.file.Path;

import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.SimpleSource;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.simple.postgis.PostGISSimpleSource;
import fi.nls.hakunapi.simple.postgis.SQLFeatureType;

/**
 * A {@link SimpleSource} (type {@code pg-branch}) that publishes an ordinary PostGIS collection with
 * an added required per-request {@code ?branch=} selector (git-worktree semantics): each branch is
 * read from its own database.
 *
 * <p>It reuses the full base PostGIS parsing by delegating to a {@link PostGISSimpleSource}, then
 * wraps the resulting {@link SQLFeatureType} in a {@link BranchFeatureType} that adds the branch
 * parameter and the branch-reading producer. Enable it by listing this class in {@code db.classes}
 * alongside the base PostGIS source, and set {@code type=pg-branch} on the collection.
 *
 * <pre>
 * db.classes = fi.nls.hakunapi.simple.postgis.PostGISSimpleSource,\
 *              fi.nls.hakunapi.simple.postgis.branch.BranchPostGISSource
 *
 * collections.mycol.type          = pg-branch
 * collections.mycol.branch.pattern = ^[a-z0-9_]+$          # optional regexp guard
 * collections.mycol.branch.db.jdbcUrl  = jdbc:postgresql://host/branch_{branch}
 * collections.mycol.branch.db.username = readonly
 * </pre>
 */
public class BranchPostGISSource implements SimpleSource {

    private final PostGISSimpleSource delegate = new PostGISSimpleSource();

    public BranchPostGISSource() {
        // Public no-arg constructor: instantiated reflectively from the db.classes list.
    }

    @Override
    public String getType() {
        return "pg-branch";
    }

    @Override
    public SimpleFeatureType parse(HakunaConfigParser cfg, Path path, String collectionId, int[] srids)
            throws Exception {
        SimpleFeatureType parsed = delegate.parse(cfg, path, collectionId, srids);
        if (!(parsed instanceof SQLFeatureType)) {
            throw new IllegalStateException("Expected PostGIS SQLFeatureType for branch collection " + collectionId);
        }
        String prefix = "collections." + collectionId + ".";
        BranchConfig branchConfig = BranchConfig.parse(cfg, prefix);
        if (branchConfig == null) {
            throw new IllegalArgumentException("Collection " + collectionId
                    + " uses type=pg-branch but has no branch.db.* configuration");
        }
        return new BranchFeatureType((SQLFeatureType) parsed, branchConfig);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

}
