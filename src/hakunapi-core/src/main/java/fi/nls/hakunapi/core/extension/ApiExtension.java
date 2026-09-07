package fi.nls.hakunapi.core.extension;

import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.ConformanceClass;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.operation.OperationImpl;
import fi.nls.hakunapi.core.schemas.CollectionInfo;
import fi.nls.hakunapi.core.schemas.Link;

/**
 * A module that adds resources of its own to the API - OGC API - Tiles, say -
 * without hakunapi-core or the servlet layer knowing the module.
 *
 * <p>One instance describes everything the servlet layer needs to publish that
 * module, so a deployment declares an extension in exactly one place and nothing
 * else has to be kept in step:
 * <ul>
 *   <li>what it conforms to - {@link #getConformanceClasses()}</li>
 *   <li>which operations and resources serve its paths - {@link #getOperations()}</li>
 *   <li>how it shows up in the landing page and collections documents - the
 *       {@code get*Links} / {@code getCollections} methods</li>
 * </ul>
 *
 * <p>The metadata methods are called once per request on a cold path (metadata,
 * never per feature), and receive the resolved base URL plus the query params to
 * carry over, because building a {@link Link} needs both. An extension returns
 * {@link List#of()} for anything it does not contribute; see {@link #NOP}.
 *
 * <p>An instance is built once at startup, shared across requests, and must be
 * thread-safe.
 */
public interface ApiExtension {

    /** Contributes nothing. */
    public static final ApiExtension NOP = new ApiExtension() {

        @Override
        public String getName() {
            return "nop";
        }

        @Override
        public List<ConformanceClass> getConformanceClasses() {
            return List.of();
        }

        @Override
        public List<OperationImpl> getOperations() {
            return List.of();
        }

        @Override
        public Map<Class<?>, Object> getInjectables() {
            return Map.of();
        }

        @Override
        public List<Link> getLandingPageLinks(String baseUrl, Map<String, String> queryParams) {
            return List.of();
        }

        @Override
        public List<Link> getCollectionLinks(FeatureType ft, String baseUrl, Map<String, String> queryParams) {
            return List.of();
        }

        @Override
        public List<CollectionInfo> getCollections(String baseUrl, Map<String, String> queryParams) {
            return List.of();
        }

        @Override
        public List<String> getCollectionIds(String resource) {
            return List.of();
        }

        @Override
        public Map<String, List<String>> getFormatMediaTypes() {
            return Map.of();
        }

    };

    /** Short name for logging the enabled extensions at startup. */
    public String getName();

    /**
     * Conformance classes this extension adds to {@code /conformance}. Declared
     * once at startup, so an extension that ends up with nothing configured
     * returns {@link List#of()} rather than claiming conformance it cannot meet.
     */
    public List<ConformanceClass> getConformanceClasses();

    /**
     * The operations this extension serves, each pairing its OpenAPI
     * description with the JAX-RS resource implementing it, in the same shape
     * the application already registers its own operations in. An extension
     * with nothing configured returns {@link List#of()}, so no paths appear.
     */
    public List<OperationImpl> getOperations();

    /**
     * The extension's own configuration objects that its resources need
     * injected, keyed by the type to bind them as. The application registers
     * each with its dependency injection; declared here as plain types so
     * hakunapi-core stays free of any DI framework.
     *
     * <p>Each value must be an instance of its key, and is shared across
     * requests like every other injected config.
     */
    public Map<Class<?>, Object> getInjectables();

    /**
     * Links to add to the landing page, typically the extension's own top-level
     * resource.
     *
     * @param baseUrl current server URL, without a trailing slash
     * @param queryParams query params to carry over; may be modified during the
     *        call but must be restored before returning
     */
    public List<Link> getLandingPageLinks(String baseUrl, Map<String, String> queryParams);

    /**
     * Links to add to a feature collection, for an extension offering a further
     * representation of it. Applied both to {@code /collections/{collectionId}}
     * and to that collection's entry in {@code /collections}.
     */
    public List<Link> getCollectionLinks(FeatureType ft, String baseUrl, Map<String, String> queryParams);

    /**
     * Collections this extension publishes that are not feature collections. In
     * OGC API a collection is any geospatial data resource, so these are listed
     * in {@code /collections} and resolvable at
     * {@code /collections/{collectionId}}, but have no {@code items} link.
     *
     * <p>The extension builds the whole {@link CollectionInfo}, because only it
     * knows the resource's links, extent and {@code itemType}.
     */
    public List<CollectionInfo> getCollections(String baseUrl, Map<String, String> queryParams);

    /**
     * Ids of the collections for which this extension serves {@code resource},
     * letting a {@link fi.nls.hakunapi.core.operation.DynamicPathOperation}
     * expand its paths in the OpenAPI document. Resource names are the
     * extension's own; it answers {@link List#of()} for one it does not know.
     *
     * <p>This exists separately from {@link #getOperations()} because the OpenAPI
     * generator asks a freshly constructed operation instance that has no
     * injected state, so the ids have to come from the service config.
     */
    public List<String> getCollectionIds(String resource);

    /**
     * The {@code f} query parameter values this extension's resources answer to,
     * each mapped to the media types {@code f} should ask for, most preferred
     * first. Lets {@code ?f=mvt} work the same way {@code ?f=json} does, without
     * hakunapi-core knowing any of the extension's formats.
     *
     * <p>An id must not collide with a
     * {@link fi.nls.hakunapi.core.MetadataFormat} id or a configured output
     * format; those are resolved first and win.
     */
    public Map<String, List<String>> getFormatMediaTypes();

}
