package fi.nls.hakunapi.flatgeobuf;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.wololo.flatgeobuf.ColumnMeta;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.QueryContext;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.filter.FilterOp;
import fi.nls.hakunapi.core.filter.LikeFilter;
import fi.nls.hakunapi.core.projection.ProjectionHelper;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.core.util.EmptyFeatureStream;

public class FlatgeobufFeatureProducer implements FeatureProducer {

    @Override
    public FeatureStream getFeatures(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        FlatgeobufFeatureType ft = (FlatgeobufFeatureType) col.getFt();
        List<Filter> filters = col.getFilters();

        if (filters.stream().anyMatch(it -> it == Filter.DENY)) {
            return new EmptyFeatureStream();
        }
        
        QueryContext ctx = new QueryContext();
        ctx.setSRID(request.getSRID());

        int[] indexMap = select(ft, col.getProperties(), ctx);

        Optional<Filter> intersectsFilter = filters.stream().filter(f -> f.getOp() == FilterOp.INTERSECTS || f.getOp() == FilterOp.INTERSECTS_INDEX).findAny();
        if (intersectsFilter.isPresent()) {
            Filter f = intersectsFilter.get();
            HakunaPropertyGeometry prop = (HakunaPropertyGeometry) f.getProp();
            Geometry geom = ProjectionHelper.reprojectToStorageCRS(prop, (Geometry) f.getValue());
            Envelope envelope = geom.getEnvelopeInternal();
            filters.remove(f);
            if (!envelope.contains(ft.fgb.meta.envelope)) {
                Predicate<ValueProvider> filterFn = toPredicate(ft, filters, Predicate::and);
                return new FlatgeobufAllFeaturesStream(ft.fgb, request.getOffset(), filterFn, indexMap);
            }
        }

        Predicate<ValueProvider> filterFn = toPredicate(ft, filters, Predicate::and);
        return new FlatgeobufAllFeaturesStream(ft.fgb, request.getOffset(), filterFn, indexMap);
    }

    @Override
    public int getNumberMatched(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        FlatgeobufFeatureType ft = (FlatgeobufFeatureType) col.getFt();
        List<Filter> filters = col.getFilters();
        if (filters.isEmpty()) {
            return (int) ft.fgb.meta.featuresCount;
        }
        return 0;
    }

    private static int[] select(FlatgeobufFeatureType ft, List<HakunaProperty> properties, QueryContext ctx) {
        List<ColumnMeta> allColumns = ft.fgb.meta.columns;
        int[] indexMap = new int[properties.size()];
        for (int i = 0; i < properties.size(); i++) {
            HakunaProperty property = properties.get(i);
            if (property instanceof HakunaPropertyGeometry) {
                indexMap[i] = 0;
            } else {
                indexMap[i] = 1 + indexOf(allColumns, property.getColumn());
            }
        }
        return indexMap;
    }

    private Predicate<ValueProvider> toPredicate(FlatgeobufFeatureType ft, List<Filter> filters, BinaryOperator<Predicate<ValueProvider>> reduce) {
        return filters.stream()
                .map(f -> toPredicate(ft, f))
                .filter(p -> p != null)
                .reduce(reduce)
                .orElseGet(() -> __ -> true);
    }

    private Predicate<ValueProvider> toPredicate(FlatgeobufFeatureType ft, Filter filter) {
        switch (filter.getOp()) {
        case PASS:
            return null;
        case AND:
            return toPredicate(ft, (List<Filter>) filter.getValue(), Predicate::and);
        case OR:
            return toPredicate(ft, (List<Filter>) filter.getValue(), Predicate::or);
        case NOT:
            return toPredicate(ft, (Filter) filter.getValue()).negate();
        }

        HakunaProperty prop = filter.getProp();
        int i = prop instanceof HakunaPropertyGeometry ? 0 : indexOf(ft.fgb.meta.columns, prop.getColumn()) + 1;

        switch (filter.getOp()) {
        case NULL:
            return vp -> vp.isNull(i);
        case NOT_NULL:
            return vp -> !vp.isNull(i);
        }

        Object value = filter.getValue();
        if (value instanceof String) {
            boolean casei = filter.isCaseInsensitive();
            String s = (String) value;
            String lc = s.toLowerCase();
            switch (filter.getOp()) {
            case EQUAL_TO:
                return casei
                        ? vp -> !vp.isNull(i) && s.equalsIgnoreCase(vp.getString(i))
                        : vp -> !vp.isNull(i) && s.equals(vp.getString(i));
            case NOT_EQUAL_TO:
                return casei 
                        ? vp -> !vp.isNull(i) && !s.equalsIgnoreCase(vp.getString(i))
                        : vp -> !vp.isNull(i) && !s.equals(vp.getString(i));
            case GREATER_THAN:
                return casei
                        ? vp -> !vp.isNull(i) && lc.compareTo(vp.getString(i).toLowerCase()) > 0
                        : vp -> !vp.isNull(i) && s.compareTo(vp.getString(i)) > 0;
            case GREATER_THAN_OR_EQUAL_TO:
                return casei
                        ? vp -> !vp.isNull(i) && lc.compareTo(vp.getString(i).toLowerCase()) >= 0
                        : vp -> !vp.isNull(i) && s.compareTo(vp.getString(i)) >= 0;
            case LESS_THAN:
                return casei
                        ? vp -> !vp.isNull(i) && lc.compareTo(vp.getString(i).toLowerCase()) < 0
                        : vp -> !vp.isNull(i) && s.compareTo(vp.getString(i)) < 0;
            case LESS_THAN_OR_EQUAL_TO:
                return casei
                        ? vp -> !vp.isNull(i) && lc.compareTo(vp.getString(i).toLowerCase()) <= 0
                        : vp -> !vp.isNull(i) && s.compareTo(vp.getString(i)) <= 0;
            case LIKE:
                return toPredicate((LikeFilter) filter, i);
            case NOT_LIKE:
                return toPredicate((LikeFilter) filter, i).negate();
            }
        }
        if (value instanceof Comparable) {
            Comparable c = (Comparable) value;
            switch (filter.getOp()) {
            case EQUAL_TO:
                return vp -> !vp.isNull(i) && c.equals(vp.getObject(i));
            case NOT_EQUAL_TO:
                return vp -> !vp.isNull(i) && !c.equals(vp.getObject(i));
            case GREATER_THAN:
                return vp -> !vp.isNull(i) && c.compareTo(vp.getObject(i)) <= 0;
            case GREATER_THAN_OR_EQUAL_TO:
                return vp -> !vp.isNull(i) && c.compareTo(vp.getObject(i)) > 0;
            case LESS_THAN:
                return vp -> !vp.isNull(i) && c.compareTo(vp.getObject(i)) >= 0;
            case LESS_THAN_OR_EQUAL_TO:
                return vp -> !vp.isNull(i) && c.compareTo(vp.getObject(i)) > 0;
            }
        }
        if (value instanceof Geometry) {
            Geometry g = (Geometry) value;
            switch (filter.getOp()) {
            case INTERSECTS_INDEX:
                return vp -> !vp.isNull(i) && g.getEnvelopeInternal().intersects(vp.getHakunaGeometry(i).toJTSGeometry().getEnvelopeInternal());
            case INTERSECTS:
                return vp -> !vp.isNull(i) && g.intersects(vp.getHakunaGeometry(i).toJTSGeometry());
            case EQUALS:
                return vp -> !vp.isNull(i) && g.equals(vp.getHakunaGeometry(i).toJTSGeometry());
            case DISJOINT:
                return vp -> !vp.isNull(i) && g.disjoint(vp.getHakunaGeometry(i).toJTSGeometry());
            case TOUCHES:
                return vp -> !vp.isNull(i) && g.touches(vp.getHakunaGeometry(i).toJTSGeometry());
            case WITHIN:
                return vp -> !vp.isNull(i) && g.within(vp.getHakunaGeometry(i).toJTSGeometry());
            case OVERLAPS:
                return vp -> !vp.isNull(i) && g.overlaps(vp.getHakunaGeometry(i).toJTSGeometry());
            case CROSSES:
                return vp -> !vp.isNull(i) && g.crosses(vp.getHakunaGeometry(i).toJTSGeometry());
            case CONTAINS:
                return vp -> !vp.isNull(i) && g.contains(vp.getHakunaGeometry(i).toJTSGeometry());                
            }
        }
        if (filter.getOp() == FilterOp.ARRAY_OVERLAPS) {
            final List<Object> v = (List<Object>) value;
            return vp -> !vp.isNull(i) && Arrays.stream(vp.getArray(i)).anyMatch(v::contains);
        }

        throw new IllegalArgumentException("Could not convert to predicate: " + filter.getOp() + " " + prop.getName());
    }

    private static Predicate<ValueProvider> toPredicate(LikeFilter like, int idx) {
        char wild = like.getWildCard();
        char single = like.getSingleChar();
        char escape = like.getEscape();
        String v = (String) like.getValue();

        StringBuilder regexBuilder = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == escape) {
                if (i < v.length() - 1) {
                    regexBuilder.append('\\');
                    regexBuilder.append(v.charAt(++i));
                }
            } else if (c == wild) {
                regexBuilder.append('.');
                regexBuilder.append('*');
            } else if (c == single) {
                regexBuilder.append('.');
            } else {
                if ("\\.[]{}()<>*+-=!?^$|".indexOf(c) >= 0) {
                    regexBuilder.append('\\');
                }
                regexBuilder.append(c);
            }
        }
        String regex = regexBuilder.toString(); 

        int flags = 0;
        if (like.isCaseInsensitive()) {
            flags += Pattern.CASE_INSENSITIVE;
        }

        Pattern pattern = Pattern.compile(regex, flags);
        return vp -> !vp.isNull(idx) && pattern.matcher(vp.getString(idx)).matches();
    }

    private static int indexOf(List<ColumnMeta> column, String name) {
        for (int i = 0; i < column.size(); i++) {
            if (column.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

}
