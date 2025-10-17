package fi.nls.hakunapi.flatgeobuf;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.GeometryType;

import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.SimpleSource;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.property.HakunaPropertyWriter;
import fi.nls.hakunapi.core.property.HakunaPropertyWriters;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyBoolean;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyDouble;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyFloat;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyInt;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyLong;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyString;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyTimestamp;

public class FlatgeobufSource implements SimpleSource {

    private static final Logger LOG = LoggerFactory.getLogger(FlatgeobufSource.class);

    @Override
    public String getType() {
        return "fgb";
    }

    @Override
    public SimpleFeatureType parse(HakunaConfigParser cfg, Path path, String collectionId, int[] srids) throws Exception {
        // Current prefix for properties
        String p = "collections." + collectionId + ".";

        LargeFile largeFile;
        String uri = cfg.get(p + "db");
        if (uri.startsWith("http")) {
            URL url = new URL(uri);
            largeFile = new LargeFileHttp(url); 
        } else {
            File file = getFile(cfg.get(p + "db"), path);
            if (file.length() > Integer.MAX_VALUE) {
                largeFile = new LargeFileMmap(file.toPath());
            } else {
                largeFile = new SmallFileMmap(file.toPath());
            }
            
        }
        Flatgeobuf fgb = new Flatgeobuf(largeFile);

        int cfgSrid = Integer.parseInt(cfg.get(p + "srid.storage", "0"));

        FlatgeobufFeatureType ft = new FlatgeobufFeatureType(fgb);
        ft.setName(collectionId);
        ft.setTitle(fgb.meta.name);

        ft.setId(getIdProperty(ft, cfg.getRequired(p + "id.mapping")));
        ft.setGeom(toHakunaGeometryProperty(collectionId, fgb.meta, srids, cfgSrid));
        ft.setProperties(toProperties(fgb.meta, ft.getId().getColumn()));

        return ft;
    }

    private List<HakunaProperty> toProperties(HeaderMeta meta, String idColumnName) {
        return meta.columns.stream()
                .map(this::toProperty)
                .filter(Objects::nonNull)
                .filter(p -> !p.getColumn().equals(idColumnName))
                .collect(Collectors.toList());
    }
    
    private HakunaProperty toProperty(ColumnMeta column) {
        String name = column.name;
        String col = column.name;
        String[] nonNullableColumns = { "mtk_id", "sijaintitarkkuus", "alkupvm", "aineistolahde", "kohdeluokka", "korkeustarkkuus", "kayttotarkoitus", "kerrosluku", "pohjankorkeus" };
        boolean nullable = Arrays.stream(nonNullableColumns).noneMatch(name::equals) && column.nullable;
        boolean unique = column.unique;
        switch (column.type) {
        case ColumnType.Bool:
            return new HakunaPropertyBoolean(name, col, col, nullable, unique, HakunaPropertyWriters.getSimplePropertyWriter(name, HakunaPropertyType.BOOLEAN));
        case ColumnType.Int:
            return new HakunaPropertyInt(name, col, col, nullable, unique, HakunaPropertyWriters.getPrimitivePropertyWriter(name, HakunaPropertyType.INT, nullable));
        case ColumnType.Long:
            return new HakunaPropertyLong(name, col, col, nullable, unique, HakunaPropertyWriters.getSimplePropertyWriter(name, HakunaPropertyType.LONG));
        case ColumnType.Float:
            return new HakunaPropertyFloat(name, col, col, nullable, unique, HakunaPropertyWriters.getSimplePropertyWriter(name, HakunaPropertyType.FLOAT));
        case ColumnType.Double:
            return new HakunaPropertyDouble(name, col, col, nullable, unique, HakunaPropertyWriters.getSimplePropertyWriter(name, HakunaPropertyType.DOUBLE));
        case ColumnType.DateTime:
            return new HakunaPropertyTimestamp(name, col, col, nullable, unique, HakunaPropertyWriters.getPrimitivePropertyWriter(name, HakunaPropertyType.TIMESTAMP, nullable));
        case ColumnType.String:
            return new HakunaPropertyString(name, col, col, nullable, unique, HakunaPropertyWriters.getSimplePropertyWriter(name, HakunaPropertyType.STRING));
        default:
            return null;
            // throw new IllegalArgumentException("Unsupported column type: " + column.type);
        }
    }

    private File getFile(String name, Path configPath) {
        File file = new File(name);
        return file.isAbsolute() ? file : new File(configPath.getParent().toFile(), name);
    }

    private static HakunaProperty getIdProperty(FlatgeobufFeatureType ft, String idMapping) {
        String name = "id";
        boolean nullable = false;
        boolean unique = true;
        ColumnMeta column = ft.fgb.meta.columns.stream().filter(it -> it.name.equalsIgnoreCase(idMapping)).findAny().get();
        HakunaPropertyType type = 
                column.type == ColumnType.Int ? HakunaPropertyType.INT :
                    column.type == ColumnType.Long ? HakunaPropertyType.LONG :
                        column.type == ColumnType.String ? HakunaPropertyType.STRING : null;
        String col = column.name;
        HakunaPropertyWriter propWriter = HakunaPropertyWriters.getIdPropertyWriter(ft, ft.getName(), name, type);
        switch (type) {
        case INT:
            return new HakunaPropertyInt(name, col, col, nullable, unique, propWriter);
        case LONG:
            return new HakunaPropertyLong(name, col, col, nullable, unique, propWriter);
        case STRING:
            return new HakunaPropertyString(name, col, col, nullable, unique, propWriter);
        default:
            throw new IllegalArgumentException("Unsupported id column type");    
        }
    }

    private static HakunaPropertyGeometry toHakunaGeometryProperty(String collectionId, HeaderMeta meta, int[] srids, int cfgSrid) {
        String name = "geometry";
        boolean nullable = false;
        HakunaGeometryType type = toHakunaGeometryType(meta.geometryType);
        // Ignore other dimensions for now
        int dimension = 2;
        int storageSRID = getSrid(collectionId, cfgSrid, meta.srid); 
        HakunaPropertyWriter propWriter = HakunaPropertyWriters.getGeometryPropertyWriter(name, true);
        return new HakunaPropertyGeometry(name, name, name, nullable, type, srids, storageSRID, dimension, propWriter); 
    }

    private static int getSrid(String collectionId, int cfgSrid, int fileSrid) {
        if (cfgSrid <= 0 && fileSrid <= 0) {
            throw new IllegalArgumentException(String.format("Collection %s srid is unknown!", collectionId));
        } else if (fileSrid <= 0) {
            return cfgSrid;
        } else {
            if (cfgSrid > 0) {
                LOG.warn("For collection {} using srid {} from file instead of configured {}", collectionId, cfgSrid, fileSrid);
            }
            return fileSrid;
        }
    }

    private static HakunaGeometryType toHakunaGeometryType(int geometryType) {
        switch (geometryType) {
        case GeometryType.Point:
            return HakunaGeometryType.POINT;
        case GeometryType.LineString:
            return HakunaGeometryType.LINESTRING;
        case GeometryType.Polygon:
            return HakunaGeometryType.POLYGON;
        case GeometryType.MultiPoint:
            return HakunaGeometryType.MULTIPOINT;
        case GeometryType.MultiLineString:
            return HakunaGeometryType.MULTILINESTRING;
        case GeometryType.MultiPolygon:
            return HakunaGeometryType.MULTIPOLYGON;
        case GeometryType.Unknown:
            return HakunaGeometryType.GEOMETRY;
        default:
            throw new RuntimeException("Unknown geometry type");
        }
    }

}